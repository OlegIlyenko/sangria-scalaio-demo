package demos

import model._
import sangria.execution._
import sangria.macros.derive._
import sangria.marshalling.circe._
import sangria.schema._
import common.CustomScalars._
import common.GraphQLRoutes.simpleServer

import scala.concurrent.ExecutionContext.Implicits.global

/** Use field arguments to provide pagination, sorting and filtering */
object Demo5PaginationSortingFiltering extends App {

  // STEP: Define GraphQL Types & Schema

  implicit lazy val BookType: ObjectType[Unit, Book] = deriveObjectType[Unit, Book]()
  implicit lazy val AuthorType = deriveObjectType[Unit, Author]()

  implicit val BookSortingType = deriveEnumType[BookSorting.Value]()

  // NEW: define pagination, sorting & filter arguments
  val LimitArg = Argument("limit", OptionInputType(IntType), defaultValue = 5)
  val OffsetArg = Argument("offset", OptionInputType(IntType), defaultValue = 0)
  val BookSortingArg = Argument("sortBy", OptionInputType(BookSortingType))
  val TitleFilterArg = Argument("title", OptionInputType(StringType))

  val QueryType = ObjectType("Query", fields[BookRepo with AuthorRepo, Unit](
    Field("books", ListType(BookType),
      // NEW: declare arguments
      arguments = LimitArg :: OffsetArg :: BookSortingArg :: TitleFilterArg :: Nil,
      // NEW: retrieve argument values & pass them to `allBooks`
      resolve = c ⇒ c.withArgs(LimitArg, OffsetArg, BookSortingArg, TitleFilterArg)(
        c.ctx.allBooks))))

  val schema = Schema(QueryType)

  // STEP: Create akka-http server and expose GraphQL route

  val repo = InMemoryDbRepo.createDatabase

  simpleServer { (query, operationName, variables, _, _) ⇒
    Executor.execute(schema, query, repo,
      variables = variables,
      operationName = operationName)
  }
}
