package main.scala

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
import scala.concurrent.{ Await, ExecutionContext, Future }
import scala.concurrent.duration._
import akka.actor._
import scala.concurrent.ExecutionContext
import ExecutionContext.Implicits.global
import ClientProtocol._
import java.security.PublicKey;

case class storePublicKey(id: String, pkKey: PublicKey)
case class sendUsersPublicKey()
case class createClientActorsAndSimulate()
case class sendClientRef(id: Integer)

object Client extends App {

  implicit val system = ActorSystem("Facebook-spray-client")

  import system.dispatcher

  var numberOfUsers: Int = 5000 //default value

  println("HOW MANY CLIENTS DO YOU WANT TO SIMULATE?")

  numberOfUsers = readInt
  val clientCreater = system.actorOf(Props(new MasterActor(numberOfUsers)), name = "master")
  clientCreater ! createClientActorsAndSimulate()
}

class MasterActor(numUsers: Int) extends Actor {

  import context._
  var numberOfUsers = numUsers
  var userPublicKeyPair = Map[String, PublicKey]()
  println("CREATING ACTORS ON CLIENT...")

  val Clients = (for { i <- 1 to numberOfUsers } yield {
    var NodeRef = context.actorOf(Props(new ClientMethodDefinitions(i, self)), name = String.valueOf((i)))
    (i, NodeRef)
  }).toMap

  def receive = {
    case createClientActorsAndSimulate() =>
      simulateClientCreation()
      Thread.sleep(1000)

      println("STARTING SIMULATION...")
      var startTime = System.currentTimeMillis;
      for (i <- 1 to numberOfUsers) {
        Clients(i) ! Simulate()
      }
      sys addShutdownHook {
        var endTime = System.currentTimeMillis
        var timeRunning = endTime - startTime
        println("The simulation has been running for " + timeRunning + " ms")
      }

    case sendClientRef(id) =>
      sender ! (Clients(id))

    case sendUsersPublicKey() =>
      sender ! userPublicKeyPair

    case storePublicKey(uid, publicKey) =>
      userPublicKeyPair += (uid -> publicKey)

  }

  def simulateClientCreation() = {
    println("ASSIGNING BEHAVIOURS TO THE USER AND CREATING PROFILE ON THE SERVER...")
    var ONLY_ME = 1; var FRIENDS_ONLY = 2; var PUBLIC = 3
    var gender = Array("Male", "Female", "Unknown")
    var ageRange = Array("40", "25-40", "12-24")
    var characteristic = Array("Shy", "Moderate", "Social")
    var postOnwallPermission = Array(ONLY_ME, FRIENDS_ONLY, PUBLIC)
    for (i <- 1 to numberOfUsers) {

      //RANDOM GENDER,AGE,CHARACTERISTICS
      var genderSelector = util.Random.nextInt(gender.size)
      var ageSelector = util.Random.nextInt(ageRange.size)
      var charactersiticSelector = util.Random.nextInt(characteristic.size)
      var ageValue = ageRange(ageSelector)
      var permLevel = util.Random.nextInt(postOnwallPermission.size)
      //ASSIGNING USER DETAILS
      val userDetails: User = User(None, Some("UserName_" + i.toString), Some("UserFullName_" + i.toString),
        Some("UserFirstName_" + i.toString), Some("UserMiddleName_" + i.toString),
        Some("UserLastName_" + i.toString), Some("mailId@" + i.toString),
        Some("Somelink_" + i.toString), Some(gender(genderSelector)),
        Some(ageRange(ageSelector)), Seq.empty[String], Seq.empty[String],
        Seq.empty[String], Seq.empty[String], Seq.empty[String], i, postOnwallPermission(2))

      var profileScore = (charactersiticSelector + ageSelector)

      //CALLING THE SERVER
      Clients(i) ! CreateNewUser(userDetails, i)
    }
  }
}