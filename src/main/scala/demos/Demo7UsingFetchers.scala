package demos

import model._
import sangria.execution._
import sangria.macros.derive._
import sangria.marshalling.circe._
import sangria.schema._
import common.CustomScalars._
import common.GraphQLRoutes.simpleServer
import sangria.execution.deferred.{DeferredResolver, Fetcher, HasId}
import sangria.slowlog.SlowLog

import scala.concurrent.ExecutionContext.Implicits.global

/** Efficiently load author information with Fetch API */
object Demo7UsingFetchers extends App {

  // STEP: Define GraphQL Types & Schema

  // NEW: add fetcher to load authors in batch
  val authorFetcher = Fetcher.caching(
    (ctx: AuthorRepo, ids: Seq[String]) ⇒
      ctx.authors(ids))(HasId(_.id))
  
  implicit lazy val BookType: ObjectType[Unit, Book] =
    deriveObjectType[Unit, Book](
      DeprecateField("authorId", "Please use `author` field instead."),
      AddFields(
        Field("author", OptionType(AuthorType),
          // NEW: use fetcher to defer loading author by ID
          resolve = c ⇒ authorFetcher.defer(c.value.authorId))))

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
      // NEW: provide fetcher to load object in batches during execution
      deferredResolver = DeferredResolver.fetchers(authorFetcher),
      middleware = if (tracing) SlowLog.apolloTracing :: Nil else Nil)
  }
}
