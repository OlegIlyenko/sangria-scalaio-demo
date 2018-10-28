package demos

import language.postfixOps

import io.circe.Json

import model.Book
import sangria.schema._
import sangria.macros._
import sangria.execution._
import sangria.marshalling.circe._

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

/** Most basic example of GraphQL Schema definition and query execution */
object Demo1Basics extends App {

  // STEP: Define some data

  val books = List(
    Book("1", "Harry Potter and the Philosopher's Stone", "J. K. Rowling"),
    Book("2", "A Game of Thrones", "George R. R. Martin"))

  // STEP: Define GraphQL Types & Schema

  val BookType = ObjectType("Book", fields[Unit, Book](
    Field("id", StringType, resolve = _.value.id),
    Field("title", StringType, resolve = _.value.title),
    Field("authorId", StringType, resolve = _.value.authorId)))

  val QueryType = ObjectType("Query", fields[Unit, Unit](
    Field("books", ListType(BookType), resolve = _ ⇒ books)))

  val schema = Schema(QueryType)

  // STEP: Define a query

  val query =
    graphql"""
      {
        books {
          title
          authorId
        }
      }
    """

  // STEP: Execute query against the schema

  val result: Future[Json] = Executor.execute(schema, query)

  println(Await.result(result, 1 second))
}
