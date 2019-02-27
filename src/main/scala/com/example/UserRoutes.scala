package com.example

import java.io.File

import akka.actor.{ ActorRef, ActorSystem }
import akka.event.Logging

import scala.concurrent.duration._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.Credentials
import akka.http.scaladsl.server.directives.Credentials.Provided
import akka.http.scaladsl.server.directives.MethodDirectives.delete
import akka.http.scaladsl.server.directives.MethodDirectives.get
import akka.http.scaladsl.server.directives.MethodDirectives.post
import akka.http.scaladsl.server.directives.RouteDirectives.complete
import akka.http.scaladsl.server.directives.PathDirectives.path

import scala.concurrent.Future
import com.example.UserRegistryActor._
import akka.pattern.ask
import akka.stream.scaladsl.{ Framing, Sink }
import akka.util.{ ByteString, Timeout }
import com.typesafe.config.ConfigFactory

import collection.JavaConversions._

//#user-routes-class
trait UserRoutes extends JsonSupport {
  //#user-routes-class

  // we leave these abstract, since they will be provided by the App
  implicit def system: ActorSystem

  lazy val log = Logging(system, classOf[UserRoutes])

  // other dependencies that UserRoutes use
  def userRegistryActor: ActorRef

  // Required by the `ask` (?) method below
  implicit lazy val timeout = Timeout(5.seconds) // usually we'd obtain the timeout from the system's configuration

  val tokens: List[String] = ConfigFactory.load().getStringList("tokens").toList

  def check(credentials: Credentials): Option[String] = credentials match {
    case p @ Credentials.Provided(token) if tokens.exists(t => p.verify(t)) => Some(token)
    case _ => None
  }

  //#all-routes
  //#users-get-post
  //#users-get-delete
  lazy val userRoutes: Route = Route.seal {
    concat(
      pathPrefix("users") {
        //authenticateBasic(realm = "secure site", check) { userName =>
        authenticateOAuth2(realm = "secure site", check) { token =>
          concat(
            //#users-get-delete
            pathEnd {
              concat(
                get {
                  val users: Future[Users] =
                    (userRegistryActor ? GetUsers).mapTo[Users]
                  log.info(token + " registered")
                  complete(users)
                },
                post {
                  entity(as[User]) { user =>
                    val userCreated: Future[ActionPerformed] =
                      (userRegistryActor ? CreateUser(user)).mapTo[ActionPerformed]
                    onSuccess(userCreated) { performed =>
                      log.info("Created user [{}]: {}", user.name, performed.description)
                      complete((StatusCodes.Created, performed))
                    }
                  }
                })
            },
            //#users-get-post
            //#users-get-delete
            path(Segment) { name =>
              concat(
                get {
                  //#retrieve-user-info
                  val maybeUser: Future[Option[User]] =
                    (userRegistryActor ? GetUser(name)).mapTo[Option[User]]
                  rejectEmptyResponse {
                    complete(maybeUser)
                  }
                  //#retrieve-user-info
                },
                delete {
                  //#users-delete-logic
                  val userDeleted: Future[ActionPerformed] =
                    (userRegistryActor ? DeleteUser(name)).mapTo[ActionPerformed]
                  onSuccess(userDeleted) { performed =>
                    log.info("Deleted user [{}]: {}", name, performed.description)
                    complete((StatusCodes.OK, performed))
                  }
                  //#users-delete-logic
                })
            })
        }
        //#users-get-delete
      },
      path("upload") {
        println("upload")
        post {
          println("post")
          extractRequestContext { ctx =>
            implicit val materializer = ctx.materializer

            println("csv")
            fileUpload("csv") {
              case (metadata, byteSource) =>
                println("blabla")

                val sumF: Future[Int] =
                  // sum the numbers as they arrive so that we can
                  // accept any size of file
                  byteSource.via(Framing.delimiter(ByteString("\n"), 1024))
                    .mapConcat(_.utf8String.split(",").toVector)
                    .map(_.toInt)
                    .runFold(0) { (acc, n) => acc + n }

                onSuccess(sumF) { sum => complete(s"Sum: $sum") }
            }
          }
        }
      })
    //#all-routes
  }
}
