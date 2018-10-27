package finalServer

import common.AuthToken
import sangria.schema._
import sangria.macros.derive._
import model._
import common.CustomScalars._
import io.circe.generic.auto._
import sangria.marshalling.circe._

object SchemaDefinition {
  implicit lazy val BookType: ObjectType[AppContext, Book] = deriveObjectType[AppContext, Book](
    ReplaceField("authorId",
      Field("author", OptionType(AuthorType),
        complexity = constantPrice(10),
        resolve = c ⇒ c.ctx.authors.author(c.value.authorId))))

  implicit lazy val AuthorType = deriveObjectType[AppContext, Author](
    AddFields(
      Field("books", ListType(BookType),
        complexity = constantPrice(10),
        resolve = c ⇒ c.ctx.bookFetcher.deferRelSeq(c.ctx.booksByAuthor, c.value.id))))

  implicit val BookSortingType = deriveEnumType[BookSorting.Value]()

  val IdArg = Argument("id", IDType)
  val LimitArg = Argument("limit", OptionInputType(IntType), defaultValue = 5)
  val OffsetArg = Argument("offset", OptionInputType(IntType), defaultValue = 0)
  val BookSortingArg = Argument("sortBy", OptionInputType(BookSortingType))
  val TitleFilterArg = Argument("title", OptionInputType(StringType))

  val MeType = ObjectType("Me", fields[AppContext, AuthToken](
    Field("name", StringType, Some("The name of authenticated user"),
      resolve = c ⇒ c.value.userName),

    Field("favouriteBooks", ListType(BookType),
      complexity = constantPrice(10),
      resolve = c ⇒ c.ctx.bookFetcher.deferSeq(c.value.books))))

  val QueryType = ObjectType("Query", fields[AppContext, Unit](
    Field("books", ListType(BookType),
      description = Some("Gives the list of books sorted and filtered based on the arguments"),
      arguments = LimitArg :: OffsetArg :: BookSortingArg :: TitleFilterArg :: Nil,
      complexity = Some((_, args, child) ⇒ 100 + args.arg(LimitArg) * child),
      resolve = c ⇒ c.withArgs(LimitArg, OffsetArg, BookSortingArg, TitleFilterArg)(
        c.ctx.books.allBooks)),

    Field("book", OptionType(BookType),
      description = Some("Returns a book with a specified ID."),
      arguments = IdArg :: Nil,
      complexity = constantPrice(10),
      resolve = c ⇒ c.ctx.books.book(c arg IdArg)),

    Field("authors", ListType(AuthorType),
      arguments = LimitArg :: OffsetArg :: Nil,
      complexity = Some((_, args, child) ⇒ 100 + args.arg(LimitArg) * child),
      resolve = c ⇒ c.withArgs(LimitArg, OffsetArg)(c.ctx.authors.allAuthors)),
    Field("author", OptionType(AuthorType),
      arguments = IdArg :: Nil,
      complexity = constantPrice(10),
      resolve = c ⇒ c.ctx.authors.author(c arg IdArg)),

    Field("me", OptionType(MeType),
      description = Some("Information about authenticated user. Requires OAuth token."),
      tags = Authorized :: Nil,
      resolve = c ⇒ c.ctx.authToken)))

  val BookInputType = deriveInputObjectType[Book](
    InputObjectTypeName("BookInput"))

  val BookArg = Argument("book", BookInputType)

  val MutationType = ObjectType("Mutation", fields[AppContext, Unit](
    Field("addBook", OptionType(BookType),
      arguments = BookArg :: Nil,
      resolve = c ⇒ c.ctx.books.addBook(c arg BookArg)),
    Field("deleteBook", OptionType(BookType),
      arguments = IdArg :: Nil,
      resolve = c ⇒ c.ctx.books.deleteBook(c arg IdArg))))

  val schema = Schema(QueryType, Some(MutationType))

  def constantPrice(num: Double): Option[(Any, Args, Double) ⇒ Double] =
    Some((_, _, child) ⇒ child + num)
}
