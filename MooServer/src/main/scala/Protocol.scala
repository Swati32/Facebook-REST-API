package main.scala

import spray.json._
import akka.actor._
import akka.actor.ActorRef
import akka.serialization._

object MooProtocol extends DefaultJsonProtocol {

  case class User(
    var id: Option[String],
    username: Option[String],
    name: Option[String],
    first_name: Option[String],
    middle_name: Option[String],
    last_name: Option[String],
    email: Option[String],
    link: Option[String],
    gender: Option[String],
    age: Option[String],
    userPages: Seq[String],
    postlist: Seq[String],
    albumlist: Seq[String],
    friendlist: Seq[String],
    friendrequest: Seq[String],
    clientId: Int,
    postOnMyWallPermission: Int)

  case class UserInfo(
    id: String,
    publicKey: String)

  case class Mapping(
    key: String,
    value: String)

  case class Page(
    var id: Option[String],
    name: String,
    owner: String,
    about: String,
    link: String,
    var likes: Int = 0,
    posts: Seq[String],
    likelist: Seq[String],
    postlist: Seq[String],
    albumlist: Seq[String],
    postOnMyWallPermission: Int)

  case class Posts(
    var id: Option[String],
    story: String,
    created_time: String,
    from: String,
    message: Option[String] = None,
    updatedTimeStamp: String,
    var likes: Int = 0,
    likelist: Seq[String],
    commentList: Seq[String],
    iv: String,
    key: Map[String, String],
    var clientId: Int,
    viewPermission: Int)

  case class Comment(
    var id: Option[String],
    from: Option[String],
    message: String,
    can_remove: Option[String],
    created_time: Option[String],
    like_count: Option[String],
    user_likes: Seq[String])

  case class Album(
    var id: Option[String],
    name: String,
    link: String,
    photolist: Seq[String],
    commentlist: Seq[String],
    var likes: Int = 0,
    likelist: Seq[String])

  case class Photo(
    var id: Option[String],
    photo: String,
    var link: String,
    name: String,
    commentlist: Seq[String],
    iv: String,
    key:  Map[String, String],
    var clientId: Int,
    viewPermission: Int)

  case class Like(var id: Option[String], from: String)

  case class AllPages(
    pages: Seq[Page])

  case class AllPhotos(
    photos: Seq[Photo])

  case class AllComments(
    comments: Seq[Comment])

  case class AllPosts(
    post: Seq[Posts])

  case class AllAlbums(
    album: Seq[Album])

  case class MessageFormat(
    message: String)

  case class Ids(
    id: Seq[String])

  implicit val userFormat = jsonFormat17(User)
  implicit val userInfoFormat = jsonFormat2(UserInfo)
  implicit val pageFormat = jsonFormat11(Page)
  implicit val idsFormat = jsonFormat1(Ids)
  implicit val albums = jsonFormat7(Album)
  implicit val allAlbumFormat = jsonFormat1(AllAlbums)
  implicit val commentFormat = jsonFormat7(Comment)
  implicit val postsFormat = jsonFormat13(Posts)
  implicit val likeFormat = jsonFormat2(Like)
  implicit val allPostsFormat = jsonFormat1(AllPosts)
  implicit val allPagesFormat = jsonFormat1(AllPages)
  implicit val photoFormat1 = jsonFormat9(Photo)
  implicit val allPhotoFormat = jsonFormat1(AllPhotos)
  implicit val allcommentsFormat = jsonFormat1(AllComments)
  implicit val messageFormat = jsonFormat1(MessageFormat)
  implicit val mappingFormat = jsonFormat2(Mapping)

}