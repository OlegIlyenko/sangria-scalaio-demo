package demos

import model._
import sangria.execution._
import sangria.macros.derive._
import sangria.marshalling.circe._
import sangria.schema._
import common.CustomScalars._
import common.GraphQLRoutes.simpleServer
import sangria.slowlog.SlowLog

import scala.concurrent.ExecutionContext.Implicits.global

/** Representing book-author relation with an object type field */
object Demo6BookAuthorRelation extends App {

  // STEP: Define GraphQL Types & Schema

  // NEW: parametrize derived GraphQL object type
  implicit lazy val BookType: ObjectType[AuthorRepo, Book] =
    deriveObjectType[AuthorRepo, Book](
      // NEW: deprecate `authorId` & add `author` field
      DeprecateField("authorId", "Please use `author` field instead."),
      AddFields(
        Field("author", OptionType(AuthorType),
          resolve = c ⇒ c.ctx.author(c.value.authorId))))

  implicit lazy val AuthorType = deriveObjectType[Unit, Author]()

  implicit val BookSortingType = deriveEnumType[BookSorting.Value]()

  val QueryType = ObjectType("Query", fields[BookRepo with AuthorRepo, Unit](
    Field("books", ListType(BookType),
      resolve = c ⇒ c.ctx.allBooks())))

  val schema = Schema(QueryType)

  // STEP: Create akka-http server and expose GraphQL route

  val repo = InMemoryDbRepo.createDatabase

  simpleServer { (query, operationName, variables, _, tracing) ⇒
    Executor.execute(schema, query, repo,
      variables = variables,
      operationName = operationName,
      // NEW: add middleware to show tracing info in the playground
      middleware = if (tracing) SlowLog.apolloTracing :: Nil else Nil)
  }
}
