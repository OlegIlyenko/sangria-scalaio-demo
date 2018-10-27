package demos

import model._
import sangria.execution._
import sangria.macros.derive._
import sangria.marshalling.circe._
import sangria.schema._
import common.CustomScalars._
import common.GraphQLRoutes.simpleServer

import scala.concurrent.ExecutionContext.Implicits.global

/**
  * Use an SQL database to load the book and author
  * data returned by GraphQL API
  */
object Demo4AddingDatabase extends App {

  // STEP: Define GraphQL Types & Schema

  implicit lazy val BookType: ObjectType[Unit, Book] = deriveObjectType[Unit, Book]()
  implicit lazy val AuthorType = deriveObjectType[Unit, Author]()

  // NEW: use repository type a context object
  val QueryType = ObjectType("Query", fields[BookRepo with AuthorRepo, Unit](
    Field("books", ListType(BookType),
      description = Some(
        "Gives the list of books sorted and filtered based on the arguments"),
      // NEW: load books from context object repo
      resolve = c ⇒ c.ctx.allBooks())))

  val schema = Schema(QueryType)

  // STEP: Create akka-http server and expose GraphQL route

  // NEW: crete new DB and repository
  val repo = InMemoryDbRepo.createDatabase

  simpleServer { (query, operationName, variables, _, _) ⇒
    // NEW: provide a `repo` to a query executor
    Executor.execute(schema, query, repo,
      variables = variables,
      operationName = operationName)
  }
}
