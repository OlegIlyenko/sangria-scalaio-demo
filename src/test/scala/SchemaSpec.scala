import fullServer.AppContext
import org.scalatest.{Matchers, WordSpec}
import sangria.ast.Document
import sangria.macros._
import sangria.execution.Executor
import sangria.marshalling.circe._
import io.circe._
import io.circe.parser._

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import fullServer.SchemaDefinition.schema
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
                  "id": "OL653987W",
                  "title": "Leviathan"
                },
                {
                  "id": "OL654092W",
                  "title": "The English works of Thomas Hobbes of Malmesbury"
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
  }

  def executeQuery(query: Document, vars: Json = Json.obj()) = {
    val context = new AppContext(repo, repo)

    val futureResult = Executor.execute(schema, query, context,
      variables = vars,
      deferredResolver = context.deferredResolver)

    Await.result(futureResult, 10.seconds)
  }
}
