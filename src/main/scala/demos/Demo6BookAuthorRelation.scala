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
import sangria.slowlog.SlowLog

import scala.language.postfixOps

/** Representing book-author relation with an object type field */
object Demo6BookAuthorRelation extends App {

  // Define GraphQL Types & Schema

  implicit lazy val BookType: ObjectType[AuthorRepo, Book] =
    deriveObjectType[AuthorRepo, Book](
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

  // Create akka-http server and expose GraphQL route

  implicit val system = ActorSystem("sangria-server")
  implicit val materializer = ActorMaterializer()

  import system.dispatcher

  val repo = InMemoryDbRepo.createDatabase

  val route = GraphQLRoutes.route { (query, operationName, variables, _, tracing) ⇒
    Executor.execute(schema, query, repo,
      variables = variables,
      operationName = operationName,
      middleware = if (tracing) SlowLog.apolloTracing :: Nil else Nil)
  }

  Http().bindAndHandle(route, "0.0.0.0", 8080)
}
