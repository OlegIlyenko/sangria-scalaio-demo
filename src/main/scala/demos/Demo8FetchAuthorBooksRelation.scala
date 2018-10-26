package demos

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import common.GraphQLRoutes
import finalServer.AppContext
import model._
import sangria.execution._
import sangria.execution.deferred._
import sangria.macros.derive._
import sangria.marshalling.circe._
import sangria.schema._
import sangria.slowlog.SlowLog
import common.CustomScalars._
import finalServer.SchemaDefinition.constantPrice

import scala.language.postfixOps

/** Efficiently load author books information with Fetch API */
object Demo8FetchAuthorBooksRelation extends App {

  // Define GraphQL Types & Schema

  val authorFetcher = Fetcher.caching(
    (ctx: BookRepo with AuthorRepo, ids: Seq[String]) ⇒
      ctx.authors(ids))(HasId(_.id))
  
  implicit lazy val BookType: ObjectType[Unit, Book] =
    deriveObjectType[Unit, Book](
      DeprecateField("authorId", "Please use `author` field instead."),
      AddFields(
        Field("author", OptionType(AuthorType),
          resolve = c ⇒ authorFetcher.defer(c.value.authorId))))

  val booksByAuthor = Relation[Book, String]("booksByAuthor", book ⇒ Seq(book.authorId))

  val bookFetcher = Fetcher.relCaching(
    (ctx: BookRepo with AuthorRepo, ids: Seq[String]) ⇒
      ctx.books(ids),
    (ctx: BookRepo with AuthorRepo, relIds: RelationIds[Book]) ⇒
      ctx.booksByAuthors(relIds(booksByAuthor)))(HasId(_.id))

  implicit lazy val AuthorType = deriveObjectType[Unit, Author](
    AddFields(
      Field("books", ListType(BookType),
        resolve = c ⇒ bookFetcher.deferRelSeq(booksByAuthor, c.value.id))))

  implicit val BookSortingType = deriveEnumType[BookSorting.Value]()

  val QueryType = ObjectType("Query", fields[BookRepo with AuthorRepo, Unit](
    Field("books", ListType(BookType),
      resolve = c ⇒ c.ctx.allBooks())))

  val schema = Schema(QueryType)

  // Create akka-http server and expose GraphQL route

  implicit val system = ActorSystem("sangria-server")
  implicit val materializer = ActorMaterializer()

  import system.dispatcher

  val repo = InMemoryDbRepo.createDatabase

  val route = GraphQLRoutes.route { (query, operationName, variables, _, tracing) ⇒
    Executor.execute(schema, query, repo,
      variables = variables,
      operationName = operationName,
      deferredResolver = DeferredResolver.fetchers(authorFetcher, bookFetcher),
      middleware = if (tracing) SlowLog.apolloTracing :: Nil else Nil)
  }

  Http().bindAndHandle(route, "0.0.0.0", 8080)
}
