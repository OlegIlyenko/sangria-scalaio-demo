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
import finalServer.SchemaDefinition.constantPrice

import scala.concurrent.ExecutionContext.Implicits.global

/** Guard GraphQL API from abuse with static query complexity analysis */
object Demo9QueryComplexityAnalysis extends App {

  // STEP: Define GraphQL Types & Schema

  val authorFetcher = Fetcher.caching(
    (ctx: BookRepo with AuthorRepo, ids: Seq[String]) ⇒
      ctx.authors(ids))(HasId(_.id))
  
  implicit lazy val BookType: ObjectType[Unit, Book] =
    deriveObjectType[Unit, Book](
      DeprecateField("authorId", "Please use `author` field instead."),
      AddFields(
        Field("author", OptionType(AuthorType),
          // NEW: define a small static costs for a fetcher-based field
          complexity = constantPrice(10),
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
        // NEW: define a small static costs for a fetcher-based field
        complexity = constantPrice(10),
        resolve = c ⇒ bookFetcher.deferRelSeq(booksByAuthor, c.value.id))))

  implicit val BookSortingType = deriveEnumType[BookSorting.Value]()

  val LimitArg = Argument("limit", OptionInputType(IntType), defaultValue = 5)
  val OffsetArg = Argument("offset", OptionInputType(IntType), defaultValue = 0)
  val BookSortingArg = Argument("sortBy", OptionInputType(BookSortingType))
  val TitleFilterArg = Argument("title", OptionInputType(StringType))

  val QueryType = ObjectType("Query", fields[BookRepo with AuthorRepo, Unit](
    Field("books", ListType(BookType),
      arguments = LimitArg :: OffsetArg :: BookSortingArg :: TitleFilterArg :: Nil,
      // NEW: define complexity of `books` field based on formula:
      // NEW: <cost of DB access> + <limit> * <child sub-query cost>
      complexity = Some((_, args, child) ⇒ 100 + args.arg(LimitArg) * child),
      resolve = c ⇒ c.withArgs(LimitArg, OffsetArg, BookSortingArg, TitleFilterArg)(
        c.ctx.allBooks))))

  val schema = Schema(QueryType)

  // STEP: Create akka-http server and expose GraphQL route

  val repo = InMemoryDbRepo.createDatabase

  // NEW: define query reducers to statically analyze the query and
  // NEW: prevent complex queries from being executed
  val reducers = List(
    QueryReducer.rejectMaxDepth[Any](15),
    QueryReducer.rejectComplexQueries[Any](200, (complexity, _) ⇒
      new IllegalStateException(s"Too complex query: $complexity/200")))

  simpleServer { (query, operationName, variables, _, tracing) ⇒
    Executor.execute(schema, query, repo,
      variables = variables,
      operationName = operationName,
      deferredResolver = DeferredResolver.fetchers(authorFetcher, bookFetcher),
      // NEW: provide the list of reducers for an execution
      queryReducers = reducers,
      middleware = if (tracing) SlowLog.apolloTracing :: Nil else Nil)
  }
}
