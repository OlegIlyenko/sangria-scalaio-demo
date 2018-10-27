package common

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.MediaTypes._
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import akka.stream.ActorMaterializer
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._
import io.circe._
import io.circe.optics.JsonPath._
import io.circe.parser._
import io.circe.generic.auto._

import scala.util.{Failure, Success}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal
import sangria.ast.Document
import sangria.parser._
import sangria.marshalling.circe._
import common.GraphQLRequestUnmarshaller.{explicitlyAccepts, _}
import sangria.execution._

object GraphQLRoutes {
  def route(executeFn: (Document, Option[String], Json, Option[AuthToken], Boolean) ⇒ Future[Json])(implicit ex: ExecutionContext): Route =
    (optionalHeaderValueByName("X-Apollo-Tracing") & optionalJwtToken("secret")) { (tracing, authToken) ⇒
      path("graphql") {
        get {
          explicitlyAccepts(`text/html`) {
            getFromResource("assets/playground.html")
          } ~
          parameters('query, 'operationName.?, 'variables.?) { (query, operationName, variables) ⇒
            QueryParser.parse(query) match {
              case Success(ast) ⇒
                variables.map(parse) match {
                  case Some(Left(error)) ⇒ complete(BadRequest, formatError(error))
                  case Some(Right(json)) ⇒ executeGraphQL(executeFn, ast, operationName, json, tracing.isDefined, authToken)
                  case None ⇒ executeGraphQL(executeFn, ast, operationName, Json.obj(), tracing.isDefined, authToken)
                }
              case Failure(error) ⇒ complete(BadRequest, formatError(error))
            }
          }
        } ~
        post {
          parameters('query.?, 'operationName.?, 'variables.?) { (queryParam, operationNameParam, variablesParam) ⇒
            entity(as[Json]) { body ⇒
              val query = queryParam orElse root.query.string.getOption(body)
              val operationName = operationNameParam orElse root.operationName.string.getOption(body)
              val variablesStr = variablesParam orElse root.variables.string.getOption(body)

              query.map(QueryParser.parse(_)) match {
                case Some(Success(ast)) ⇒
                  variablesStr.map(parse) match {
                    case Some(Left(error)) ⇒ complete(BadRequest, formatError(error))
                    case Some(Right(json)) ⇒ executeGraphQL(executeFn, ast, operationName, json, tracing.isDefined, authToken)
                    case None ⇒ executeGraphQL(executeFn, ast, operationName, root.variables.json.getOption(body) getOrElse Json.obj(), tracing.isDefined, authToken)
                  }
                case Some(Failure(error)) ⇒ complete(BadRequest, formatError(error))
                case None ⇒ complete(BadRequest, formatError("No query to execute"))
              }
            } ~
            entity(as[Document]) { document ⇒
              variablesParam.map(parse) match {
                case Some(Left(error)) ⇒ complete(BadRequest, formatError(error))
                case Some(Right(json)) ⇒ executeGraphQL(executeFn, document, operationNameParam, json, tracing.isDefined, authToken)
                case None ⇒ executeGraphQL(executeFn, document, operationNameParam, Json.obj(), tracing.isDefined, authToken)
              }
            }
          }
        }
      }
    } ~
    (get & pathPrefix("assets")) {
      getFromResourceDirectory("assets")
    } ~
    (get & pathEndOrSingleSlash) {
      redirect("/graphql", PermanentRedirect)
    }

  def simpleServer(executeFn: (Document, Option[String], Json, Option[AuthToken], Boolean) ⇒ Future[Json]) = {
    implicit val system = ActorSystem("sangria-server")
    implicit val materializer = ActorMaterializer()

    import system.dispatcher

    val route = GraphQLRoutes.route(executeFn)

    Http().bindAndHandle(route, "0.0.0.0", 8080).foreach(_ ⇒
      println("Server started on port 8080"))
  }

  def executeGraphQL(
    executeFn: (Document, Option[String], Json, Option[AuthToken], Boolean) ⇒ Future[Json],
    query: Document,
    operationName: Option[String],
    variables: Json,
    tracing: Boolean,
    authToken: Option[Json]
  )(implicit ex: ExecutionContext) =
    complete(
      executeFn(query, operationName, if (variables.isNull) Json.obj() else variables, authToken.flatMap(_.as[AuthToken].toOption), tracing)
        .map(OK → _)
        .recover {
          case QueryReducingError(error: MaxQueryDepthReachedError, _) ⇒ BadRequest → formatError(error.getMessage)
          case QueryReducingError(error: IllegalStateException, _) ⇒ BadRequest → formatError(error.getMessage)
          case error: QueryAnalysisError ⇒ BadRequest → error.resolveError
          case error: ErrorWithResolver ⇒ InternalServerError → error.resolveError
        })

  def formatError(error: Throwable): Json = error match {
    case syntaxError: SyntaxError ⇒
      Json.obj("errors" → Json.arr(
      Json.obj(
        "message" → Json.fromString(syntaxError.getMessage),
        "locations" → Json.arr(Json.obj(
          "line" → Json.fromBigInt(syntaxError.originalError.position.line),
          "column" → Json.fromBigInt(syntaxError.originalError.position.column))))))
    case NonFatal(e) ⇒
      formatError(e.getMessage)
    case e ⇒
      throw e
  }

  def formatError(message: String): Json =
    Json.obj("errors" → Json.arr(Json.obj("message" → Json.fromString(message))))
}
