package main.scala

import scala.concurrent.{ Await, ExecutionContext, Future }
import scala.concurrent.duration._
import akka.actor._
import akka.actor.ActorRef
import spray.client._
import akka.serialization._
import spray.http._
import HttpMethods._
import spray.json._
import DefaultJsonProtocol._
import spray.httpx.SprayJsonSupport._
import org.apache.commons.codec.binary.Base64
import ClientProtocol._
import scala.util.{ Success, Failure }
import scala.concurrent.duration._
import akka.actor.ActorSystem
import akka.pattern.ask
import akka.event.Logging
import akka.io.IO
import spray.json.{ JsonFormat, DefaultJsonProtocol }
import spray.can.Http
import spray.httpx.SprayJsonSupport
import spray.client.pipelining._
import spray.util._
import spray.http._
import spray.json.DefaultJsonProtocol
import spray.httpx.encoding.{ Gzip, Deflate }
import spray.httpx.SprayJsonSupport._
import spray.client.pipelining._
import akka.util.Timeout
import java.util.concurrent.TimeUnit
import scala.concurrent.{ Await, ExecutionContext, Future }
import scala.concurrent.duration._
import akka.actor._
import scala.concurrent.ExecutionContext
import ExecutionContext.Implicits.global
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Date
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import javax.crypto.KeyGenerator;
import java.net.URLEncoder;
import org.apache.commons.codec.binary.Base64;
import java.security.Signature
import com.typesafe.config.ConfigFactory
import javax.crypto.KeyGenerator;
import java.security.spec.X509EncodedKeySpec
import java.security.KeyFactory
import akka.actor.ExtendedActorSystem

case class CreateNewUser(UserDetails: User, profileScore: Int)
case class Simulate()
case class encryptThePostKeys(pst: Seq[Posts], usrsPublicKey: PublicKey)
case class encryptTheKey(key: String, usrsPublicKey: PublicKey)

class ClientMethodDefinitions(id: Integer, mstrActor: ActorRef) extends Actor {
  val MAX = 3600;
  var clientId: Integer = id;
  var userid: String = null;
  var userProfile: Int = 0;
  var encryptID = 0
  var sessionToken: String = ""
  var masterActor = mstrActor
  var userPublicKeyPair = Map[String, PublicKey]()
  var ONLY_ME = 1; var FRIENDS_ONLY = 2; var PUBLIC = 3
  var viewPermission = Array(ONLY_ME, FRIENDS_ONLY, PUBLIC)
  var num = 2

  def receive = {

    case CreateNewUser(userDetails: User, profileScore: Int) =>
      val usrid = CreateNewUser(userDetails)
      userid = usrid
      userProfile = profileScore
      createPublicPrivateKeyPair()
      sendPublicKeyToServer(new Mapping(userid, publiknyckelString))
      Thread.sleep(1000)
      masterActor ! storePublicKey(userid, publiknyckel)
      login()
      Thread.sleep(1000)
      implicit val timeout = Timeout(10 seconds)
      val future = masterActor ? sendUsersPublicKey()
      userPublicKeyPair = Await.result(future, timeout.duration).asInstanceOf[Map[String, PublicKey]]

    case Simulate() =>
      runProfile(userProfile);
  }

  //----------------------------------------------------------------------------------------------------//
  //                                       REQUEST FUNCTIONS                                            //
  //----------------------------------------------------------------------------------------------------//

  def getPublicKey(friendId: String): Future[String] = {
    val pipeline1: HttpRequest => Future[String] = (
      addHeader("SessionToken", sessionToken)
      ~> sendReceive
      ~> unmarshal[String])
    pipeline1(Get("http://localhost:8080/getPublicKey/" + friendId))
  }

  def loginToTheServer(): String = {
    val pipeline1: HttpRequest => Future[String] = (
      sendReceive
      ~> unmarshal[String])
    implicit val timeout = Timeout(Duration(1000, TimeUnit.SECONDS))
    val future = pipeline1(Post("http://localhost:8080/login/" + userid))
    val rndmNumber: String = Await.result(future, timeout.duration).asInstanceOf[String]
    rndmNumber
  }
  def verifyTheSignature(rndmNumber: String, signatureString: String) {
    val pipeline2: HttpRequest => Future[String] = (
      sendReceive
      ~> unmarshal[String])
    var messageSignature = new Mapping(rndmNumber, signatureString)
    val response: Future[String] = pipeline2(Post("http://localhost:8080/verify/" + userid, messageSignature))
    response onComplete {
      case Success(token) =>
        sessionToken = token
      case Failure(error) =>
        login()
    }
  }
  //Friend Suggestion
  def GetFriendSuggestions(): Future[Ids] = {

    val pipeline1: HttpRequest => Future[Ids] = (
      addHeader("SessionToken", sessionToken)
      ~> sendReceive
      ~> unmarshal[Ids])
    pipeline1(Get("http://localhost:8080/Users/" + userid + "/" + "friendSuggestion"))

  }

  //Likes

  def CreateNewLikesonPost(like: Like, objid: String, Obj: String, postid: String): Future[String] = {

    val pipeline1: HttpRequest => Future[String] = (
      addHeader("SessionToken", sessionToken)
      ~> sendReceive
      ~> unmarshal[String])
    pipeline1(Post("http://localhost:8080/" + Obj + "/" + objid + "/" + "posts/" + postid + "/likes", like))

  }

  def CreateNewLikesonAlbum(like: Like, objid: String, Obj: String, albid: String): Future[String] = {

    val pipeline1: HttpRequest => Future[String] = (
      addHeader("SessionToken", sessionToken)
      ~> sendReceive
      ~> unmarshal[String])
    pipeline1(Post("http://localhost:8080/" + Obj + "/" + objid + "/" + "albums/" + albid + "/likes", like))

  }

  def CreateNewLikeOnPage(like: Like, PageId: String): Future[String] = {

    val pipeline1: HttpRequest => Future[String] = (
      addHeader("SessionToken", sessionToken)
      ~> sendReceive
      ~> unmarshal[String])
    pipeline1(Post("http://localhost:8080/Pages/" + PageId + "/likes", like))

  }

  //Friends

  def MakeFriendRequest(from: String, to: String): Future[String] = {

    val pipeline1: HttpRequest => Future[String] = (
      addHeader("SessionToken", sessionToken)
      ~> sendReceive
      ~> unmarshal[String])
    pipeline1(Post("http://localhost:8080/Users/" + from + "/friendRequest/" + to))

  }

  def AcceptFriendRequest(from: String, to: String, response: String): Future[String] = {

    val pipeline1: HttpRequest => Future[String] = (
      addHeader("SessionToken", sessionToken)
      ~> sendReceive
      ~> unmarshal[String])
    pipeline1(Post("http://localhost:8080/Users/" + from + "/acceptRequest/" + to + "/" + response))

  }

  //posts

  def getAllPosts(Obj: String, objid: String): Future[AllPosts] = {

    val pipeline1: HttpRequest => Future[AllPosts] = (
      addHeader("SessionToken", sessionToken)

      ~> sendReceive
      ~> unmarshal[AllPosts])
    pipeline1(Get("http://localhost:8080/" + Obj + "/" + objid + "/posts"))

  }

  def CreateNewPost(postDetails: Posts, objid: String, Obj: String): Future[String] = {

    val pipeline1: HttpRequest => Future[String] = (
      addHeader("SessionToken", sessionToken)

      ~> sendReceive
      ~> unmarshal[String])
    pipeline1(Post("http://localhost:8080/" + Obj + "/" + objid + "/posts", postDetails))

  }

  def GetNewPost(postid: String, objid: String, Obj: String): Future[Posts] = {

    val pipeline1: HttpRequest => Future[Posts] = (
      addHeader("SessionToken", sessionToken)
      ~> sendReceive
      ~> unmarshal[Posts])
    pipeline1(Get("http://localhost:8080/" + Obj + "/" + objid + "/posts/" + postid))

  }

  //Comments

  def CreateNewCommentonPost(commentDetails: Comment, objid: String, Obj: String, postid: String): Future[String] = {

    val pipeline1: HttpRequest => Future[String] = (
      addHeader("SessionToken", sessionToken)
      ~> sendReceive
      ~> unmarshal[String])
    pipeline1(Post("http://localhost:8080/" + Obj + "/" + objid + "/posts/" + postid + "/comments", commentDetails))

  }

  def GetCommentonPost(commentId: String, objid: String, Obj: String, postid: String): Future[Comment] = {

    val pipeline1: HttpRequest => Future[Comment] = (
      addHeader("SessionToken", sessionToken)
      ~> sendReceive
      ~> unmarshal[Comment])
    pipeline1(Get("http://localhost:8080/" + Obj + "/" + objid + "/posts/" + postid + "/comments/" + commentId))

  }

  def CreateNewCommentonPhoto(commentDetails: Comment, objid: String, Obj: String, albid: String, PhotoId: String): Future[String] = {

    val pipeline1: HttpRequest => Future[String] = (
      addHeader("SessionToken", sessionToken)
      ~> sendReceive
      ~> unmarshal[String])
    pipeline1(Post("http://localhost:8080/" + Obj + "/" + objid + "/album/" + albid + "/photos/" + PhotoId + "/comments", commentDetails))

  }

  def GetCommentsonPhoto(commentId: String, objid: String, Obj: String, albid: String, PhotoId: String): Future[Comment] = {

    val pipeline1: HttpRequest => Future[Comment] = (
      addHeader("SessionToken", sessionToken)
      ~> sendReceive
      ~> unmarshal[Comment])
    pipeline1(Get("http://localhost:8080/" + Obj + "/" + objid + "/albums/" + albid + "/photos/" + PhotoId + "/comments/" + commentId))

  }

  def CreateNewCommentOnAlbum(commentDetails: Comment, objid: String, Obj: String, albid: String): Future[String] = {

    val pipeline1: HttpRequest => Future[String] = (
      addHeader("SessionToken", sessionToken)
      ~> sendReceive
      ~> unmarshal[String])

    pipeline1(Post("http://localhost:8080/" + Obj + "/" + objid + "/album/" + albid + "/comments", commentDetails))

  }

  def GetCommentsOnAlbum(commentId: String, objid: String, Obj: String, albid: String): Future[Comment] = {

    val pipeline1: HttpRequest => Future[Comment] = (
      addHeader("SessionToken", sessionToken)
      ~> sendReceive
      ~> unmarshal[Comment])

    pipeline1(Get("http://localhost:8080/" + Obj + "/" + objid + "/albums/" + albid + "/comments/", commentId))

  }

  //users

  def CreateNewUser(userDetails: User): String = {

    val pipeline1: HttpRequest => Future[String] = (
      sendReceive
      ~> unmarshal[String])

    implicit val timeout = Timeout(Duration(10000000, TimeUnit.SECONDS))
    val response: Future[String] = pipeline1(Post("http://localhost:8080/Users", userDetails))
    val resp = Await.result(response, timeout.duration).asInstanceOf[String]
    return resp

  }

  def GetUserDetails(userId: String): Future[User] = {

    val pipeline: HttpRequest => Future[User] = (
      addHeader("SessionToken", sessionToken)
      ~> sendReceive
      ~> unmarshal[User])
    pipeline { Get("http://localhost:8080/Users/" + userId) }

  }

  //pages

  def CreateNewPage(PageDetails: Page): Future[String] = {

    val pipeline1: HttpRequest => Future[String] = (
      addHeader("SessionToken", sessionToken)
      ~> sendReceive
      ~> unmarshal[String])

    pipeline1(Post("http://localhost:8080/Pages", PageDetails))

  }

  def GetPageDetails(pageId: String): Future[Page] = {

    val pipeline: HttpRequest => Future[Page] = (
      addHeader("SessionToken", sessionToken)
      ~> sendReceive
      ~> unmarshal[Page])
    pipeline { Get("http://localhost:8080/Pages/" + pageId) }

  }

  //photos

  def uploadPics(photo: Photo, albumID: String, userID: String): Future[String] = {

    val pipeline1: HttpRequest => Future[String] = (
      addHeader("SessionToken", sessionToken)

      ~> sendReceive
      ~> unmarshal[String])

    pipeline1(Post("http://localhost:8080/Users/" + userID + "/album/" + albumID + "/photos", photo))
  }
  def createAlbum(album: Album, userID: String): Future[String] = {

    val pipeline1: HttpRequest => Future[String] = (
      addHeader("SessionToken", sessionToken)
      ~> sendReceive
      ~> unmarshal[String])

    pipeline1(Post("http://localhost:8080/Users/" + userID + "/album", album))

  }

  def getAlbumById(albumId: String, userID: String): Future[Album] = {

    val pipeline: HttpRequest => Future[Album] = (
      addHeader("SessionToken", sessionToken)
      ~> sendReceive
      ~> unmarshal[Album])
    pipeline { Get("http://localhost:8080/Users/" + userID + "/album/" + albumId) }

  }

  def getAlbumsOfUser(userID: String): Future[AllAlbums] = {

    val pipeline: HttpRequest => Future[AllAlbums] = (
      addHeader("SessionToken", sessionToken)
      ~> sendReceive
      ~> unmarshal[AllAlbums])
    pipeline { Get("http://localhost:8080/Users/" + userID + "/album/") }

  }

  def getAllPhotos(Obj: String, objid: String, albumId: String): Future[AllPhotos] = {

    val pipeline1: HttpRequest => Future[AllPhotos] = (
      addHeader("SessionToken", sessionToken)
      ~> sendReceive
      ~> unmarshal[AllPhotos])
    pipeline1(Get("http://localhost:8080/" + Obj + "/" + objid + "/album/" + albumId + "/photos"))

  }

  def getPhotoById(albumId: String, userID: String, photoid: String): Future[Photo] = {

    val pipeline: HttpRequest => Future[Photo] = (
      addHeader("SessionToken", sessionToken)
      ~> sendReceive
      ~> unmarshal[Photo])
    pipeline { Get("http://localhost:8080/Users/" + userID + "/album/" + albumId + "/photos/" + photoid) }

  }

  //SENDING MY PUBLIC KEY TO THE SERVER
  def sendPublicKeyToServer(usr: Mapping) {
    val pipeline1: HttpRequest => Future[String] = (
      addHeader("SessionToken", sessionToken)
      ~> sendReceive
      ~> unmarshal[String])

    val response: Future[String] = pipeline1(Post("http://localhost:8080/registerPublicKey", usr))
    response onComplete {
      case Success(msg)   =>
      case Failure(error) =>

    }
  }
  /*-------------------------------------------------------------------------------------------------*/
  //                                CLIENT SIMULATION FUNCTIONS                                             /
  /*-------------------------------------------------------------------------------------------------*/

  //ACTIONS
  // GET ONE OF MY POSTS
  def CreatePage() {
    var pageDetails: Page = Page(None, "PageName_" + id.toString, "User_" + id.toString, "About Page " + id.toString, "Link_" + id.toString, 0, Seq.empty[String], Seq.empty[String], Seq.empty[String], Seq.empty[String], viewPermission(util.Random.nextInt(viewPermission.size)))
    val response: Future[String] = CreateNewPage(pageDetails)
    response onComplete {
      case Success(pageid: String) =>
      case Failure(error)          =>
    }
  }

  //COMMENTS

  def commentOnMyOwnPhoto() {
    implicit val timeout = Timeout(Duration(1000, TimeUnit.SECONDS))
    val future = GetUserDetails(userid)
    var user: User = Await.result(future, timeout.duration).asInstanceOf[User]
    if (user.albumlist.length > 0) {
      var album_id = user.albumlist(util.Random.nextInt(user.albumlist.length))
      val future2 = getAlbumById(album_id, userid)
      var alb: Album = Await.result(future2, timeout.duration).asInstanceOf[Album]
      if (alb.photolist.length > 0) {
        val photoId: String = alb.photolist(util.Random.nextInt(alb.photolist.length))
        var commentDetails = Comment(None, Some(userid), "This is my comment" + userid, None, None, None, Seq.empty[String])
        val responseFuture: Future[String] = CreateNewCommentonPhoto(commentDetails, userid, "Users", album_id, photoId)
        responseFuture onComplete {
          case Success(commentID: String) =>
          case Failure(error)             =>

        }
      }
    }
  }

  def commentOnMyPosts() {
    implicit val timeout = Timeout(Duration(1000, TimeUnit.SECONDS))
    val future = GetUserDetails(userid)
    var user: User = Await.result(future, timeout.duration).asInstanceOf[User]
    if (user.postlist.length > 0) {
      val postId: String = user.postlist(util.Random.nextInt(user.postlist.length))
      val future2 = GetNewPost(postId, userid, "Users")
      var pst: Posts = Await.result(future2, timeout.duration).asInstanceOf[Posts]
      var commentDetails = Comment(None, Some(userid), "This is my comment" + userid, None, None, None, Seq.empty[String])
      val responseFuture: Future[String] = CreateNewCommentonPost(commentDetails, userid, "Users", postId)
      responseFuture onComplete {
        case Success(commentID: String) =>
        case Failure(error)             =>

      }
    }
  }
  def commentOnMyAlbum() {
    implicit val timeout = Timeout(Duration(1000, TimeUnit.SECONDS))
    val future = GetUserDetails(userid)
    var user: User = Await.result(future, timeout.duration).asInstanceOf[User]
    if (user.albumlist.length > 0) {
      var album_id = user.albumlist(util.Random.nextInt(user.albumlist.length))
      var commentDetails = Comment(None, Some(userid), "This is my comment on album" + userid, None, None, None, Seq.empty[String])
      val responseFuture: Future[String] = CreateNewCommentOnAlbum(commentDetails, userid, "Users", album_id)
      responseFuture onComplete {
        case Success(commentID: String) =>
        case Failure(error)             =>

      }
    }
  }
  def commentOnMyFriendsPhoto() {
    implicit val timeout = Timeout(Duration(1000, TimeUnit.SECONDS))
    val future = GetUserDetails(userid)
    var user: User = Await.result(future, timeout.duration).asInstanceOf[User]
    if (user.friendlist.length > 0) {
      val future2 = GetUserDetails(user.friendlist(util.Random.nextInt(user.friendlist.length)))
      var friend: User = Await.result(future2, timeout.duration).asInstanceOf[User]
      val friendid: String = friend.id.getOrElse("default value")
      if (friend.albumlist.length > 0) {
        var album_id = friend.albumlist(util.Random.nextInt(friend.albumlist.length))
        val future3 = getAlbumById(album_id, userid)
        var alb: Album = Await.result(future3, timeout.duration).asInstanceOf[Album]
        if (alb.photolist.length > 0) {
          val photoId: String = alb.photolist(util.Random.nextInt(alb.photolist.length))
          var commentDetails = Comment(None, Some(userid), "This is " + userid + "s coment on my friend" + userid, None, None, None, Seq.empty[String])
          val responseFuture: Future[String] = CreateNewCommentonPhoto(commentDetails, friendid, "Users", album_id, photoId)
          responseFuture onComplete {
            case Success(commentID: String) =>
            case Failure(error)             =>

          }
        }
      }
    }
  }

  def commentOnMyFriendsAlbum() {
    implicit val timeout = Timeout(Duration(1000, TimeUnit.SECONDS))
    val future = GetUserDetails(userid)
    var user: User = Await.result(future, timeout.duration).asInstanceOf[User]
    if (user.friendlist.length > 0) {
      val future2 = GetUserDetails(user.friendlist(util.Random.nextInt(user.friendlist.length)))
      var friend: User = Await.result(future2, timeout.duration).asInstanceOf[User]
      val friendid: String = friend.id.getOrElse("default value")
      if (friend.albumlist.length > 0) {
        var album_id = friend.albumlist(util.Random.nextInt(friend.albumlist.length))
        var commentDetails = Comment(None, Some(userid), "This is " + userid + "s coment on my friend " + friendid, None, None, None, Seq.empty[String])
        val responseFuture: Future[String] = CreateNewCommentOnAlbum(commentDetails, friendid, "Users", album_id)
        responseFuture onComplete {
          case Success(commentID: String) =>
          case Failure(error)             =>

        }
      }
    }
  }
  def commentOnMyFriendsPosts() {
    implicit val timeout = Timeout(Duration(1000, TimeUnit.SECONDS))
    val future = GetUserDetails(userid)
    var user: User = Await.result(future, timeout.duration).asInstanceOf[User]
    if (user.friendlist.length > 0) {
      val future2 = GetUserDetails(user.friendlist(util.Random.nextInt(user.friendlist.length)))
      var friend: User = Await.result(future2, timeout.duration).asInstanceOf[User]
      val friendid: String = friend.id.getOrElse("default value")
      if (friend.postlist.length > 0) {
        val postId: String = friend.postlist(util.Random.nextInt(friend.postlist.length))
        val future3 = GetNewPost(postId, friendid, "Users")
        var pst: Posts = Await.result(future3, timeout.duration).asInstanceOf[Posts]
        var commentDetails = Comment(None, Some(userid), "This is my comment on friend " + friendid + "post" + userid, None, None, None, Seq.empty[String])
        println(userid + " is commenting on his friend's " + friend.id + " post " + postId)
        val responseFuture: Future[String] = CreateNewCommentonPost(commentDetails, friendid, "Users", postId)
        responseFuture onComplete {
          case Success(commentID: String) =>
          case Failure(error)             =>

        }
      }
    }
  }

  //LIKES

  def likeMyOwnPost() {
    implicit val timeout = Timeout(Duration(1000, TimeUnit.SECONDS))
    val future = GetUserDetails(userid)
    var user: User = Await.result(future, timeout.duration).asInstanceOf[User]
    if (user.postlist.length > 0) {
      val postId: String = user.postlist(util.Random.nextInt(user.postlist.length))
      val future2 = GetNewPost(postId, userid, "Users")
      var pst: Posts = Await.result(future2, timeout.duration).asInstanceOf[Posts]
      var likeObj = Like(None, userid)
      println(userid + " liked his own post " + postId)
      val responseFuture: Future[String] = CreateNewLikesonPost(likeObj, userid, "Users", postId)
      responseFuture onComplete {
        case Success(likeID: String) =>

        case Failure(error)          =>

      }
    }
  }

  def likeMyAlbum() {
    implicit val timeout = Timeout(Duration(1000, TimeUnit.SECONDS))
    val future = GetUserDetails(userid)
    var user: User = Await.result(future, timeout.duration).asInstanceOf[User]
    if (user.albumlist.length > 0) {
      var album_id = user.albumlist(util.Random.nextInt(user.albumlist.length))
      var likeObj = Like(None, userid)
      val responseFuture: Future[String] = CreateNewLikesonAlbum(likeObj, userid, "Users", album_id)
      responseFuture onComplete {
        case Success(likeID: String) =>

        case Failure(error)          =>

      }
    }
  }

  def likeMyFriendsAlbum() {
    implicit val timeout = Timeout(Duration(1000, TimeUnit.SECONDS))
    val future = GetUserDetails(userid)
    var user: User = Await.result(future, timeout.duration).asInstanceOf[User]
    if (user.friendlist.length > 0) {
      val future2 = GetUserDetails(user.friendlist(util.Random.nextInt(user.friendlist.length)))
      var friend: User = Await.result(future2, timeout.duration).asInstanceOf[User]
      val friendid: String = friend.id.getOrElse("default value")
      if (friend.albumlist.length > 0) {
        var album_id = friend.albumlist(util.Random.nextInt(friend.albumlist.length))
        println(userid + " liked his friend's " + friend.id + " album ")
        var likeObj = Like(None, userid)
        val responseFuture: Future[String] = CreateNewLikesonAlbum(likeObj, friendid, "Users", album_id)
        responseFuture onComplete {
          case Success(likeID: String) =>
            println("Successfully created like " + likeID + " for user" + user + " on his friends " + friend.id + " album " + album_id)
          case Failure(error) =>

        }
      }
    }
  }
  def likeMyFriendsPosts() {
    implicit val timeout = Timeout(Duration(1000, TimeUnit.SECONDS))
    val future = GetUserDetails(userid)
    var user: User = Await.result(future, timeout.duration).asInstanceOf[User]
    if (user.friendlist.length > 0) {
      val future2 = GetUserDetails(user.friendlist(util.Random.nextInt(user.friendlist.length)))
      var friend: User = Await.result(future2, timeout.duration).asInstanceOf[User]
      val friendid: String = friend.id.getOrElse("default value")
      if (friend.postlist.length > 0) {
        val postId: String = friend.postlist(util.Random.nextInt(friend.postlist.length))
        var likeObj = Like(None, userid)
        println(userid + " liked his friend's " + friend.id + " post " + postId)
        val responseFuture: Future[String] = CreateNewLikesonPost(likeObj, friendid, "Users", postId)
        responseFuture onComplete {
          case Success(likeID: String) =>
            println("Successfully created like " + likeID + " for user" + user + " on his friends " + friend.id + " post " + postId)
          case Failure(error) =>

        }
      }
    }
  }
  def likeMyFriendsPage() {
    implicit val timeout = Timeout(Duration(1000, TimeUnit.SECONDS))
    val future = GetUserDetails(userid)
    var user: User = Await.result(future, timeout.duration).asInstanceOf[User]
    if (user.friendlist.length > 0) {
      val future2 = GetUserDetails(user.friendlist(util.Random.nextInt(user.friendlist.length)))
      var friend: User = Await.result(future2, timeout.duration).asInstanceOf[User]
      val friendid: String = friend.id.getOrElse("default value")
      if (friend.userPages.length > 0) {
        var page_id = friend.userPages(util.Random.nextInt(friend.userPages.length))
        println(userid + " liked his friend's " + friend.id + " page ")
        var likeObj = Like(None, userid)
        val responseFuture: Future[String] = CreateNewLikeOnPage(likeObj, page_id)
        responseFuture onComplete {
          case Success(likeID: String) =>
            println("Successfully created like " + likeID + " for user" + user + " on his friends " + friend.id + " page " + page_id)
          case Failure(error) =>

        }
      }

    }
  }
  def likeMyPage() {
    implicit val timeout = Timeout(Duration(1000, TimeUnit.SECONDS))
    val future = GetUserDetails(userid)
    var user: User = Await.result(future, timeout.duration).asInstanceOf[User]
    if (user.userPages.length > 0) {
      var page_id = user.userPages(util.Random.nextInt(user.userPages.length))
      var likeObj = Like(None, userid)
      println(userid + " liked his own page " + page_id)
      val responseFuture: Future[String] = CreateNewLikeOnPage(likeObj, page_id)
      responseFuture onComplete {
        case Success(likeID: String) =>
          println("Successfully created like " + likeID + " for user " + userid + "on his own page" + page_id)
        case Failure(error) =>

      }
    }
  }

  //FRIEND REQUESTS

  def MakeFriendRequest(num: Int) {

    val responseFuture = GetFriendSuggestions()
    var start1 = Duration(2, TimeUnit.SECONDS)
    implicit val timeout = Timeout(Duration(1000, TimeUnit.SECONDS))
    val future = GetFriendSuggestions()
    val (idlist) = Await.result(future, timeout.duration).asInstanceOf[Ids]
    var count = num
    if (idlist.id.size < num) {
      count = idlist.id.size
    }
    for (i <- 0 until count) {
      val friendid = idlist.id((util.Random.nextInt(count)));

      val response = MakeFriendRequest(userid, friendid);
      response onComplete {
        case Success(msg)   =>

        case Failure(error) =>

      }
    }

  }

  def ViewFriendRequest() {

    val responseFuture = GetUserDetails(userid)
    responseFuture onComplete {
      case Success(
        User(id, username, name, first_name, middle_name, last_name, email, link, gender, age, pages, postlist, albumlist, friendlist, friendrequestlist, clientId, permissionLevel)) =>
        var responseList = Seq("Accept", "Reject")
        val rsp: String = responseList(util.Random.nextInt(1))
        for (frnd <- friendrequestlist) {
          AcceptFriendRequest(userid, frnd, rsp)
        }
      case Failure(error) =>

    }
  }

  //Albums and Photos

  def createMyAlbum() = {
    var albumDetail = Album(None, "Album " + userid, "", Seq.empty[String], Seq.empty[String], 0, Seq.empty[String])
    val responseFuture: Future[String] = createAlbum(albumDetail, userid)
    responseFuture onComplete {
      case Success(albumid: String) =>
      case Failure(error)           =>

    }
  }
  //
  def uploadPicInMyAlbum() = {

    implicit val timeout = Timeout(Duration(1000, TimeUnit.SECONDS))
    val future = GetUserDetails(userid)
    var user: User = Await.result(future, timeout.duration).asInstanceOf[User]
    if (user.albumlist.length > 0) {
      var album_id = user.albumlist(util.Random.nextInt(user.albumlist.length))
      val file = "my-image.png"
      val bis = new BufferedInputStream(new FileInputStream(file))
      val bArray = Stream.continually(bis.read).takeWhile(-1 !=).map(_.toByte).toArray

      val bytes64 = Base64.encodeBase64(bArray)
      val content = new String(bytes64)
      var (finalContent, encryptedSymmkey, initializationVector) = encrypt(content, publiknyckel)
      var photoDetail = Photo(None, finalContent, "", "Photo" + userid, Seq.empty[String], Base64.encodeBase64String(initializationVector), encryptedSymmkey, 0, viewPermission(util.Random.nextInt(viewPermission.size)))
      val responseFuture: Future[String] = uploadPics(photoDetail, album_id, userid)
      responseFuture onComplete {
        case Success(picid: String) =>
        case Failure(error)         =>

      }
    }
  }

  //MY WALL

  def PostOnMyWall() = {
    //Encrypting the message using AES
    var (message, encryptedSymmkey, initializationVector) = encrypt("Message" + userid, publiknyckel)
    var postDetails: Posts = Posts(None, "My Story" + userid, new Date().toString(), userid, Some(message), "", 0, Seq.empty[String], Seq.empty[String], Base64.encodeBase64String(initializationVector), encryptedSymmkey, 0, viewPermission(util.Random.nextInt(viewPermission.size)))
    val responseFuture: Future[String] = CreateNewPost(postDetails, userid, "Users")
    responseFuture onComplete {
      case Success(postid: String) =>
      case Failure(error)          =>

    }
  }

  def PublishPostOnMyPage() = {
    //Encrypting the message using AES
    var (message, encryptedSymmkey, initializationVector) = encrypt("Message" + userid, publiknyckel)
    var postDetails: Posts = Posts(None, "My Story" + userid, new Date().toString(), userid, Some(message), "", 0, Seq.empty[String], Seq.empty[String], Base64.encodeBase64String(initializationVector), encryptedSymmkey, 0, viewPermission(util.Random.nextInt(viewPermission.size)))
    implicit val timeout = Timeout(Duration(1000, TimeUnit.SECONDS))
    val future = GetUserDetails(userid)
    val user: User = Await.result(future, timeout.duration).asInstanceOf[User]
    if ((user.userPages.length) > 0) {
      val responseFuture: Future[String] = CreateNewPost(postDetails, user.userPages(util.Random.nextInt(user.userPages.length)), "Pages")
      responseFuture onComplete {
        case Success(postid: String) =>
        case Failure(error)          =>

      }
    } else {
      println("No pages found for the User")
    }
  }

  def PublishPostOnFriendsPage() = {
    val friend = getMyFriends()
    val friendid: String = friend.id.getOrElse("default value")
    implicit val timeout = Timeout(Duration(1000, TimeUnit.SECONDS))
    if (friend.userPages.length > 0) {
      val future3 = getPublicKey(friend.id.getOrElse(""))
      var publicKey: String = Await.result(future3, timeout.duration).asInstanceOf[String]
      //Obtaining friends public key  to encrypt the AES key 
      var pubKey = convertToPublicKey(publicKey)
      //Encrypting the message using AES
      var (message, encryptedSymmkey, initializationVector) = encrypt("Message" + userid, pubKey)
      var postDetails: Posts = Posts(None, "My Story" + userid, new Date().toString(), userid, Some(message), "", 0, Seq.empty[String], Seq.empty[String], Base64.encodeBase64String(initializationVector), encryptedSymmkey, 0, viewPermission(util.Random.nextInt(viewPermission.size)))
      val responseFuture: Future[String] = CreateNewPost(postDetails, friend.userPages(util.Random.nextInt(friend.userPages.length)), "Pages")
      responseFuture onComplete {
        case Success(postid: String) =>
        case Failure(error)          =>
      }
    }

  }

  def PostOnFriendsWall() = {
    val friend = getMyFriends()
    val friendid: String = friend.id.getOrElse("default value")
    implicit val timeout = Timeout(Duration(1000, TimeUnit.SECONDS))
    //Obtaining friends public key  to encrypt the AES key
    val future3 = getPublicKey(friendid)
    var publicKey: String = Await.result(future3, timeout.duration).asInstanceOf[String]
    var pubKey = convertToPublicKey(publicKey)
    //Encrypting the message using AES
    var (message, encryptedSymmkey, initializationVector) = encrypt("Message" + userid, pubKey)
    var postDetails: Posts = Posts(None, "My Story" + userid, new Date().toString(), userid, Some(message), "", 0, Seq.empty[String], Seq.empty[String], Base64.encodeBase64String(initializationVector), encryptedSymmkey, 0, viewPermission(util.Random.nextInt(viewPermission.size)))
    val responseFuture: Future[String] = CreateNewPost(postDetails, friendid, "Users")
    responseFuture onComplete {
      case Success(postid: String) =>
      case Failure(error)          =>

    }

  }

  //TimeLine

  def GetMyTimeLine() {
    val responseFuture: Future[AllPosts] = getAllPosts("Users", userid)
    responseFuture onComplete {
      case Success(mypost: AllPosts) =>
        var post = decryptPost(mypost.post)
        var stringToDisplay = userid + " is viewing all his posts"
        displayPost(stringToDisplay, mypost.post, post)
      case Failure(error) =>

    }
  }

  def GetMyPageTimeLine() {
    implicit val timeout = Timeout(Duration(1000, TimeUnit.SECONDS))
    val future = GetUserDetails(userid)
    val user: User = Await.result(future, timeout.duration).asInstanceOf[User]
    if ((user.userPages.length) > 0) {
      val responseFuture: Future[AllPosts] = getAllPosts("Pages", user.userPages(util.Random.nextInt(user.userPages.length)))
      responseFuture onComplete {
        case Success(mypost: AllPosts) =>
          var post = decryptPost(mypost.post)
          var stringToDisplay = userid + " is viewing all the posts on his page"
          displayPost(stringToDisplay, mypost.post, post)
        case Failure(error) =>

      }
    }
  }

  def SeeFriendsPageTimeLine() {
    implicit val timeout = Timeout(Duration(1000, TimeUnit.SECONDS))
    val friend = getMyFriends()
    val friendid: String = friend.id.getOrElse("default value")
    if ((friend.userPages.length) > 0) {
      val responseFuture: Future[AllPosts] = getAllPosts("Pages", friend.userPages(util.Random.nextInt(friend.userPages.length)))
      responseFuture onComplete {
        case Success(mypost: AllPosts) =>
          var stringToDisplay = userid + " is viewing all the posts on " + friendid + "'s page"
          var post = decryptPost(mypost.post)
          displayPost(stringToDisplay, mypost.post, post)
        case Failure(error) =>

      }
    }
  }

  def SeeFriendsTimeLine() {
    val friend = getMyFriends()
    val friendid: String = friend.id.getOrElse("default value")
    val responseFuture: Future[AllPosts] = getAllPosts("Users", friendid)
    responseFuture onComplete {
      case Success(mypost: AllPosts) =>
        var newPost = Seq.empty[Posts]
        var post = decryptPost(mypost.post)
        var stringToDisplay = userid + " is viewing all the posts on " + friendid + "'s wall"
        displayPost(stringToDisplay, mypost.post, post)
      case Failure(error) =>

    }
  }

  //GET MY PHOTOS
  def GetAllMyPhotos() {
    implicit val timeout = Timeout(Duration(1000, TimeUnit.SECONDS))
    val future = GetUserDetails(userid)
    var user: User = Await.result(future, timeout.duration).asInstanceOf[User]
    if (user.albumlist.length > 0) {
      var album_id = user.albumlist(util.Random.nextInt(user.albumlist.length))
      val future2 = getAlbumById(album_id, userid)
      var alb: Album = Await.result(future2, timeout.duration).asInstanceOf[Album]
      if (alb.photolist.length > 0) {
        val responseFuture: Future[AllPhotos] = getAllPhotos("Users", userid, album_id)
        responseFuture onComplete {
          case Success(myphoto: AllPhotos) =>
            var photo = decryptPhotos(myphoto.photos)
            saveToMyDisk(photo)
          case Failure(error) =>

        }

      }
    }

  }

  def getMyFriendsPhoto() = {
    implicit val timeout = Timeout(Duration(1000, TimeUnit.SECONDS))
    val friend = getMyFriends()
    val friendid: String = friend.id.getOrElse("default value")
    if (friend.albumlist.length > 0) {
      var album_id = friend.albumlist(util.Random.nextInt(friend.albumlist.length))
      val future3 = getAlbumById(album_id, userid)
      var alb: Album = Await.result(future3, timeout.duration).asInstanceOf[Album]
      if (alb.photolist.length > 0) {
        val photoId: String = alb.photolist(util.Random.nextInt(alb.photolist.length))
        val responseFuture: Future[Photo] = getPhotoById(album_id, friendid, photoId)
        responseFuture onComplete {
          case Success(photo: Photo) =>
            decryptFriendsPhoto(photo, friendid)
          case Failure(error) =>

        }
      }
    }
  }

  //Common Functions
  def getMyFriends(): User = {
    implicit val timeout = Timeout(Duration(1000, TimeUnit.SECONDS))
    val future = GetUserDetails(userid)
    var user: User = Await.result(future, timeout.duration).asInstanceOf[User]
    val future2 = GetUserDetails(user.friendlist(util.Random.nextInt(user.friendlist.length)))
    var friend: User = Await.result(future2, timeout.duration).asInstanceOf[User]
    friend
  }

  /********************************************ENCRYPTION*************************************************************/

  var privatnyckel: PrivateKey = null
  var publiknyckel: PublicKey = null
  var publiknyckelString: String = ""

  // LOGIN INTO THE SERVER
  def login() {
    var rndmNumber = loginToTheServer()
    var nonce = Base64.decodeBase64(rndmNumber)
    var instance = Signature.getInstance("SHA1withRSA");
    instance.initSign(privatnyckel);
    instance.update((nonce));
    var signature = instance.sign();
    var signatureString = Base64.encodeBase64String(signature)
    verifyTheSignature(rndmNumber, signatureString)
  }

  // GENERATE PUBLIC PRIVATE KEY PAIR
  def createPublicPrivateKeyPair() {
    var gen2: KeyPairGenerator = KeyPairGenerator.getInstance("RSA");
    gen2.initialize(1024);
    var keyPair: KeyPair = gen2.genKeyPair();
    privatnyckel = keyPair.getPrivate();
    publiknyckel = keyPair.getPublic();
    var publicKeyBytes = publiknyckel.getEncoded();
    publiknyckelString = Base64.encodeBase64String(publicKeyBytes);
  }

  // GENERATE THE IV
  def generateIV(): Array[Byte] = {
    var randomSecureRandom = SecureRandom.getInstance("SHA1PRNG");
    var iv = new Array[Byte](16);
    randomSecureRandom.nextBytes(iv);
    iv
  }
  
  // GENERATE THE KEY
  def generateKey(): (SecretKeySpec, Array[Byte]) = {
    var kgen: KeyGenerator = KeyGenerator.getInstance("AES");
    kgen.init(128);
    var key: SecretKey = kgen.generateKey();
    var aesKey = key.getEncoded();
    var skeySpec: SecretKeySpec = new SecretKeySpec(aesKey, "AES");
    (skeySpec, aesKey)
  }

  //ENCRYPTING THE DATA
  def encrypt(value: String, publicKey: PublicKey): (String, Map[String, String], Array[Byte]) = {
    var initVector = generateIV()
    var iv: IvParameterSpec = new IvParameterSpec(initVector);
    var cipher: Cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING");
    var (skeySpec, aesKey) = generateKey()
    cipher.init(Cipher.ENCRYPT_MODE, skeySpec, iv);
    var enrypted = cipher.doFinal(value.getBytes());
    var friendList = createListOfSharedUsers(num)
    var encryptedKeyUserMap = Map[String, String]()
    var pub = userPublicKeyPair(userid)
    var enryptedSymKey = encryptKeyUsingRSA(aesKey, pub).asInstanceOf[(Array[Byte])]
    encryptedKeyUserMap += (userid -> Base64.encodeBase64String(enryptedSymKey))
    for (i <- 0 until friendList.size) {
      var pkKey = userPublicKeyPair(friendList(i))
      var enryptedSymmKey = encryptKeyUsingRSA(aesKey, pkKey).asInstanceOf[(Array[Byte])]
      encryptedKeyUserMap += (friendList(i) -> Base64.encodeBase64String(enryptedSymmKey))
    }
    return (Base64.encodeBase64String(enrypted), encryptedKeyUserMap, initVector);
  }

  //ENCRYPTING THE KEY  
  def encryptKeyUsingRSA(symmetriskNyckel: Array[Byte], publicKey: PublicKey): (Array[Byte]) = {
    var pipher: Cipher = Cipher.getInstance("RSA");
    pipher.init(Cipher.ENCRYPT_MODE, publicKey);
    var krypteradAESNyckel = pipher.doFinal(symmetriskNyckel);
    return krypteradAESNyckel
  }
  
  //DECRYPTING THE KEY
  def decryptUsingRSA(krypteradAESNyckel: Array[Byte]): Array[Byte] = {
    var pipher: Cipher = Cipher.getInstance("RSA");
    pipher.init(Cipher.DECRYPT_MODE, privatnyckel);
    var decrypt = pipher.doFinal(krypteradAESNyckel);
    decrypt
  }
  
  // DECRYPTING THE DATA
  def decryptData(initializationVector: String, key: String, encryptedMessage: String): String = {
    var initVector = Base64.decodeBase64(initializationVector)
    var encryptedKey = Base64.decodeBase64(key)
    var aesKey = decryptUsingRSA(encryptedKey)
    var iv: IvParameterSpec = new IvParameterSpec(initVector);
    var skeySpec: SecretKeySpec = new SecretKeySpec(aesKey, "AES");
    var cipher: Cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING");
    cipher.init(Cipher.DECRYPT_MODE, skeySpec, iv);
    var original = cipher.doFinal(Base64.decodeBase64(encryptedMessage));
    new String(original)
  }



  //DISPLAYING THE DATA

  def displayPost(stringToDisplay: String, oldPost: Seq[Posts], newpost: Seq[Posts]) {
    println()
    println(stringToDisplay)
    println()
    for (i <- 0 until newpost.length) {
      println("Post id: " + newpost(i).id.getOrElse(""))
      println("Message After Encryption \t Before Encryption")
      println(newpost(i).message.getOrElse("") + "\t\t\t" + oldPost(i).message.getOrElse(""))
      println()
    }
  }
  
 //SAVING THE PHOTO
  def savePhotoToDisk(photo: Photo, folderName: String, subFolder: String) {
    var inputStream = photo.photo
    var imageDataBytes = inputStream.substring(inputStream.indexOf(",") + 1);
    var stream = Base64.decodeBase64(imageDataBytes.getBytes());
    var d = new File(folderName)
    if (!d.exists()) {
      d.mkdir()
    }
    var k = new File(folderName + "/" + subFolder)
    if (!k.exists()) {
      k.mkdir()
    }
    var path = folderName + "/" + subFolder + "/" + photo.id + ".png"
    var out = new BufferedOutputStream(new FileOutputStream(path));
    out.write(stream);
    out.close()
  }

  def saveToMyDisk(photo: Seq[Photo]) {
    for (i <- 0 until photo.length) {
      savePhotoToDisk(photo(i), userid, "myPhotos")
    }
  }

  def convertToPublicKey(publicKeyString: String): PublicKey = {
    var publicBytes = Base64.decodeBase64(publicKeyString);
    var keySpec = new X509EncodedKeySpec(publicBytes);
    var keyFactory = KeyFactory.getInstance("RSA");
    keyFactory.generatePublic(keySpec);
  }

  def createListOfSharedUsers(numberOfUsers: Int): Seq[String] = {
    implicit val timeout = Timeout(Duration(1000, TimeUnit.SECONDS))
    val future = GetUserDetails(userid)
    var user: User = Await.result(future, timeout.duration).asInstanceOf[User]
    var friendList = Seq.empty[String]
    for (i <- 0 until numberOfUsers) {
      var friendid = (user.friendlist(util.Random.nextInt(user.friendlist.size)))
      friendList = friendList :+ friendid
    }
    friendList
  }

  def decryptPost(post: Seq[Posts]): Seq[Posts] = {
    var postSeq: Seq[Posts] = Seq.empty[Posts]
    for (i <- 0 until post.length) {
      var encryptedMessage = post(i).message.getOrElse("default")
      var original = decryptData(post(i).iv, post(i).key(userid), encryptedMessage)
      postSeq = postSeq :+ post(i).copy(message = Some(original))
    }
    postSeq
  }

  def decryptPhotos(photo: Seq[Photo]): Seq[Photo] = {
    var photoSeq: Seq[Photo] = Seq.empty[Photo]
    for (i <- 0 until photo.length) {
      var encryptedMessage = photo(i).photo
      var original = decryptData(photo(i).iv, photo(i).key(userid), encryptedMessage)
      photoSeq = photoSeq :+ photo(i).copy(photo = original)
    }
    photoSeq
  }

  def decryptFriendsPhoto(photo: Photo, friendid: String) {
    var pht = photo
    var original = decryptData(pht.iv, pht.key(userid), pht.photo)
    var newPhoto = pht.copy(photo = original)
    var folderName = userid + "s copy of " + friendid + "s photo"
    savePhotoToDisk(newPhoto, userid, folderName)
  }

  //--------------------------------------------------------------------------------------------------------//
  //-----------------------------------------------------------------------------------------------------//

  //                                   SIMULATION STARTS HERE                                            //

  //-----------------------------------------------------------------------------------------------------//

  def runProfile(userProfile: Int) {

    var SENDF = MAX; var ACCF = MAX
    var SENDF_I = MAX; var ACCF_I = MAX
    var CRPG = MAX; var CRPG_I = MAX
    var GETMYT = MAX; var GETMYTPG = MAX; var GETMYFT = MAX; var GETMYFTPG = MAX
    var GETMYT_I = MAX; var GETMYTPG_I = MAX; var GETMYFT_I = MAX; var GETMYFTPG_I = MAX
    var POSTMYFRW = MAX; var POSTMYFRPG = MAX; var POSTMYW = MAX; var POSTMYPG = MAX
    var POSTMYFRW_I = MAX; var POSTMYFRPG_I = MAX; var POSTMYW_I = MAX; var POSTMYPG_I = MAX
    var LIKEMYPG = MAX; var LIKEMYFRPG = MAX; var LIKEMYFRP = MAX; var LIKEMYFRAL = MAX; var LIKEMYAL = MAX; var LIKEMYPO = MAX
    var LIKEMYPG_I = MAX; var LIKEMYFRPG_I = MAX; var LIKEMYFRP_I = MAX; var LIKEMYFRAL_I = MAX; var LIKEMYAL_I = MAX; var LIKEMYPO_I = MAX
    var UPPIC = MAX; var UPAL = MAX
    var UPPIC_I = MAX; var UPAL_I = MAX
    var COMFRPIC_I = MAX; var COMFRP_I = MAX; var COMFAL_I = MAX; var COMMYAL_I = MAX; var COMMYPIC_I = MAX; var COMMYP = MAX
    var COMFRPIC = MAX; var COMFRP = MAX; var COMFAL = MAX; var COMMYAL = MAX; var COMMYPIC = MAX; var num = 2; var num2 = 2; var COMMYP_I = MAX;

    (userProfile) match {

      case 0 =>
        //SENDF = 75; SENDF_I = 825
        //ACCF = 82; ACCF_I = 930
        POSTMYW = 100; POSTMYW_I = 1500;
        COMFRPIC_I = 4000; COMFRP_I = 800; COMMYP = 100
        COMFRPIC = 700; COMFRP = 200; COMMYP_I = 500;
        LIKEMYFRPG = 100; LIKEMYFRP = 100; LIKEMYFRAL = 100; LIKEMYPO = 100
        LIKEMYFRPG_I = 600; LIKEMYFRP_I = 400; LIKEMYFRAL_I = 1000; LIKEMYPO_I = 1900
        GETMYT = 10; GETMYFT = 25; GETMYFTPG = 300
        GETMYT_I = 800; GETMYFT_I = 400; GETMYFTPG_I = 1400

      case 1 =>
        //SENDF = 55; SENDF_I = 500
        //ACCF = 65; ACCF_I = 600
        POSTMYW = 165; POSTMYW_I = 500;
        POSTMYFRW = 130; POSTMYFRW_I = 300;
        COMFRPIC_I = 3000; COMFRP_I = 500; COMMYP = 700
        COMFRPIC = 100; COMFRP = 200; COMMYP_I = 500;
        LIKEMYFRPG = 100; LIKEMYFRP = 100; LIKEMYFRAL = 100; LIKEMYPO = 100
        LIKEMYFRPG_I = 600; LIKEMYFRP_I = 200; LIKEMYFRAL_I = 1000; LIKEMYPO_I = 1700
        GETMYT = 20; GETMYFT = 35; GETMYFTPG = 300
        GETMYT_I = 800; GETMYFT_I = 400; GETMYFTPG_I = 1000

      case 2 =>
        //SENDF = 35; SENDF_I = 450
        //ACCF = 40; ACCF_I = 500
        CRPG = 50; CRPG_I = 400
        POSTMYW = 100; POSTMYW_I = 500; POSTMYPG = 20; POSTMYPG_I = 1000
        POSTMYFRPG = 150; POSTMYFRPG_I = 200
        POSTMYFRW = 20; POSTMYFRW_I = 200;
        COMFRPIC_I = 1000; COMFRP_I = 500; COMMYP = 700
        COMFRPIC = 100; COMFRP = 200; COMMYP_I = 500;
        LIKEMYFRPG = 100; LIKEMYFRP = 100; LIKEMYFRAL = 100; LIKEMYPO = 100
        LIKEMYPG_I = 600; LIKEMYFRPG_I = 500; LIKEMYFRP_I = 200; LIKEMYFRAL_I = 1000; LIKEMYAL_I = 3000; LIKEMYPO_I = 1000
        GETMYT = 30; GETMYTPG = 300; GETMYFT = 45; GETMYFTPG = 400
        GETMYT_I = 100; GETMYTPG_I = 600; GETMYFT_I = 700; GETMYFTPG_I = 1200

      case 3 =>
        SENDF = 15; SENDF_I = 350
        ACCF = 19; ACCF_I = 300
        CRPG = 60; CRPG_I = 210
        POSTMYW = 90; POSTMYW_I = 150; POSTMYPG = 20; POSTMYPG_I = 90
        POSTMYFRW = 70; POSTMYFRW_I = 150;
        COMFRPIC_I = 1000; COMFRP_I = 500; COMMYP = 700
        COMFRPIC = 100; COMFRP = 200; COMMYP_I = 500;
        LIKEMYPG = 100; LIKEMYFRPG = 100; LIKEMYFRP = 100; LIKEMYFRAL = 100; LIKEMYAL = 100; LIKEMYPO = 100
        LIKEMYPG_I = 600; LIKEMYFRPG_I = 400; LIKEMYFRP_I = 150; LIKEMYFRAL_I = 1000; LIKEMYAL_I = 3000; LIKEMYPO_I = 1000
        GETMYT = 200; GETMYTPG = MAX; GETMYFT = MAX; GETMYFTPG = MAX
        GETMYT_I = MAX; GETMYTPG_I = MAX; GETMYFT_I = MAX; GETMYFTPG_I = MAX
        GETMYT = 40; GETMYTPG = 200; GETMYFT = 65; GETMYFTPG = 300
        GETMYT_I = 800; GETMYTPG_I = 500; GETMYFT_I = 400; GETMYFTPG_I = 1000

      case 4 =>
        SENDF = 0; SENDF_I = 250
        ACCF = 1; ACCF_I = 150
        CRPG = 40; CRPG_I = 500
        POSTMYW = 65; POSTMYW_I = 100; POSTMYPG = 10; POSTMYPG_I = 50
        POSTMYFRW = 72; POSTMYFRW_I = 100;
        COMFRPIC_I = 1000; COMFRP_I = 1300; COMFAL_I = 1400; COMMYAL_I = 1600; COMMYPIC_I = 1200; COMMYP = 1000
        COMFRPIC = 100; COMFRP = 300; COMFAL = 200; COMMYAL = 150; COMMYPIC = 100; COMMYP_I = 1500;
        UPPIC = 50; UPAL = 30
        UPPIC_I = 4000; UPAL_I = 4000
        LIKEMYPG = 5; LIKEMYFRPG = 5; LIKEMYFRP = 5; LIKEMYFRAL = 50; LIKEMYAL = 50; LIKEMYPO = 5
        LIKEMYPG_I = 600; LIKEMYFRPG_I = 400; LIKEMYFRP_I = 200; LIKEMYFRAL_I = 1000; LIKEMYAL_I = 3000; LIKEMYPO_I = 1000
        GETMYT = 50; GETMYTPG = 200; GETMYFT = 75; GETMYFTPG = 300
        GETMYT_I = 800; GETMYTPG_I = 500; GETMYFT_I = 400; GETMYFTPG_I = 1000

      case _ =>

        SENDF = 0; SENDF_I = 30
        ACCF = 1; ACCF_I = 50
        COMFRPIC_I = 6000; COMFRP_I = 1000; COMMYP = 2000
        COMFRPIC = 500; COMFRP = 200; COMMYP_I = 500;
        LIKEMYFRPG = 100; LIKEMYFRP = 100; LIKEMYFRAL = 100;
        LIKEMYFRPG_I = 800; LIKEMYFRP_I = 400; LIKEMYFRAL_I = 1700;
        GETMYT = 55; GETMYFT = 40; GETMYFTPG = 300
        GETMYT_I = 1000; GETMYFT_I = 900; GETMYFTPG_I = 1600

    }

    //CREATE PAGE
    //    context.system.scheduler.schedule(Duration(12, TimeUnit.SECONDS), Duration(50, TimeUnit.SECONDS))(CreatePage())

    //FRIENDREQUESTS

    context.system.scheduler.schedule(Duration(0, TimeUnit.SECONDS), Duration(40, TimeUnit.SECONDS))(MakeFriendRequest(num: Int))
    context.system.scheduler.schedule(Duration(5, TimeUnit.SECONDS), Duration(50, TimeUnit.SECONDS))(ViewFriendRequest())

    //POSTS 
    context.system.scheduler.schedule(Duration(10, TimeUnit.SECONDS), Duration(200, TimeUnit.SECONDS))(PostOnFriendsWall())
    //  context.system.scheduler.schedule(Duration(15, TimeUnit.SECONDS), Duration(15, TimeUnit.SECONDS))(PublishPostOnFriendsPage())
    context.system.scheduler.schedule(Duration(10, TimeUnit.SECONDS), Duration(200, TimeUnit.SECONDS))(PostOnMyWall())
    //   context.system.scheduler.schedule(Duration(15, TimeUnit.SECONDS), Duration(15, TimeUnit.SECONDS))(PublishPostOnMyPage())

    //TIMELINE

    context.system.scheduler.schedule(Duration(GETMYT, TimeUnit.SECONDS), Duration(GETMYT_I, TimeUnit.SECONDS))(GetMyTimeLine())
    //   context.system.scheduler.schedule(Duration(25, TimeUnit.SECONDS), Duration(50, TimeUnit.SECONDS))(GetMyPageTimeLine())
    context.system.scheduler.schedule(Duration(GETMYFT, TimeUnit.SECONDS), Duration(GETMYFT_I, TimeUnit.SECONDS))(SeeFriendsTimeLine())
    //    context.system.scheduler.schedule(Duration(35, TimeUnit.SECONDS), Duration(50, TimeUnit.SECONDS))(SeeFriendsPageTimeLine())

    //LIKES

    //context.system.scheduler.schedule(Duration(LIKEMYPG, TimeUnit.SECONDS), Duration(LIKEMYPG_I, TimeUnit.SECONDS))(likeMyPage())
    // context.system.scheduler.schedule(Duration(LIKEMYFRPG, TimeUnit.SECONDS), Duration(LIKEMYFRPG_I, TimeUnit.SECONDS))(likeMyFriendsPage())
    //context.system.scheduler.schedule(Duration(LIKEMYFRP, TimeUnit.SECONDS), Duration(LIKEMYFRP_I, TimeUnit.SECONDS))(likeMyFriendsPosts())
    //context.system.scheduler.schedule(Duration(LIKEMYFRAL, TimeUnit.SECONDS), Duration(LIKEMYFRAL_I, TimeUnit.SECONDS))(likeMyFriendsAlbum())
    //context.system.scheduler.schedule(Duration(LIKEMYAL, TimeUnit.SECONDS), Duration(LIKEMYAL_I, TimeUnit.SECONDS))(likeMyAlbum())
    //context.system.scheduler.schedule(Duration(LIKEMYPO, TimeUnit.SECONDS), Duration(LIKEMYPO_I, TimeUnit.SECONDS))(likeMyOwnPost())

    //PHOTO STUFF

    context.system.scheduler.schedule(Duration(30, TimeUnit.SECONDS), Duration(30, TimeUnit.SECONDS))(uploadPicInMyAlbum())
    context.system.scheduler.schedule(Duration(20, TimeUnit.SECONDS), Duration(25, TimeUnit.SECONDS))(createMyAlbum())
    context.system.scheduler.schedule(Duration(40, TimeUnit.SECONDS), Duration(40, TimeUnit.SECONDS))(GetAllMyPhotos())
    context.system.scheduler.schedule(Duration(30, TimeUnit.SECONDS), Duration(30, TimeUnit.SECONDS))(getMyFriendsPhoto())

    //
    //    //COMMENTS

    //  context.system.scheduler.schedule(Duration(COMFRPIC, TimeUnit.SECONDS), Duration(COMFRPIC_I, TimeUnit.SECONDS))(commentOnMyFriendsPhoto)
    // context.system.scheduler.schedule(Duration(COMFRP, TimeUnit.SECONDS), Duration(COMFRP_I, TimeUnit.SECONDS))(commentOnMyFriendsPosts())
    // context.system.scheduler.schedule(Duration(COMFAL, TimeUnit.SECONDS), Duration(COMFAL_I, TimeUnit.SECONDS))(commentOnMyFriendsAlbum())
    // context.system.scheduler.schedule(Duration(COMMYAL, TimeUnit.SECONDS), Duration(COMMYAL_I, TimeUnit.SECONDS))(commentOnMyAlbum())
    // context.system.scheduler.schedule(Duration(COMMYP, TimeUnit.SECONDS), Duration(COMMYP_I, TimeUnit.SECONDS))(commentOnMyPosts())
    //context.system.scheduler.schedule(Duration(COMMYPIC, TimeUnit.SECONDS), Duration(COMMYPIC_I, TimeUnit.SECONDS))(commentOnMyOwnPhoto())

  }

}