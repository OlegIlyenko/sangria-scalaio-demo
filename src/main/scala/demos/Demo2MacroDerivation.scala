package demos

import model.Book
import sangria.execution._
import sangria.macros._
import sangria.macros.derive._
import sangria.marshalling.circe._
import sangria.schema._

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.language.postfixOps

/**
  * Using `deriveObjectType` to derive GraphQl object type
  * based on the `Book` case class
  */
object Demo2MacroDerivation extends App {

  // Define some data

  val books = List(
    Book("1", "Harry Potter and the Philosopher's Stone", "J. K. Rowling"),
    Book("1", "A Game of Thrones", "George R. R. Martin"))

  // Define GraphQL Types & Schema

  val BookType = deriveObjectType[Unit, Book]()

  val QueryType = ObjectType("Query", fields[Unit, Unit](
    Field("books", ListType(BookType), resolve = _ ⇒ books)))

  val schema = Schema(QueryType)

  // Execute a query

  val query =
    graphql"""
      {
        books {
          title
          authorId
        }
      }
    """

  val result = Executor.execute(schema, query)

  println(Await.result(result, 1 second))
}
