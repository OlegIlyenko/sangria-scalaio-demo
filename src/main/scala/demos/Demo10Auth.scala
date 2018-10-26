package demos

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import common.{AuthToken, GraphQLRoutes}
import finalServer.{AppContext, AuthException, AuthMiddleware, Authorized}
import model._
import sangria.execution._
import sangria.execution.deferred._
import sangria.macros.derive._
import sangria.marshalling.circe._
import sangria.schema._
import common.CustomScalars._
import finalServer.SchemaDefinition.constantPrice

import scala.language.postfixOps

/** Securing GraphQL API with OAuth and JWT tokens */
object Demo10Auth extends App {

  /*
   For testing, you can use this HTTP header:

    {
      "Authorization": "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VyTmFtZSI6Ik9sZWcgSWx5ZW5rbyIsImJvb2tzIjpbIk9MMzAzMTBXIiwiT0w5OTg0M1ciXX0.k4MOSGhh_55nFOimhX97PKa4c5lZguLN4UQQZ2jf9Iw"
    }
  */

  // Define GraphQL Types & Schema

  implicit lazy val BookType: ObjectType[AppContext, Book] =
    deriveObjectType[AppContext, Book](
      DeprecateField("authorId", "Please use `author` field instead."),
      AddFields(
        Field("author", OptionType(AuthorType),
          resolve = c ⇒ c.ctx.authorFetcher.defer(c.value.authorId))))

  implicit lazy val AuthorType = deriveObjectType[AppContext, Author](
    AddFields(
      Field("books", ListType(BookType),
        resolve = c ⇒ c.ctx.bookFetcher.deferRelSeq(c.ctx.booksByAuthor, c.value.id))))

  implicit val BookSortingType = deriveEnumType[BookSorting.Value]()

  val MeType = ObjectType("Me", fields[AppContext, AuthToken](
    Field("name", StringType, Some("The name of authenticated user"),
      resolve = c ⇒ c.value.userName),

    Field("favouriteBooks", ListType(BookType),
      complexity = constantPrice(10),
      resolve = c ⇒ c.ctx.bookFetcher.deferSeq(c.value.books))))

  val LimitArg = Argument("limit", OptionInputType(IntType), defaultValue = 5)
  val OffsetArg = Argument("offset", OptionInputType(IntType), defaultValue = 0)
  val BookSortingArg = Argument("sortBy", OptionInputType(BookSortingType))
  val TitleFilterArg = Argument("title", OptionInputType(StringType))

  val QueryType = ObjectType("Query", fields[AppContext, Unit](
    Field("books", ListType(BookType),
      arguments = LimitArg :: OffsetArg :: BookSortingArg :: TitleFilterArg :: Nil,
      complexity = Some((_, args, child) ⇒ 100 + args.arg(LimitArg) * child),
      resolve = c ⇒ c.withArgs(LimitArg, OffsetArg, BookSortingArg, TitleFilterArg)(
        c.ctx.books.allBooks)),

    Field("me", OptionType(MeType),
      description = Some("Information about authenticated user. Requires OAuth token."),
      tags = Authorized :: Nil,
      resolve = c ⇒ c.ctx.authToken)))

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

  val exceptionHandler = ExceptionHandler {
    case (_, AuthException(message)) ⇒ HandledException(message)
  }

  val route = GraphQLRoutes.route { (query, operationName, variables, authToken, _) ⇒
    val context = AppContext(repo, repo, authToken)

    Executor.execute(schema, query, context,
      variables = variables,
      operationName = operationName,
      deferredResolver = context.deferredResolver,
      queryReducers = reducers,
      exceptionHandler = exceptionHandler,
      middleware = AuthMiddleware :: Nil)
  }

  Http().bindAndHandle(route, "0.0.0.0", 8080)
}