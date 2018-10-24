package finalServer

import common.AuthToken
import sangria.execution.deferred._
import model._

case class AppContext(books: BookRepo, authors: AuthorRepo, authToken: Option[AuthToken]) {
  val authorFetcher = Fetcher.caching(
    (ctx: AppContext, ids: Seq[String]) ⇒
      ctx.authors.authors(ids))(HasId(_.id))

  val booksByAuthor = Relation[Book, String]("booksByAuthor", book ⇒ Seq(book.authorId))

  val bookFetcher = Fetcher.relCaching(
    (ctx: AppContext, ids: Seq[String]) ⇒
      ctx.books.books(ids),
    (ctx: AppContext, relIds: RelationIds[Book]) ⇒
      ctx.books.booksByAuthors(relIds(booksByAuthor)))(HasId(_.id))

  val deferredResolver =
    DeferredResolver.fetchers[AppContext](bookFetcher, authorFetcher)
}
