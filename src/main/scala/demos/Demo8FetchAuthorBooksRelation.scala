package demos

import model._
import sangria.execution._
import sangria.execution.deferred._
import sangria.macros.derive._
import sangria.marshalling.circe._
import sangria.schema._
import sangria.slowlog.SlowLog
import common.CustomScalars._
import common.GraphQLRoutes.simpleServer

import scala.concurrent.ExecutionContext.Implicits.global

/**
  * Efficiently load author books information with Fetch API.
  * This represents 1:m relationship between author and his/her books.
  */
object Demo8FetchAuthorBooksRelation extends App {

  // STEP: Define GraphQL Types & Schema

  val authorFetcher = Fetcher.caching(
    (ctx: BookRepo with AuthorRepo, ids: Seq[String]) ⇒
      ctx.authors(ids))(HasId(_.id))
  
  implicit lazy val BookType: ObjectType[Unit, Book] =
    deriveObjectType[Unit, Book](
      DeprecateField("authorId", "Please use `author` field instead."),
      AddFields(
        Field("author", OptionType(AuthorType),
          resolve = c ⇒ authorFetcher.defer(c.value.authorId))))

  // NEW: define author-book relation
  val booksByAuthor = Relation[Book, String]("booksByAuthor", book ⇒ Seq(book.authorId))

  // NEW: define fetcher to load books by author ID in batches
  val bookFetcher = Fetcher.relCaching(
    (ctx: BookRepo with AuthorRepo, ids: Seq[String]) ⇒
      ctx.books(ids),
    (ctx: BookRepo with AuthorRepo, relIds: RelationIds[Book]) ⇒
      ctx.booksByAuthors(relIds(booksByAuthor)))(HasId(_.id))

  implicit lazy val AuthorType = deriveObjectType[Unit, Author](
    // NEW: add `books` field & defer its loading to fetcher
    AddFields(
      Field("books", ListType(BookType),
        resolve = c ⇒ bookFetcher.deferRelSeq(booksByAuthor, c.value.id))))

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
      // NEW: add `bookFetcher` to a `DeferredResolver`
      deferredResolver = DeferredResolver.fetchers(authorFetcher, bookFetcher),
      middleware = if (tracing) SlowLog.apolloTracing :: Nil else Nil)
  }
}
