import common.AuthToken
import finalServer.{AppContext, AuthException, AuthMiddleware}
import org.scalatest.{Matchers, WordSpec}
import sangria.ast.Document
import sangria.macros._
import sangria.execution.{ExceptionHandler, Executor, HandledException}
import sangria.marshalling.circe._
import io.circe._
import io.circe.parser._

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import finalServer.SchemaDefinition.schema
import model.InMemoryDbRepo

class SchemaSpec extends WordSpec with Matchers {
  val repo = InMemoryDbRepo.createDatabase

  "GraphQL Schema" should {
    "List books" in {
      val query =
        graphql"""
          query bookList {
            books(limit: 2) {
              id
              title
            }
          }
        """

      executeQuery(query) should be (parse(
        """
          {
            "data": {
              "books": [
                {
                  "id": "OL30310W",
                  "title": "A theory of justice"
                },
                {
                  "id": "OL80609W",
                  "title": "Du contrat social"
                }
              ]
            }
          }
       """).right.get)
    }

    "Provide book relation data on an author type" in {
      val query =
        graphql"""
          query authorBooks($$authorId: ID!) {
            author(id: $$authorId) {
              name

              books {
                title
              }
            }
          }
        """

      executeQuery(query, vars = Json.obj("authorId" → Json.fromString("OL27530A"))) should be (parse(
        """
          {
            "data": {
              "author": {
                "name": "John Rawls",
                "books": [
                  {"title": "A theory of justice"},
                  {"title": "Political liberalism"}
                ]
              }
            }
          }
        """).right.get)
    }

    "Show books for an authorized user" in {
      val query =
        graphql"""
          query  {
            me {
              name

              favouriteBooks {
                title
              }
            }
          }
        """

      executeQuery(query, token = Some(AuthToken("John Doe", Seq("OL30310W", "OL99843W")))) should be (parse(
        """
          {
            "data": {
              "me": {
                "name": "John Doe",
                "favouriteBooks": [
                  {
                    "title": "A theory of justice"
                  },
                  {
                    "title": "Grundlegung zur Metaphysik der Sitten"
                  }
                ]
              }
            }
          }
        """).right.get)
    }

    "Disallow unauthorized access" in {
      val query =
        graphql"""
          query  {
            me {
              name
            }
          }
        """

      executeQuery(query, token = None) should be (parse(
        """
          {
            "data": {
              "me": null
            },
            "errors": [
              {
                "message": "Unauthorized access",
                "path": [ "me" ],
                "locations": [{"line": 3, "column": 13}]
              }
            ]
          }
        """).right.get)
    }
  }

  def executeQuery(query: Document, vars: Json = Json.obj(), token: Option[AuthToken] = None) = {
    val context = AppContext(repo, repo, token)

    val futureResult = Executor.execute(schema, query, context,
      variables = vars,
      middleware = AuthMiddleware :: Nil,
      exceptionHandler = exceptionHandler,
      deferredResolver = context.deferredResolver)

    Await.result(futureResult, 10.seconds)
  }

  val exceptionHandler = ExceptionHandler {
    case (_, AuthException(message)) ⇒ HandledException(message)
  }
}
