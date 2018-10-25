package demos

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import common.GraphQLRoutes
import model._
import sangria.execution._
import sangria.execution.deferred._
import sangria.macros.derive._
import sangria.marshalling.circe._
import sangria.schema._
import sangria.slowlog.SlowLog
import common.CustomScalars._

import scala.language.postfixOps

/** Guard GraphQL API from abuse with static query complexity analysis */
object Demo9QueryComplexityAnalysis extends App {

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

  val LimitArg = Argument("limit", OptionInputType(IntType), defaultValue = 5)
  val OffsetArg = Argument("offset", OptionInputType(IntType), defaultValue = 0)
  val BookSortingArg = Argument("sortBy", OptionInputType(BookSortingType))
  val TitleFilterArg = Argument("title", OptionInputType(StringType))

  val QueryType = ObjectType("Query", fields[BookRepo with AuthorRepo, Unit](
    Field("books", ListType(BookType),
      arguments = LimitArg :: OffsetArg :: BookSortingArg :: TitleFilterArg :: Nil,
      complexity = Some((_, args, child) ⇒ 100 + args.arg(LimitArg) * child),
      resolve = c ⇒ c.withArgs(LimitArg, OffsetArg, BookSortingArg, TitleFilterArg)(
        c.ctx.allBooks))))

  val schema = Schema(QueryType)

  // Create akka-http server and expose GraphQL route

  implicit val system = ActorSystem("sangria-server")
  implicit val materializer = ActorMaterializer()

  import system.dispatcher

  val repo = InMemoryDbRepo.createDatabase

  val reducers = List(
    QueryReducer.rejectMaxDepth[Any](15),
    QueryReducer.rejectComplexQueries[Any](200, (complexity, _) ⇒
      new IllegalStateException(s"Too complex query: $complexity/200")))

  val route = GraphQLRoutes.route { (query, operationName, variables, _, tracing) ⇒
    Executor.execute(schema, query, repo,
      variables = variables,
      operationName = operationName,
      deferredResolver = DeferredResolver.fetchers(authorFetcher, bookFetcher),
      queryReducers = reducers,
      middleware = if (tracing) SlowLog.apolloTracing :: Nil else Nil)
  }

  Http().bindAndHandle(route, "0.0.0.0", 8080)
}
