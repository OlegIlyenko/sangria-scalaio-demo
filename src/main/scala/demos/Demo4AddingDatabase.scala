package demos

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import common.GraphQLRoutes
import model._
import sangria.execution._
import sangria.macros.derive._
import sangria.marshalling.circe._
import sangria.schema._
import common.CustomScalars._

import scala.language.postfixOps

/**
  * Use an SQL database to load the book and author
  * data returned by GraphQL API
  */
object Demo4AddingDatabase extends App {

  // Define GraphQL Types & Schema

  implicit lazy val BookType: ObjectType[Unit, Book] = deriveObjectType[Unit, Book]()
  implicit lazy val AuthorType = deriveObjectType[Unit, Author]()

  val QueryType = ObjectType("Query", fields[BookRepo with AuthorRepo, Unit](
    Field("books", ListType(BookType),
      description = Some("Gives the list of books sorted and filtered based on the arguments"),
      resolve = c ⇒ c.ctx.allBooks())))

  val schema = Schema(QueryType)

  // Create akka-http server and expose GraphQL route

  implicit val system = ActorSystem("sangria-server")
  implicit val materializer = ActorMaterializer()

  import system.dispatcher

  val repo = InMemoryDbRepo.createDatabase

  val route = GraphQLRoutes.route { (query, operationName, variables, _, _) ⇒
    Executor.execute(schema, query, repo,
      variables = variables,
      operationName = operationName)
  }

  Http().bindAndHandle(route, "0.0.0.0", 8080)
}