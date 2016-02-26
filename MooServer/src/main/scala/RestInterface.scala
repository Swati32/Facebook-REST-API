package main.scala

import akka.actor._
import akka.util.Timeout
import spray.http.StatusCodes
import spray.httpx.SprayJsonSupport._
import spray.routing._
import MooProtocol._
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.concurrent.ExecutionContext
import ExecutionContext.Implicits.global
import scala.concurrent.{ Await, ExecutionContext, Future }
import scala.concurrent.duration._
import spray.http.{ BasicHttpCredentials, HttpChallenge, StatusCodes, HttpHeaders }
import spray.routing.authentication.{ BasicAuth, UserPass }
import scala.collection.mutable.ListBuffer
import org.apache.commons.codec.binary.Base64
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import spray.http._
import HttpMethods._
import spray.json._
import DefaultJsonProtocol._
import spray.httpx.SprayJsonSupport._
import org.apache.commons.codec.binary.Base64
import java.util.Date
import scala.concurrent.duration._
import akka.actor.ActorSystem
import akka.pattern.ask
import akka.event.Logging
import akka.io.IO
import spray.json.{ JsonFormat, DefaultJsonProtocol }
import spray.can.Http
import spray.httpx.SprayJsonSupport
import spray.util._
import spray.http._
import spray.json.DefaultJsonProtocol
import spray.httpx.encoding.{ Gzip, Deflate }
import spray.httpx.SprayJsonSupport._
import akka.util.Timeout
import java.util.concurrent.TimeUnit
import java.security.Signature
import javax.crypto.KeyGenerator;
import java.security.spec.X509EncodedKeySpec
import java.security.KeyFactory
import java.security.SecureRandom
import authentikat.jwt._
import spray.routing.authentication.{ BasicAuth, UserPass }
import spray.routing.Directive1
import spray.routing.directives.AuthMagnet
import spray.routing.directives.BasicDirectives.{ extract, provide }
import spray.routing.directives.RouteDirectives.reject
import spray.routing.directives.FutureDirectives.onSuccess
import javax.crypto.Cipher;
import java.security.PrivateKey;
import java.security.PublicKey;

case class UserCreated(usrId: String)
case class UserNotFound()
case class PageCreated(pgid: String)
case class PageNotFound()
case class CommentsCreated(commentID: String)
case class PostCreated(postID: String)
case class AcceptFriendRequest()
case class FriendRequestCreated()
case class LikeIncluded()
case class AlbumCreated(albId: String)
case class PhotoUploaded(phtId: String)
case class Deleted()
case class NotFound()
case class updatedPosts()
case class UserRegisterd()
case class Keys(key: String, IV: String, postID: String)
case class passRandonNumber(randomNumber: String)
case class SignatureVerified(token: String)
case class SignatureDenied()
case class sendPublicKey(pkKey: String)
case class logoffSuccess()

class RestInterface extends HttpServiceActor with RestApi {
  def receive = handleTimeouts orElse runRoute(routes)
}

trait RestApi extends HttpService with ActorLogging { actor: Actor =>
  implicit val timeout = Timeout(Duration(1000, TimeUnit.SECONDS))

  var userInfo = scala.collection.mutable.Map[String, String]()
  var users = Vector[User]()
  var pages = Vector[Page]()
  var posts = Vector[Posts]()
  var comments = Vector[Comment]()
  var photos = Vector[Photo]()
  var albums = Vector[Album]()
  var keys = Vector[Keys]()
  var userIdList = new ListBuffer[String]()
  var userNonceMap = scala.collection.mutable.Map[String, String]()
  var tokenMap = scala.collection.mutable.Map[String, String]()

  var userId: Int = 0;
  var pageId: Int = 0;
  var postId: Int = 0;
  var commentId: Int = 0;
  var photoId: Int = 0;
  var albumId: Int = 0;
  val ONLY_ME: Int = 1; val FRIENDS_ONLY: Int = 2; val PUBLIC: Int = 3

  /*****************************FINAL STATISTICS ON SHUTDOWN******************************************************/
  var totalNumberOfRequestsMade: Int = 0
  sys addShutdownHook {
    println("Total number of requests made:" + totalNumberOfRequestsMade)
    println("Total number of comments made:" + comments.size)
    println("Total number of albums created:" + albums.size)
    println("Total number of posts made:" + posts.size)
    println("Total number of photos uploaded:" + photos.size)
    Thread.sleep(1000)
  }

  /******************************HANDLING TIMEOUTS****************************************************************************/
  def handleTimeouts: Receive = {
    case Timedout(x: HttpRequest) =>
      sender() ! HttpResponse(StatusCodes.InternalServerError, "Too late")
  }

  /*******************************ROUTING THE REQUESTS*******************************************************************/
  def routes: Route = {
    //USER PUBLIC KEY REG
    pathPrefix("registerPublicKey") {
      pathEnd {
        post {
          entity(as[Mapping]) { usr =>
            requestContext =>
              val responder = createResponder(requestContext)
              totalNumberOfRequestsMade = totalNumberOfRequestsMade + 1
              registerUserPublicKey(usr)
              responder ! UserRegisterd()
          }
        }
      }
    } ~ pathPrefix("login" / Segment) { uid =>
      pathEnd {
        post {
          requestContext =>
            var nonceString = login(uid)
            val responder = createResponder(requestContext)
            responder ! passRandonNumber(nonceString)

        }
      }

    } ~ pathPrefix("logoff" / Segment) { uid =>
      pathEnd {
        post {
          requestContext =>
            tokenMap -= uid
            val responder = createResponder(requestContext)
            responder ! logoffSuccess()
        }
      }

    } ~ pathPrefix("getPublicKey" / Segment) { uid =>
      get { requestContext =>
        val responder = createResponder(requestContext)
        totalNumberOfRequestsMade = totalNumberOfRequestsMade + 1
        var publickey = getUsersPublicKey(uid)
        responder ! sendPublicKey(publickey)

      }
    } ~
      pathPrefix("verify" / Segment) { uid =>
        pathEnd {
          post {
            entity(as[Mapping]) { msgSign =>
              requestContext =>
                val responder = createResponder(requestContext)
                verifyTheData(uid, msgSign) match {
                  case true =>
                    var token = issuetoken(uid)
                    //encryptToken(uid, token)
                    tokenMap += (token -> uid)
                    responder ! SignatureVerified(token)
                  case _ => responder ! SignatureDenied()

                }
            }
          }
        }
        //USERS (POST/GET/PUT/DELETE)  
      } ~ pathPrefix("Users") {
        pathEnd {
          post {
            entity(as[User]) { user =>
              requestContext =>
                val responder = createResponder(requestContext)
                totalNumberOfRequestsMade = totalNumberOfRequestsMade + 1
                var UsrId = createUser(user)
                responder ! UserCreated(UsrId)
            }
          }
        } ~
          path(Segment) { id =>
            get { requestContext =>
              val responder = createResponder(requestContext)
              totalNumberOfRequestsMade = totalNumberOfRequestsMade + 1
              getUserData(id).map(responder ! _)
                .getOrElse(responder ! UserNotFound())
            } ~
              path(Segment) { id =>
                delete { requestContext =>
                  val responder = createResponder(requestContext)
                  totalNumberOfRequestsMade = totalNumberOfRequestsMade + 1
                  deleteValue(id, "Users", "", "") match {
                    case true => responder ! Deleted()
                    case _    => responder ! NotFound()
                  }
                }
              }
            //GET PAGES OF THE USER

          } ~ pathPrefix(Segment / "allPages") { usrid =>
            get { requestContext =>
              val responder = createResponder(requestContext)
              totalNumberOfRequestsMade = totalNumberOfRequestsMade + 1
              getAllPagesofTheUser(usrid).map(responder ! _)
                .getOrElse(responder ! PageNotFound())
            }
          } ~
          //SEND FRIEND SUGGESTION
          pathPrefix(Segment / "friendSuggestion") { id =>
            get { requestContext =>
              val responder = createResponder(requestContext)
              totalNumberOfRequestsMade = totalNumberOfRequestsMade + 1
              getFriendSuggestion(id).map(responder ! _)
                .getOrElse(responder ! UserNotFound())

            }
          } ~
          //POSTS FOR THE USER (POST,GET UPDATE,DELETE)
          pathPrefix(Segment / "posts") { uid =>
            headerValueByName("SessionToken") { token =>
              pathEnd {
                post {
                  authorize(checkPermission(uid, token, "POST", "Posts", "")) {
                    entity(as[Posts]) { post =>
                      requestContext =>
                        val responder = createResponder(requestContext)
                        totalNumberOfRequestsMade = totalNumberOfRequestsMade + 1
                        var pstId = createPostForUser(post, uid)
                        responder ! PostCreated(pstId)
                    }
                  }
                } ~ get {
                  authorize(JsonWebToken.validate(token, "PrivateServerKey")) {
                    requestContext =>
                      val responder = createResponder(requestContext)
                      totalNumberOfRequestsMade = totalNumberOfRequestsMade + 1
                      getAllPostsForUsers(uid, token).map(responder ! _)
                        .getOrElse(responder ! NotFound())

                  }
                }
              } ~
                path(Segment) { (postid) =>
                  get {
                    authorize(checkPermission(uid, token, "GET", "Posts", postid)) {
                      requestContext =>
                        val responder = createResponder(requestContext)
                        totalNumberOfRequestsMade = totalNumberOfRequestsMade + 1
                        getPosts(uid, postid).map(responder ! _)
                          .getOrElse(responder ! NotFound())
                    }
                  } ~
                    delete {
                      authorize(checkPermission(uid, token, "DELETE", "", "")) {
                        requestContext =>
                          val responder = createResponder(requestContext)
                          totalNumberOfRequestsMade = totalNumberOfRequestsMade + 1
                          deleteValue(postid, "Posts", uid, "Users") match {
                            case true => responder ! Deleted()
                            case _    => responder ! NotFound()
                          }
                      }
                    } ~ put {
                      entity(as[MessageFormat]) { message =>
                        authorize(checkPermission(uid, token, "PUT", "", "")) {
                          requestContext =>
                            val responder = createResponder(requestContext)
                            updatePost(postid, message)
                            responder ! updatedPosts()
                        }
                      }
                    }
                } ~
                //LIKES FOR THE POST
                pathPrefix(Segment / "likes") { postid =>
                  post {
                    entity(as[Like]) { like =>
                      requestContext =>
                        val responder = createResponder(requestContext)
                        totalNumberOfRequestsMade = totalNumberOfRequestsMade + 1
                        includeLikesForPost(like, postid)
                        responder ! LikeIncluded()
                    }
                  }
                } ~
                //COMMENTS FOR THE POST 
                pathPrefix(Segment / "comments") { objectId =>
                  pathEnd {
                    post {
                      entity(as[Comment]) { comment =>
                        requestContext =>
                          val responder = createResponder(requestContext)
                          totalNumberOfRequestsMade = totalNumberOfRequestsMade + 1
                          var cmtId = createComments(comment, objectId)
                          responder ! CommentsCreated(cmtId)
                      }
                    } ~
                      get { requestContext =>
                        val responder = createResponder(requestContext)
                        totalNumberOfRequestsMade = totalNumberOfRequestsMade + 1
                        getAllCommentsForPost(objectId).map(responder ! _)
                          .getOrElse(responder ! NotFound())
                      }
                  } ~
                    path(Segment) { commentsId =>
                      get { requestContext =>
                        val responder = createResponder(requestContext)
                        totalNumberOfRequestsMade = totalNumberOfRequestsMade + 1
                        getComments(commentsId).map(responder ! _)
                          .getOrElse(responder ! NotFound())
                      }
                    }
                }
            }
          } ~
          //MAKING AND ACCEPTING FRIEND REQUESTS
          pathPrefix(Segment / "friendRequest" / Segment) { (userId, requestToUser) =>
            post { requestContext =>
              totalNumberOfRequestsMade = totalNumberOfRequestsMade + 1
              val responder = createResponder(requestContext)
              createFriendRequest(userId, requestToUser) match {
                case true =>
                  responder ! FriendRequestCreated()
                case _ =>
                  responder ! NotFound()
              }
            }
          } ~
          pathPrefix(Segment / "acceptRequest" / Segment / Segment) { (fromId, toId, acceptStatus) =>
            post { requestContext =>
              totalNumberOfRequestsMade = totalNumberOfRequestsMade + 1
              val responder = createResponder(requestContext)
              acceptFriendRequest(fromId, toId, acceptStatus) match {

                case true =>
                  responder ! AcceptFriendRequest()
                case _ =>
                  responder ! NotFound()
              }
            }
            //USER ALBUMS
          } ~ pathPrefix(Segment / "album") { uid =>
            pathEnd {
              post {
                entity(as[Album]) { album =>
                  requestContext =>
                    totalNumberOfRequestsMade = totalNumberOfRequestsMade + 1
                    val responder = createResponder(requestContext)
                    var albId = createAlbumForUser(album, uid)
                    responder ! AlbumCreated(albId)
                }
              } ~ get { requestContext =>
                totalNumberOfRequestsMade = totalNumberOfRequestsMade + 1
                val responder = createResponder(requestContext)
                getAllAlbumsForUsers(uid).map(responder ! _)
                  .getOrElse(responder ! NotFound())
              }
            } ~
              path(Segment) { (albumid) =>
                get { requestContext =>
                  totalNumberOfRequestsMade = totalNumberOfRequestsMade + 1
                  val responder = createResponder(requestContext)
                  getAlbumForUser(albumid).map(responder ! _)
                    .getOrElse(responder ! NotFound())
                }
                // LIKES FOR ALBUM
              } ~ pathPrefix(Segment / "likes") { albumId =>
                post {
                  entity(as[Like]) { like =>
                    requestContext =>
                      totalNumberOfRequestsMade = totalNumberOfRequestsMade + 1
                      val responder = createResponder(requestContext)
                      includeLikesForAlbums(like, albumId)
                      responder ! LikeIncluded()
                  }
                }
                //UPLOADING PHOTOS 
              } ~ pathPrefix(Segment / "photos") { objectId =>
                headerValueByName("SessionToken") { token =>
                  pathEnd {
                    post {
                      authorize(checkPermission(uid, token, "POST", "Photos", "")) {
                        entity(as[Photo]) { photo =>
                          requestContext =>
                            totalNumberOfRequestsMade = totalNumberOfRequestsMade + 1
                            val responder = createResponder(requestContext)
                            var photoId = uploadPhoto(photo, objectId)
                            responder ! PhotoUploaded(photoId)
                        }
                      }
                    } ~ get {
                      authorize(JsonWebToken.validate(token, "PrivateServerKey")) {
                        requestContext =>
                          totalNumberOfRequestsMade = totalNumberOfRequestsMade + 1
                          val responder = createResponder(requestContext)
                          getAllPhotosforAlbum(uid, objectId, token).map(responder ! _)
                            .getOrElse(responder ! NotFound())
                      }
                    }
                  } ~
                    path(Segment) { (photoId) =>
                      get {
                        authorize(checkPermission(uid, token, "GET", "Photos", photoId)) {
                          requestContext =>
                            totalNumberOfRequestsMade = totalNumberOfRequestsMade + 1
                            val responder = createResponder(requestContext)
                            getPhotoById(uid, photoId, token).map(responder ! _)
                              .getOrElse(responder ! NotFound())
                        }
                      }
                      //COMMENTS FOR PHOTO  
                    } ~ pathPrefix(Segment / "comments") { objectId =>
                      pathEnd {
                        post {
                          entity(as[Comment]) { comment =>
                            requestContext =>
                              totalNumberOfRequestsMade = totalNumberOfRequestsMade + 1
                              val responder = createResponder(requestContext)
                              var cmtId = createCommentsForPhoto(comment, objectId)
                              responder ! CommentsCreated(cmtId)
                          }
                        } ~
                          get { requestContext =>
                            val responder = createResponder(requestContext)
                            totalNumberOfRequestsMade = totalNumberOfRequestsMade + 1
                            getAllCommentsForPhoto(objectId).map(responder ! _)
                              .getOrElse(responder ! NotFound())
                          }
                      } ~
                        path(Segment) { commentsId =>
                          get { requestContext =>
                            totalNumberOfRequestsMade = totalNumberOfRequestsMade + 1
                            val responder = createResponder(requestContext)
                            getComments(commentsId).map(responder ! _)
                              .getOrElse(responder ! NotFound())
                          }
                        }
                    }
                }
              } ~
              //COMMENTS FOR ALBUMS
              pathPrefix(Segment / "comments") { objectId =>
                pathEnd {
                  post {
                    entity(as[Comment]) { comment =>
                      requestContext =>
                        totalNumberOfRequestsMade = totalNumberOfRequestsMade + 1
                        val responder = createResponder(requestContext)
                        var cmtId = createCommentsForAlbum(comment, objectId)
                        responder ! CommentsCreated(cmtId)
                    }
                  } ~
                    get { requestContext =>
                      totalNumberOfRequestsMade = totalNumberOfRequestsMade + 1
                      val responder = createResponder(requestContext)
                      getAllCommentsForAlbum(objectId).map(responder ! _)
                        .getOrElse(responder ! NotFound())
                    }
                } ~
                  path(Segment) { commentsId =>
                    get { requestContext =>
                      totalNumberOfRequestsMade = totalNumberOfRequestsMade + 1
                      val responder = createResponder(requestContext)
                      getComments(commentsId).map(responder ! _)
                        .getOrElse(responder ! NotFound())
                    }
                  }
              }
          }
      } ~ pathPrefix("Pages") {
        pathEnd {
          post {
            entity(as[Page]) { page =>
              requestContext =>
                totalNumberOfRequestsMade = totalNumberOfRequestsMade + 1
                val responder = createResponder(requestContext)
                var pgid = createPage(page)
                responder ! PageCreated(pgid)
            }
          }
        } ~
          path(Segment) { pid =>
            get { requestContext =>
              totalNumberOfRequestsMade = totalNumberOfRequestsMade + 1
              val responder = createResponder(requestContext)
              getPageData(pid).map(responder ! _)
                .getOrElse(responder ! NotFound())
            }
            //LIKES FOR PAGES  
          } ~ pathPrefix(Segment / "likes") { pgId =>
            post {
              entity(as[Like]) { like =>
                requestContext =>
                  val responder = createResponder(requestContext)
                  totalNumberOfRequestsMade = totalNumberOfRequestsMade + 1
                  includeLikesForPages(like, pgId)
                  responder ! LikeIncluded()
              }
            }
          } ~
          //POSTS AND COMMENTS FOR PAGES
          pathPrefix(Segment / "posts") { pid =>
            headerValueByName("SessionToken") { token =>
              pathEnd {
                post {
                  authorize(checkPermissionForPages(pid, token, "POST", "", "")) {
                    entity(as[Posts]) { post =>
                      requestContext =>
                        totalNumberOfRequestsMade = totalNumberOfRequestsMade + 1
                        val responder = createResponder(requestContext)
                        var pstId = createPostForPage(post, pid)
                        responder ! PostCreated(pstId)
                    }
                  }
                } ~
                  get {
                    authorize(JsonWebToken.validate(token, "PrivateServerKey")) {
                      requestContext =>
                        totalNumberOfRequestsMade = totalNumberOfRequestsMade + 1
                        val responder = createResponder(requestContext)
                        getAllPostsForPages(pid, token).map(responder ! _)
                          .getOrElse(responder ! NotFound())
                    }
                  }
              } ~
                path(Segment) { (postid) =>
                  get {
                    authorize(checkPermissionForPages(pid, token, "GET", "Posts", postid)) {
                      requestContext =>
                        totalNumberOfRequestsMade = totalNumberOfRequestsMade + 1
                        val responder = createResponder(requestContext)
                        getPosts(pid, postid).map(responder ! _)
                          .getOrElse(responder ! NotFound())
                    }
                  }
                } ~
                pathPrefix(Segment / "comments") { objectId =>
                  pathEnd {
                    post {
                      entity(as[Comment]) { comment =>
                        requestContext =>
                          totalNumberOfRequestsMade = totalNumberOfRequestsMade + 1
                          val responder = createResponder(requestContext)
                          var cmtId = createComments(comment, objectId)
                          responder ! CommentsCreated(cmtId)
                      }
                    }
                  } ~
                    path(Segment) { commentsId =>
                      get { requestContext =>
                        totalNumberOfRequestsMade = totalNumberOfRequestsMade + 1
                        val responder = createResponder(requestContext)
                        getComments(commentsId).map(responder ! _)
                          .getOrElse(responder ! NotFound())
                      }
                    }
                }
            }
          }
      }
  }

  /*********************************************REGISTERATION**********************************************************/
  def registerUserPublicKey(usr: Mapping) = {
    userInfo += (usr.key -> usr.value)
  }

  def login(uid: String): String = {
    var randomSecureRandom = SecureRandom.getInstance("SHA1PRNG");
    var nonce = new Array[Byte](16);
    randomSecureRandom.nextBytes(nonce);
    var nonceString = Base64.encodeBase64String(nonce);
    userNonceMap += (uid -> nonceString)
    nonceString
  }

  def verifyTheData(userID: String, msgSign: Mapping): Boolean = {
    var msgString = msgSign.key
    var signatureString = msgSign.value
    var signatureBytes = Base64.decodeBase64(signatureString)
    var messageString = userNonceMap(userID)
    if (msgString == messageString) {
      var message = Base64.decodeBase64(messageString)
      var publicKeyString = userInfo(userID)
      var instance = Signature.getInstance("SHA1withRSA");
      var pubKey = convertToPublicKey(publicKeyString)
      instance.initVerify(pubKey);
      instance.update(message);
      userNonceMap.remove(userID)
      return instance.verify(signatureBytes)

    } else {
      return false
    }
  }
  
  def convertToPublicKey(publicKeyString: String): PublicKey = {
    var publicBytes = Base64.decodeBase64(publicKeyString);
    var keySpec = new X509EncodedKeySpec(publicBytes);
    var keyFactory = KeyFactory.getInstance("RSA");
    var pubKey = keyFactory.generatePublic(keySpec);
    pubKey
  }

  def issuetoken(userid: String): String = {
    val header = JwtHeader("HS256")
    val claimsSet = JwtClaimsSet(Map("exp" -> "3600", "userid" -> userid))
    val jwt: String = JsonWebToken(header, claimsSet, "PrivateServerKey")
    return jwt
  }

  def getUsersPublicKey(userID: String): String = {
    userInfo(userID)
  }

  def checkPermission(userID: String, token: String, requestType: String, objectType: String, objectID: String): Boolean = {
    if (JsonWebToken.validate(token, "PrivateServerKey")) {
      var userRequesting = tokenMap(token)
      if (tokenMap(token) == userID) {
        return true
      } else {
        users.find(_.id == Some(userID)) match {
          case Some(usr) =>
            var permLevel = 0
            if (requestType == "POST" && objectType == "Posts") {
              permLevel = usr.postOnMyWallPermission
            }
            return checkPermLevel(permLevel, usr.friendlist.contains(userRequesting))
          case None =>
            return false
        }
      }
    } else {
      return false
    }
  }

  def checkPermLevel(permLevel: Int, isFriend: Boolean): Boolean = {
    permLevel match {
      case ONLY_ME =>
        return false
      case FRIENDS_ONLY =>
        if (isFriend) {
          return true
        } else
          return false
      case PUBLIC =>
        return true
      case _ =>
        return false
    }
  }

  /**********************************************CREATION**************************************************************/

  /***********************************************USERS***********************************************************/
  private def createUser(user: User): String = {
    userId = userId + 1
    var Usr = "User_" + userId.toString()
    userIdList += Usr
    user.id = Some(Usr)
    users = users :+ user
    Usr
  }
  /****************************************************PAGES **********************************************************/
  private def createPage(page: Page): String = {
    pageId = pageId + 1
    var pg = "Page_" + pageId.toString()
    page.id = Some(pg)
    pages = pages :+ page
    users.find(_.id == Some(page.owner)) match {
      case Some(i) =>
        var index = users.indexOf(i)
        var pglist = i.userPages :+ pg
        var newPage = i.copy(userPages = pglist)
        users = users.updated(index, newPage)
      case None =>
        println("Page not found")
    }
    pg
  }
  /****************************************************FRIEND REQUESTS********************************************************************/

  private def getFriendSuggestion(id: String): Option[Ids] = {
    var friendList = Seq.empty[String]
    for (i <- 0 until 5) {
      var friendId = userIdList(util.Random.nextInt(userIdList.size))
      users.find(_.id == Some(id)) match {
        case Some(usr) =>
          if (!(usr.friendlist.contains(Some(friendId))) && id != friendId && !(friendList.contains(friendId)))
            friendList = friendList :+ friendId
        case None =>
          println("No friend suggestions for now")
      }
    }
    var frndIds = new Ids(friendList)
    Some(frndIds)
  }
  private def createFriendRequest(fromId: String, toId: String): Boolean = {
    users.find(_.id == Some(fromId)) match {
      case Some(usrFrom) =>
        users.find(_.id == Some(toId)) match {
          case Some(usrTo) =>
            var index = users.indexOf(usrTo)
            var oldlist = usrTo.friendrequest
            var oldListUserFrom = usrFrom.friendrequest
            if (!(oldListUserFrom.contains(usrTo))) {
              if (!(oldlist.contains(fromId))) {
                oldlist = oldlist :+ fromId
                var newUser = usrTo.copy(friendrequest = oldlist)
                users = users.updated(index, newUser)
              }
            }
          case None =>
            println("User not found")
        }
      case None =>
        println("User not found")
    }
    return true
  }

  private def acceptFriendRequest(toId: String, fromId: String, acceptStatus: String): Boolean = {
    users.find(_.id == Some(fromId)) match {
      case Some(usrFrom) =>
        users.find(_.id == Some(toId)) match {
          case Some(usrTo) =>
            var indexTo = users.indexOf(usrTo)
            var oldfriendRequestlist = usrTo.friendrequest
            oldfriendRequestlist = oldfriendRequestlist.filterNot { _ == fromId }
            var newUserTo = usrTo.copy(friendrequest = oldfriendRequestlist)
            users = users.updated(indexTo, newUserTo)
            if (acceptStatus == "Accept") {
              var indexFrom = users.indexOf(usrFrom)
              var oldFriendListTo = usrTo.friendlist
              if (!(oldFriendListTo.contains(fromId))) {
                var newfriendList = oldFriendListTo :+ fromId
                var newUserTo = usrTo.copy(friendlist = newfriendList)
                var newListFriends = usrFrom.friendlist :+ toId
                var newUserFrom = usrFrom.copy(friendlist = newListFriends)
                users = users.updated(indexTo, newUserTo)
                users = users.updated(indexFrom, newUserFrom)
              }
            }
          case None =>
            println("User not found")
        }
      case None =>
        println("User not found")
    }
    return true
  }

  /******************************************ALBUMS AND PHOTOS****************************************************/

  private def createAlbumForUser(album: Album, userId: String): String = {
    albumId = albumId + 1
    var albID = "Album_" + albumId.toString()
    album.id = Some(albID)
    var d = new File(albID)
    d.mkdir()
    albums = albums :+ album
    users.find(_.id == Some(userId)) match {
      case Some(i) =>
        var index = users.indexOf(i)
        var albumList = i.albumlist :+ albID
        var newAlbum = i.copy(albumlist = albumList)
        users = users.updated(index, newAlbum)
      case None =>
        println("Didnt find the user" + userId)
    }
    return albID
  }

  private def uploadPhoto(photo: Photo, albumId: String): String = {
    photoId = photoId + 1
    var phtID = "Photo_" + photoId.toString()
    photo.id = Some(phtID)
    var inputStream = photo.photo
    var imageDataBytes = inputStream.substring(inputStream.indexOf(",") + 1);
    var stream = Base64.decodeBase64(imageDataBytes.getBytes());
    var path = albumId + "/" + phtID + ".png"
    var out = new BufferedOutputStream(new FileOutputStream(path));
    out.write(stream);
    out.close()
    photo.link = path
    photos = photos :+ photo
    albums.find(_.id == Some(albumId)) match {
      case Some(i) =>
        var index = albums.indexOf(i)
        var Photolist = i.photolist :+ phtID
        var newPhoto = i.copy(photolist = Photolist)
        albums = albums.updated(index, newPhoto)
      case None =>
        println("Album not Found !!!")
    }
    return phtID
  }
  /**************************************************POST***********************************************************/

  private def createPostForUser(post: Posts, objectId: String): String = {
    postId = postId + 1
    var pst = "Post_" + postId.toString()
    post.id = Some(pst)
    posts = posts :+ post
    users.find(_.id == Some(objectId)) match {
      case Some(i) =>
        var index = users.indexOf(i)
        var postList = i.postlist :+ pst
        var newPost = i.copy(postlist = postList)
        users = users.updated(index, newPost)
      case None =>
        println("User not Found !!!")
    }
    return pst
  }

  private def createPostForPage(post: Posts, objectId: String): String = {
    postId = postId + 1
    var pst = "Post_" + postId.toString()
    post.id = Some(pst)
    posts = posts :+ post
    pages.find(_.id == Some(objectId)) match {
      case Some(i) =>
        var index = pages.indexOf(i)
        var postList = i.postlist :+ pst
        var newPage = i.copy(postlist = postList)
        pages = pages.updated(index, newPage)
      case None =>
        println("Page not Found !!!")
    }
    return pst
  }
  /*************************************************COMMENTS********************************************************/

  private def createComments(cmnt: Comment, objectId: String): String = {
    commentId = commentId + 1
    var cmntId = "Comment_" + commentId.toString()
    cmnt.id = Some(cmntId)
    comments = comments :+ cmnt
    posts.find(_.id == Some(objectId)) match {
      case Some(i) =>
        var index = posts.indexOf(i)
        var cmntList = i.commentList :+ cmntId
        var newPost = i.copy(commentList = cmntList)
        posts = posts.updated(index, newPost)
      case None =>
        println("Posts not Found !!!")
    }
    return cmntId
  }

  private def createCommentsForAlbum(cmnt: Comment, objectId: String): String = {
    commentId = commentId + 1
    var cmntId = "Comment_" + commentId.toString()
    cmnt.id = Some(cmntId)
    comments = comments :+ cmnt
    albums.find(_.id == Some(objectId)) match {
      case Some(i) =>
        var index = albums.indexOf(i)
        var cmntList = i.commentlist :+ cmntId
        var newPost = i.copy(commentlist = cmntList)
        albums = albums.updated(index, newPost)
      case None =>
        println("Album not Found !!!")
    }
    return cmntId
  }

  private def createCommentsForPhoto(cmnt: Comment, objectId: String): String = {
    commentId = commentId + 1
    var cmntId = "Comment_" + commentId.toString()
    cmnt.id = Some(cmntId)
    comments = comments :+ cmnt
    photos.find(_.id == Some(objectId)) match {
      case Some(i) =>
        var index = photos.indexOf(i)
        var cmntList = i.commentlist :+ cmntId
        var newPost = i.copy(commentlist = cmntList)
        photos = photos.updated(index, newPost)
      case None =>
        println("Photo not Found !!!")
    }
    return cmntId
  }

  /*********************************************LIKES**************************************************************/

  def includeLikesForPost(like: Like, postId: String) = {
    posts.find(_.id == Some(postId)) match {
      case Some(pst) =>
        var oldLikeList = pst.likelist
        if (!(oldLikeList.contains(Some(like.from)))) {
          var likeCount = pst.likes + 1
          var newLikeList = oldLikeList :+ (like.from).toString()
          var index = posts.indexOf(pst)
          var newlike = pst.copy(likelist = newLikeList, likes = likeCount)
          posts = posts.updated(index, newlike)
        }
      case None =>
        println("Post not found")
    }
  }
  def includeLikesForPages(like: Like, pgID: String) = {
    pages.find(_.id == Some(pgID)) match {
      case Some(pst) =>
        var oldLikeList = pst.likelist
        if (!(oldLikeList.contains(Some(like.from)))) {
          var likeCount = pst.likes + 1
          var newLikeList = oldLikeList :+ (like.from).toString()
          var index = pages.indexOf(pst)
          var newlike = pst.copy(likelist = newLikeList, likes = likeCount)
          pages = pages.updated(index, newlike)
        }
      case None =>
        println("Page not found")
    }
  }
  def includeLikesForAlbums(like: Like, alID: String) = {
    albums.find(_.id == Some(alID)) match {
      case Some(pst) =>
        var oldLikeList = pst.likelist
        if (!(oldLikeList.contains(Some(like.from)))) {
          var likeCount = pst.likes + 1
          var newLikeList = oldLikeList :+ (like.from).toString()
          var index = albums.indexOf(pst)
          var newlike = pst.copy(likelist = newLikeList, likes = likeCount)
          albums = albums.updated(index, newlike)
        }
      case None =>
        println("Album not found")
    }
  }

  /***************************************************GET ALL ***************************************************************/

  /***************************************************GET ALL PAGES*********************************************************/
  def getAllPagesofTheUser(userId: String): Option[AllPages] = {
    var allPages = Seq.empty[Page]
    users.find(_.id == Some(userId)) match {
      case Some(usr) =>
        usr.userPages.foreach((i: String) =>
          pages.find(_.id == Some(i)) match {
            case Some(pst) =>
              allPages = allPages :+ pst
            case None =>
              println("Not found the page" + i.toString())
          })
      case None =>
    }
    var allThePages = new AllPages(allPages)
    Some(allThePages)
  }
  /****************************GET ALBUMS AND PHOTOS***********************************************************/

  def getAllAlbumsForUsers(userId: String): Option[AllAlbums] = {
    var allAlbums = Seq.empty[Album]
    users.find(_.id == Some(userId)) match {
      case Some(usr) =>
        usr.albumlist.foreach((i: String) =>
          albums.find(_.id == Some(i)) match {
            case Some(albumDetail) =>
              allAlbums = allAlbums :+ albumDetail
            case None =>
              println("Album not found" + i.toString())
          })
      case None =>
    }
    var allTheAlbums = new AllAlbums(allAlbums)
    Some(allTheAlbums)
  }

  def getAllPhotosforAlbum(userId: String, albumId: String, token: String): Option[AllPhotos] = {
    var userRequesting = tokenMap(token)

    var allPhotos = Seq.empty[Photo]
    albums.find(_.id == Some(albumId)) match {
      case Some(alb) =>
        alb.photolist.foreach((i: String) =>
          photos.find(_.id == Some(i)) match {
            case Some(photoDetail) =>
              var modifiedphotoDetail = photoDetail
              if (modifiedphotoDetail.key.contains(userRequesting)) {
                var pk = modifiedphotoDetail.key(userRequesting)
                var modifiedKeyValuePair = Map[String, String]()
                modifiedKeyValuePair += (userRequesting -> pk)
                modifiedphotoDetail = modifiedphotoDetail.copy(key = modifiedKeyValuePair)
                allPhotos = allPhotos :+ modifiedphotoDetail
              }
            case None =>
              println("Not found the photo" + i.toString())
          })
      case None =>
        println("album not found" + albumId)
    }

    var allTheAlbums = new AllPhotos(allPhotos)
    Some(allTheAlbums)
  }

  /*********************************GET ALL POSTS***********************************************************/

  def getAllPostsForUsers(userId: String, token: String): Option[AllPosts] = {

    var userRequesting = tokenMap(token)

    var allPosts = Seq.empty[Posts]
    users.find(_.id == Some(userId)) match {
      case Some(usr) =>
        usr.postlist.foreach((i: String) =>
          posts.find(_.id == Some(i)) match {
            case Some(pst) =>
              var modifiedPst = pst
              if (modifiedPst.key.contains(userRequesting)) {
                var pk = modifiedPst.key(userRequesting)
                var modifiedKeyValuePair = Map[String, String]()
                modifiedKeyValuePair += (userRequesting -> pk)
                modifiedPst = modifiedPst.copy(key = modifiedKeyValuePair)
                allPosts = allPosts :+ modifiedPst
              }
            case None =>
              println("Did not find the post")
          })
      case None =>
        println("Did not find the user")
    }
    var allThePosts = new AllPosts(allPosts)
    Some(allThePosts)
  }

  def getAllPostsForPages(pageId: String, token: String): Option[AllPosts] = {
    var userRequesting = tokenMap(token)
    var allPosts = Seq.empty[Posts]
    pages.find(_.id == Some(pageId)) match {
      case Some(pgs) =>
        pgs.postlist.foreach((i: String) =>
          posts.find(_.id == Some(i)) match {
            case Some(pst) =>
              var modifiedPst = pst
              if (modifiedPst.key.contains(userRequesting)) {
                var pk = modifiedPst.key(userRequesting)
                var modifiedKeyValuePair = Map[String, String]()
                modifiedKeyValuePair += (userRequesting -> pk)
                modifiedPst = modifiedPst.copy(key = modifiedKeyValuePair)
                allPosts = allPosts :+ modifiedPst
              }
            case None =>
              println("Did not find the post")
          })

      case None =>
        println("Did not find the page")
    }
    var allThePosts = new AllPosts(allPosts)
    Some(allThePosts)
  }

  /******************************************GET ALL COMMENTS**************************************************/

  def getAllCommentsForPost(postId: String): Option[AllComments] = {
    var allComments = Seq.empty[Comment]
    posts.find(_.id == Some(postId)) match {
      case Some(pst) =>
        pst.commentList.foreach((i: String) =>
          comments.find(_.id == Some(i)) match {
            case Some(cmnt) =>
              allComments = allComments :+ cmnt
            case None =>
              println("Did not find the comment")
          })
      case None =>
        println("Did not find the post")
    }
    var allTheComments = new AllComments(allComments)
    Some(allTheComments)
  }

  def getAllCommentsForAlbum(albumID: String): Option[AllComments] = {
    var allComments = Seq.empty[Comment]
    albums.find(_.id == Some(albumID)) match {
      case Some(pst) =>
        pst.commentlist.foreach((i: String) =>
          comments.find(_.id == Some(i)) match {
            case Some(cmnt) =>
              allComments = allComments :+ cmnt
            case None =>
              println("Did not find the comment")
          })
      case None =>
        println("Did not find the album")
    }
    var allTheComments = new AllComments(allComments)
    Some(allTheComments)
  }

  def getAllCommentsForPhoto(photoID: String): Option[AllComments] = {
    var allComments = Seq.empty[Comment]
    photos.find(_.id == Some(photoID)) match {
      case Some(pst) =>
        pst.commentlist.foreach((i: String) =>
          comments.find(_.id == Some(i)) match {
            case Some(cmnt) =>
              allComments = allComments :+ cmnt
            case None =>
              println("Did not find the comment")
          })
      case None =>
        println("Did not find the photo")
    }
    var allTheComments = new AllComments(allComments)
    Some(allTheComments)
  }
  /****************************************GET BY ID*******************************************************************/
  private def getUserData(id: String): Option[User] = {
    users.find(_.id == Some(id))
  }
  private def getPageData(id: String): Option[Page] = {
    pages.find(_.id == Some(id))
  }
  private def getAlbumForUser(id: String): Option[Album] = {
    albums.find(_.id == Some(id))
  }
  private def getPosts(userid: String, postid: String): Option[Posts] = {
    return posts.find(_.id == Some(postid))
  }
  private def getComments(commentsId: String): Option[Comment] = {
    return comments.find(_.id == Some(commentsId))
  }
  private def getPhotoById(userid: String, photoId: String, token: String): Option[Photo] = {
    var userRequesting = tokenMap(token)
    var modifiedphotoDetail: Photo = null
    photos.find(_.id == Some(photoId)) match {
      case Some(photoDetail) =>
        var modifiedphotoDetail = photoDetail
        if (modifiedphotoDetail.key.contains(userRequesting)) {
          var pk = modifiedphotoDetail.key(userRequesting)
          var modifiedKeyValuePair = Map[String, String]()
          modifiedKeyValuePair += (userRequesting -> pk)
          modifiedphotoDetail = modifiedphotoDetail.copy(key = modifiedKeyValuePair)
          return Some(modifiedphotoDetail)
        } else {
          return None
        }
      case None =>
        return None

    }

  }
  /**************************************************UPDATE*************************************************************/

  private def updatePost(postid: String, newMessage: MessageFormat) {
    posts.find(_.id == Some(postId)) match {
      case Some(pst) =>
        var index = posts.indexOf(pst)
        var newmsg = pst.copy(message = Some(newMessage.message), updatedTimeStamp = new Date().toString())
        posts = posts.updated(index, newmsg)
      case None =>
    }
  }
  /**************************************DELETION*****************************************************************/

  private def deleteValue(id: String, value: String, predId: String, predType: String): Boolean = {

    value match {
      case "Users" =>
        users.find(_.id == Some(id)) match {
          case Some(usr) =>
            usr.postlist.foreach((i: String) =>
              deleteValue(i, "Posts", "", ""))
            usr.albumlist.foreach((i: String) =>
              deleteValue(i, "Album", "", ""))
            usr.userPages.foreach((i: String) =>
              deleteValue(i, "Pages", "", ""))
            var index = users.indexOf(usr)
            users = users.patch(index, Nil, 1)
          case None => println("User not found")
        }
      case "Pages" =>
        pages.find(_.id == Some(id)) match {
          case Some(pst) =>
            pst.postlist.foreach((i: String) =>
              deleteValue(i, "Posts", "", ""))
            pst.albumlist.foreach((i: String) =>
              deleteValue(i, "Album", "", ""))
            var usrId = pst.owner
            users.find(_.id == Some(usrId)) match {
              case Some(usr) =>
                var indexofUser = users.indexOf(usr)
                var updatedList = usr.userPages.filterNot { Some(_) == pst.id }
                var newUser = usr.copy(userPages = updatedList)
                users = users.updated(indexofUser, newUser)
              case None =>
                println("User not found")
            }
            var index = pages.indexOf(pst)
            pages = pages.patch(index, Nil, 1)
          case None => println("Page not found")
        }
      case "Posts" =>
        posts.find(_.id == Some(id)) match {
          case Some(pst) =>
            if (predType == "Users") {
              users.find(_.id == Some(predId)) match {
                case Some(usr) =>
                  var indexofUser = users.indexOf(usr)
                  var updatedList = usr.postlist.filterNot { Some(_) == pst.id }
                  var newUser = usr.copy(postlist = updatedList)
                  users = users.updated(indexofUser, newUser)
                case None =>
                  println("Didnt get the user for deleting the posts")
              }
            } else if (predType == "Pages") {
              pages.find(_.id == Some(predId)) match {
                case Some(pg) =>
                  var indexofPages = pages.indexOf(pg)
                  var updatedList = pg.postlist.filterNot { Some(_) == pst.id }
                  var newPage = pg.copy(postlist = updatedList)
                  pages = pages.updated(indexofPages, newPage)
                case None =>
                  println("Didnt get the page for deleting the posts")
              }
            }
            pst.commentList.foreach((i: String) =>
              deleteComments(i))
            var index = posts.indexOf(pst)
            posts = posts.patch(index, Nil, 1)
          case None => println("Did not get the post")
        }
      case "Photos" =>
        photos.find(_.id == Some(id)) match {
          case Some(pst) =>
            pst.commentlist.foreach((i: String) =>
              deleteComments(i))
            albums.find(_.id == Some(predId)) match {
              case Some(al) =>
                var indexofAlbum = albums.indexOf(al)
                var updatedList = al.photolist.filterNot { Some(_) == pst.id }
                var newAlbum = al.copy(photolist = updatedList)
                albums = albums.updated(indexofAlbum, newAlbum)
              case None =>
                println("Did not find album")
            }
            var index = photos.indexOf(pst)
            photos = photos.patch(index, Nil, 1)
          case None => println("Did not get photo")
        }
      case "Album" =>
        albums.find(_.id == Some(id)) match {
          case Some(pst) =>
            pst.commentlist.foreach((i: String) =>
              deleteComments(i))
            pst.commentlist.foreach((i: String) =>
              deleteValue(i, "Photos", "", ""))
            if (predType == "Users") {
              users.find(_.id == Some(predId)) match {
                case Some(usr) =>
                  var indexofUser = users.indexOf(usr)
                  var updatedList = usr.albumlist.filterNot { Some(_) == pst.id }
                  var newUser = usr.copy(postlist = updatedList)
                  users = users.updated(indexofUser, newUser)
                case None =>
                  println("Did not find user")
              }
            } else if (predType == "Pages") {
              pages.find(_.id == Some(predId)) match {
                case Some(pgs) =>
                  var indexofPages = pages.indexOf(pgs)
                  var updatedList = pgs.albumlist.filterNot { Some(_) == pst.id }
                  var newPage = pgs.copy(postlist = updatedList)
                  pages = pages.updated(indexofPages, newPage)
                case None =>
              }
            }
            var index = albums.indexOf(pst)
            albums = albums.patch(index, Nil, 1)
          case None => println("Did not find album")
        }
    }
    true
  }

  private def deleteComments(i: String) {
    comments.find(_.id == Some(i)) match {
      case Some(cmnt) =>
        var commentIndex = comments.indexOf(cmnt)
        comments = comments.patch(commentIndex, Nil, 1)
      case None =>
        println("Not found the comments")
    }
  }

  def checkPermissionForPages(pageId: String, token: String, requestType: String, objectType: String, objectID: String): Boolean = {
    if (JsonWebToken.validate(token, "PrivateServerKey")) {
      var userRequesting = tokenMap(token)
      pages.find(_.id == Some(pageId)) match {
        case Some(pg) =>
          if (pg.owner == userRequesting) {
            return true
          }
          var permLevel = 0
          if (requestType == "POST") {
            permLevel = pg.postOnMyWallPermission
          } else if (requestType == "GET") {
            objectType match {
              case "Posts" =>
                posts.find(_.id == Some(objectID)) match {
                  case Some(pst) =>
                    permLevel = pst.viewPermission
                  case None =>
                    return false
                }
              case "Photos" =>
                posts.find(_.id == Some(objectID)) match {
                  case Some(pht) =>
                    permLevel = pht.viewPermission
                  case None =>
                    return false
                }
              case _ =>
                return false
            }
          }
          users.find(_.id == Some(pg.owner)) match {
            case Some(usr) =>
              permLevel match {
                case ONLY_ME =>
                  return false
                case FRIENDS_ONLY =>
                  if (usr.friendlist.contains(userRequesting)) {
                    return true
                  } else
                    return false
                case PUBLIC =>
                  return true
                case _ =>
                  return false
              }
            case None =>
              return false

          }
        case None =>
          return false
      }

    } else {
      return false
    }
  }

  //CREATE RESPONDER FOR THE REQUEST
  private def createResponder(requestContext: RequestContext) = {
    context.actorOf(Props(new Responder(requestContext)))
  }

}

class Responder(requestContext: RequestContext) extends Actor with ActorLogging {
  import MooProtocol._
  def receive = {
    case UserCreated(userId) =>
      requestContext.complete(StatusCodes.Created, userId)
      killYourself
    case UserNotFound() =>
      requestContext.complete(StatusCodes.NotFound)
      killYourself
    case user: User =>
      requestContext.complete(StatusCodes.OK, user)
      killYourself
    case PageCreated(pgId) =>
      requestContext.complete(StatusCodes.Created, pgId)
      killYourself
    case CommentsCreated(cmtID) =>
      requestContext.complete(StatusCodes.Created, cmtID)
      killYourself
    case PostCreated(pgId) =>
      requestContext.complete(StatusCodes.Created, pgId)
      killYourself
    case FriendRequestCreated() =>
      requestContext.complete(StatusCodes.Created)
      killYourself
    case AcceptFriendRequest() =>
      requestContext.complete(StatusCodes.Created)
      killYourself
    case PageNotFound() =>
      requestContext.complete(StatusCodes.NotFound)
      killYourself
    case page: Page =>
      requestContext.complete(StatusCodes.OK, page)
      killYourself
    case post: Posts =>
      requestContext.complete(StatusCodes.OK, post)
      killYourself
    case comments: Comment =>
      requestContext.complete(StatusCodes.OK, comments)
      killYourself
    case ids: Ids =>
      requestContext.complete(StatusCodes.OK, ids)
      killYourself
    case LikeIncluded() =>
      requestContext.complete(StatusCodes.OK)
      killYourself
    case allPosts: AllPosts =>
      requestContext.complete(StatusCodes.OK, allPosts)
      killYourself
    case allPages: AllPages =>
      requestContext.complete(StatusCodes.OK, allPages)
      killYourself
    case AlbumCreated(albId) =>
      requestContext.complete(StatusCodes.Created, albId)
      killYourself
    case album: Album =>
      requestContext.complete(StatusCodes.OK, album)
      killYourself
    case allAlbums: AllAlbums =>
      requestContext.complete(StatusCodes.OK, allAlbums)
      killYourself
    case PhotoUploaded(phtID) =>
      requestContext.complete(StatusCodes.Created, phtID)
      killYourself
    case allPhotos: AllPhotos =>
      requestContext.complete(StatusCodes.OK, allPhotos)
      killYourself
    case photo: Photo =>
      requestContext.complete(StatusCodes.OK, photo)
      killYourself
    case allComments: AllComments =>
      requestContext.complete(StatusCodes.OK, allComments)
      killYourself
    case Deleted() =>
      requestContext.complete(StatusCodes.OK)
      killYourself
    case NotFound() =>
      requestContext.complete(StatusCodes.NotFound)
      killYourself
    case updatedPosts() =>
      requestContext.complete(StatusCodes.Created)
      killYourself
    case UserRegisterd() =>
      requestContext.complete(StatusCodes.Created)
      killYourself
    case passRandonNumber(randomNumber) =>
      requestContext.complete(StatusCodes.Created, randomNumber)
      killYourself
    case SignatureVerified(token) =>
      requestContext.complete(StatusCodes.OK, token)
      killYourself
    case SignatureDenied() =>
      requestContext.complete(StatusCodes.Unauthorized)
      killYourself
    case sendPublicKey(pkKey) =>
      requestContext.complete(StatusCodes.OK, pkKey)
      killYourself
    case logoffSuccess() =>
      requestContext.complete(StatusCodes.OK)
      killYourself
  }
  private def killYourself = self ! PoisonPill
}