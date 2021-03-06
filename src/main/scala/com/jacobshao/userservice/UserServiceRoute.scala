package com.jacobshao.userservice

import java.sql.SQLException

import cats.syntax.apply._
import com.typesafe.scalalogging.StrictLogging
import io.circe.generic.auto._
import io.circe.syntax._
import monix.eval.Task
import org.http4s.circe.CirceEntityDecoder._
import org.http4s.circe.CirceEntityEncoder._
import org.http4s.dsl.Http4sDsl
import org.http4s.{HttpRoutes, MessageBodyFailure}

object UserServiceRoute extends StrictLogging {

  private val dsl = new Http4sDsl[Task] {}

  import dsl._

  def apply(implicit userService: UserService): HttpRoutes[Task] =
    HttpRoutes.of[Task] {
      case req@POST -> Root / "users" =>
        (for {
          decodedReq <- req.as[UserCreationRequest]
          _ <- userService.create(decodedReq)
          response <- NoContent()
        } yield response)
          .onErrorHandleWith {
            case e: MessageBodyFailure => BadRequest(ResponseError(e.message).asJson)
            case e: UserCreationRequestInvalidFailure => BadRequest(ResponseError(e.message).asJson)
            case e: UserDataNotAvailableFailure.type => NotFound(ResponseError(e.message).asJson)
            case e: UserAlreadyExistsFailure.type => Conflict(ResponseError(e.message).asJson)
            case e: SQLException => Task(logger.warn(s"SQL exception encountered. Details: ${e.getMessage}")) *> InternalServerError()
            case e => Task(logger.warn(s"Exception encountered. Details: ${e.getMessage}")) *> ServiceUnavailable()
          }

      case GET -> Root / "users" / email =>
        (for {
          emailAddress <- Task.now(EmailAddress(email))
          user <- userService.get(emailAddress)
          response <- Ok(user)
        } yield response)
          .onErrorHandleWith {
            case e: InvalidEmailFormatFailure.type => BadRequest(ResponseError(e.message).asJson)
            case e: UserNotExistsFailure.type => NotFound(ResponseError(e.message).asJson)
            case e: SQLException => Task(logger.warn(s"SQL exception encountered. Details: ${e.getMessage}")) *> InternalServerError()
            case e => Task(logger.warn(s"Exception encountered. Details: ${e.getMessage}")) *> ServiceUnavailable()
          }

      case DELETE -> Root / "users" / email =>
        (for {
          emailAddress <- Task.now(EmailAddress(email))
          _ <- userService.delete(emailAddress)
          response <- NoContent()
        } yield response)
          .onErrorHandleWith {
            case e: InvalidEmailFormatFailure.type => BadRequest(ResponseError(e.message).asJson)
            case e: SQLException => Task(logger.warn(s"SQL exception encountered. Details: ${e.getMessage}")) *> InternalServerError()
            case e => Task(logger.warn(s"Exception encountered. Details: ${e.getMessage}")) *> ServiceUnavailable()
          }
    }
}
