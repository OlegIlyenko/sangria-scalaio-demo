package model

import language.postfixOps

import java.sql.Date
import java.time.LocalDate
import java.time.format.DateTimeFormatter

import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration._
import slick.jdbc.H2Profile.api._
import io.circe._
import io.circe.parser._
import io.circe.generic.auto._
import io.circe.Decoder._

import scala.io.Source

/** Book and Author repos implementation based on the in-memory H2 database */
class InMemoryDbRepo(db: Database)(implicit ec: ExecutionContext) extends BookRepo with AuthorRepo {
  import InMemoryDbRepo._

  def book(id: String) =
    books(Seq(id)).map(_.headOption)

  def books(ids: Seq[String]) =
    db.run(Books.filter(_.id inSet ids).result)

  def booksByAuthors(authorIds: Seq[String]) =
    db.run(Books.filter(_.authorId inSet authorIds).result)

  def addBook(book: Book) =
    db.run(Books += book).map(_ ⇒ book)
  
  def deleteBook(id: String) =
    for  {
      book ← book(id)
      _ ← db.run(Books.filter(_.id === id).delete).map(_ ⇒ Book)
    } yield book

  def author(id: String) =
    authors(Seq(id)).map(_.headOption)

  def authors(ids: Seq[String]) =
    db.run(Authors.filter(_.id inSet ids).result)

  def allAuthors(limit: Int, offset: Int) =
    db.run(Authors.drop(offset).take(limit).result)

  def allBooks(limit: Int, offset: Int, sorting: Option[BookSorting.Value], title: Option[String]) = {
    val withFilter = title match {
      case Some(t) ⇒ Books.filter(_.title like s"%$t%")
      case None ⇒ Books
    }

    val withSorting = sorting.fold(withFilter) {
      case BookSorting.Id ⇒ withFilter.sortBy(_.id.asc)
      case BookSorting.Title ⇒ withFilter.sortBy(_.title.asc)
    }

    db.run(withSorting.drop(offset).take(limit).result)
  }
}

/** Slick table definitions and data loading */
object InMemoryDbRepo {
  implicit val localDateToDate = MappedColumnType.base[LocalDate, Date](
    l ⇒ Date.valueOf(l),
    d ⇒ d.toLocalDate)

  class BookTable(tag: Tag) extends Table[Book](tag, "BOOKS") {
    def id = column[String]("BOOK_ID", O.PrimaryKey)
    def title = column[String]("TITLE")
    def description = column[Option[String]]("description")
    def authorId = column[String]("AUTHOR_ID")

    def author = foreignKey("AUTHOR_FK", authorId, Authors)(_.id)

    def * = (id, title, authorId, description) <> ((Book.apply _).tupled, Book.unapply)
  }

  val Books = TableQuery[BookTable]

  class AuthorTable(tag: Tag) extends Table[Author](tag, "AUTHORS") {
    def id = column[String]("AUTHOR_ID", O.PrimaryKey)
    def name = column[String]("NAME")
    def bio = column[Option[String]]("BIO")
    def birthDate = column[LocalDate]("BIRTH_DATE")
    def deathDate = column[Option[LocalDate]]("DEATH_DATE")

    def * = (id, name, bio, birthDate, deathDate) <> ((Author.apply _).tupled, Author.unapply)
  }

  val Authors = TableQuery[AuthorTable]

  implicit object CirceLocalDateCodec extends Encoder[LocalDate] with Decoder[LocalDate] {
    override def apply(a: LocalDate): Json =
      Encoder.encodeString.apply(a.format(DateTimeFormatter.ISO_LOCAL_DATE))
    override def apply(c: HCursor): Result[LocalDate] =
      Decoder.decodeString.map(s => LocalDate.parse(s, DateTimeFormatter.ISO_LOCAL_DATE)).apply(c)
  }

  def createDatabase(implicit ec: ExecutionContext) = {
    val db = Database.forConfig("memoryDb")
    val books = parse(Source.fromResource("bookData/books.json").getLines().mkString).right.get.as[Seq[Book]].right.get
    val authors = parse(Source.fromResource("bookData/authors.json").getLines().mkString).right.get.as[Seq[Author]].right.get

    val ddl = DBIO.seq(
      (Books.schema ++ Authors.schema).create,
      Authors ++= authors,
      Books ++= books)

    // For simplicity of this demo, this DB initialization is blocking at app startup
    Await.result(db.run(ddl), 10 seconds)

    new InMemoryDbRepo(db)
  }
}
