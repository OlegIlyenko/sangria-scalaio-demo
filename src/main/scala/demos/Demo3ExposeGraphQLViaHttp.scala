package demos

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import common.GraphQLRoutes
import model.Book
import sangria.execution._
import sangria.macros.derive._
import sangria.marshalling.circe._
import sangria.schema._

import scala.language.postfixOps

/** Let's expose our GraphQL schema via HTTP API */
object Demo3ExposeGraphQLViaHttp extends App {

  // Define some data

  val books = List(
    Book("1", "Harry Potter and the Philosopher's Stone", "J. K. Rowling"),
    Book("1", "A Game of Thrones", "George R. R. Martin"))

  // Define GraphQL Types & Schema

  val BookType = deriveObjectType[Unit, Book]()

  val QueryType = ObjectType("Query", fields[Unit, Unit](
    Field("books", ListType(BookType), resolve = _ ⇒ books)))

  val schema = Schema(QueryType)

  // Create akka-http server and expose GraphQL route

  implicit val system = ActorSystem("sangria-server")
  implicit val materializer = ActorMaterializer()

  import system.dispatcher

  val route = GraphQLRoutes.route { (query, operationName, variables, _, _) ⇒
    Executor.execute(schema, query,
      variables = variables,
      operationName = operationName)
  }

  Http().bindAndHandle(route, "0.0.0.0", 8080)
}
