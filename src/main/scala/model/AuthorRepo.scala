package model

import java.time.LocalDate

import scala.concurrent.Future

case class Author(
  id: String,
  name: String,
  bio: Option[String],
  birthDate: LocalDate,
  deathDate: Option[LocalDate])

trait AuthorRepo {
  def allAuthors(limit: Int, offset: Int): Future[Seq[Author]]
  def author(id: String): Future[Option[Author]]
  def authors(id: Seq[String]): Future[Seq[Author]]
}
