package demos

import language.postfixOps

import finalServer.MyTest.Book
import sangria.schema._
import sangria.macros._
import sangria.execution._
import sangria.marshalling.circe._

import scala.concurrent.Await
import scala.concurrent.duration._

import scala.concurrent.ExecutionContext.Implicits.global

/** Most basic example of GraphQL Schema definition and query execution */
object Demo1Basics extends App {

  // Define some data

  val books = List(
    Book("1", "Harry Potter and the Philosopher's Stone", "J. K. Rowling"),
    Book("1", "A Game of Thrones", "George R. R. Martin"))

  // Define GraphQL Types & Schema

  val BookType = ObjectType("Book", fields[Unit, Book](
    Field("id", IDType, resolve = _.value.id),
    Field("title", StringType, resolve = _.value.title),
    Field("authorId", StringType, resolve = _.value.authorId)))

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
