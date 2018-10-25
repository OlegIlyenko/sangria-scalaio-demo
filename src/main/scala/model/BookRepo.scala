package model

import scala.concurrent.Future

case class Book(
  id: String,
  title: String,
  authorId: String,
  description: Option[String] = None)

trait BookRepo {
  def allBooks(
    limit: Int = 100,
    offset: Int = 0,
    sorting: Option[BookSorting.Value] = None,
    title: Option[String] = None): Future[Seq[Book]]

  def book(id: String): Future[Option[Book]]
  def books(ids: Seq[String]): Future[Seq[Book]]
  def booksByAuthors(authorIds: Seq[String]): Future[Seq[Book]]

  def addBook(book: Book): Future[Book]
  def deleteBook(id: String): Future[Option[Book]]
}

object BookSorting extends Enumeration {
  val Id, Title = Value
}

