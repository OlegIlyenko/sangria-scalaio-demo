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

/** Use field arguments to provide pagination, sorting and filtering */
object Demo5PaginationSortingFiltering extends App {

  // Define GraphQL Types & Schema

  implicit lazy val BookType: ObjectType[Unit, Book] = deriveObjectType[Unit, Book]()
  implicit lazy val AuthorType = deriveObjectType[Unit, Author]()

  implicit val BookSortingType = deriveEnumType[BookSorting.Value]()

  val LimitArg = Argument("limit", OptionInputType(IntType), defaultValue = 5)
  val OffsetArg = Argument("offset", OptionInputType(IntType), defaultValue = 0)
  val BookSortingArg = Argument("sortBy", OptionInputType(BookSortingType))
  val TitleFilterArg = Argument("title", OptionInputType(StringType))

  val QueryType = ObjectType("Query", fields[BookRepo with AuthorRepo, Unit](
    Field("books", ListType(BookType),
      arguments = LimitArg :: OffsetArg :: BookSortingArg :: TitleFilterArg :: Nil,
      resolve = c ⇒ c.withArgs(LimitArg, OffsetArg, BookSortingArg, TitleFilterArg)(
        c.ctx.allBooks))))

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
