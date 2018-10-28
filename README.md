## Sangria Scala.io 2018 Demo

[![Build Status](https://travis-ci.com/OlegIlyenko/sangria-scalaio-demo.svg?branch=master)](https://travis-ci.com/OlegIlyenko/sangria-scalaio-demo)

A demo project that demonstrates a [GraphQL](https://graphql.org) API implemented with [sangria](https://github.com/sangria-graphql/sangria), [akka-http](https://github.com/akka/akka-http), [circe](https://github.com/circe/circe).

### Presentation slides

[![Presentation slides](https://olegilyenko.github.io/presentation-building-graphql-api-with-sangria-scalaio/assets/img/preview.png)](https://olegilyenko.github.io/presentation-building-graphql-api-with-sangria-scalaio/)

### Getting Started

After starting the server with

```bash
sbt run

# or, if you want to watch the source code changes
 
sbt ~reStart
``` 

You can run queries interactively using [graphql-playground](https://github.com/prisma/graphql-playground) 
by opening [http://localhost:8080](http://localhost:8080) in a browser or query the 
`/graphql` endpoint directly:

![GraphQL Playground](https://olegilyenko.github.io/presentation-building-graphql-api-with-sangria-scalaio/assets/img/playground.png)

### Code Structure

This demo contains several packages under `src/main/scala`:

* **common** - common definitions that are used by all examples. It also contains some plumbing (like full HTTP routing) to make more advanced examples more simple.
* **demos** - step by step walkthrough from the most basic examples to more advanced GraphQL servers that use DB, auth, etc. Every demo object (like `Demo1Basics`) is self-contained and contains `main` method (extends `App`), so you can run it.
* **finalServer** - the final server implementation that includes all demonstrated elements
* **model** - defines model for the `Book` and `Author` case classes as well as repositories (including SQL-based implementation)

I would also recommend to explore the `/src/test/scala` folder - it contains several example tests.

### Step-by-Step Walkthrough

In addition to the [final server implantation](https://github.com/OlegIlyenko/sangria-scalaio-demo/tree/master/src/main/scala/finalServer)
the project contains step-by-step demonstration of various GraphQL and Sangria concepts. Every step is self-contained and builds up on
top of previous steps:

1. Most basic example of GraphQL Schema definition and query execution ([Demo1Basics.scala](https://github.com/OlegIlyenko/sangria-scalaio-demo/blob/master/src/main/scala/demos/Demo1Basics.scala))    
1. Using `deriveObjectType` to derive GraphQL object type based on the `Book` case class ([Demo2MacroDerivation.scala](https://github.com/OlegIlyenko/sangria-scalaio-demo/blob/master/src/main/scala/demos/Demo2MacroDerivation.scala))    
1. Exposing the GraphQL schema via HTTP API ([Demo3ExposeGraphQLViaHttp.scala](https://github.com/OlegIlyenko/sangria-scalaio-demo/blob/master/src/main/scala/demos/Demo3ExposeGraphQLViaHttp.scala))    
1. Use an SQL database to load the book and author data returned by GraphQL API ([Demo4AddingDatabase.scala](https://github.com/OlegIlyenko/sangria-scalaio-demo/blob/master/src/main/scala/demos/Demo4AddingDatabase.scala))    
1. Use field arguments to provide pagination, sorting and filtering ([Demo5PaginationSortingFiltering.scala](https://github.com/OlegIlyenko/sangria-scalaio-demo/blob/master/src/main/scala/demos/Demo5PaginationSortingFiltering.scala))    
1. Representing book-author relation with an object type field ([Demo6BookAuthorRelation.scala](https://github.com/OlegIlyenko/sangria-scalaio-demo/blob/master/src/main/scala/demos/))    
1. Efficiently load author information with Fetch API ([Demo7UsingFetchers.scala](https://github.com/OlegIlyenko/sangria-scalaio-demo/blob/master/src/main/scala/demos/Demo7UsingFetchers.scala))    
1. Efficiently load author books information with Fetch API ([Demo8FetchAuthorBooksRelation.scala](https://github.com/OlegIlyenko/sangria-scalaio-demo/blob/master/src/main/scala/demos/Demo8FetchAuthorBooksRelation.scala))    
1. Guard GraphQL API from abuse with static query complexity analysis ([Demo9QueryComplexityAnalysis.scala](https://github.com/OlegIlyenko/sangria-scalaio-demo/blob/master/src/main/scala/demos/Demo9QueryComplexityAnalysis.scala))    
1. Securing GraphQL API with OAuth and JWT tokens ([Demo10Auth.scala](https://github.com/OlegIlyenko/sangria-scalaio-demo/blob/master/src/main/scala/demos/Demo10Auth.scala))    

In each class you will find comments that highlight separate steps (with `// STEP: ...`)
and things that are new in comparison with previous steps (with `// NEW: ...`). 
If you add extra highlighting in the IDE (e.g. TODO highlighting in Intellij IDEA)
you can get additional visual hints:

![TODO highlighting in Intellij IDEA](https://olegilyenko.github.io/presentation-building-graphql-api-with-sangria-scalaio/assets/img/todo-highlighting.png)

### Demo Database 

The demo uses a simple H2 in-memory database (it would be re-created automatically when server starts). 

<details>
  <summary>DB schema DDL</summary>
  
```sql
create table "BOOKS" (
  "BOOK_ID" VARCHAR NOT NULL PRIMARY KEY,
  "TITLE" VARCHAR NOT NULL,
  "AUTHOR_ID" VARCHAR NOT NULL,
  "description" VARCHAR)
  
alter table "BOOKS" 
  add constraint "AUTHOR_FK" foreign key("AUTHOR_ID") 
  references "AUTHORS"("AUTHOR_ID") on update NO ACTION on delete NO ACTION
  
create table "AUTHORS" (
  "AUTHOR_ID" VARCHAR NOT NULL PRIMARY KEY,
  "NAME" VARCHAR NOT NULL,
  "BIO" VARCHAR,
  "BIRTH_DATE" DATE NOT NULL,
  "DEATH_DATE" DATE)
```
</details>


The example book data was taken from [Open Library](https://openlibrary.org/).  

### JWT Auth

In order to demonstrate the authentication and authorization, demo uses [JWT](https://jwt.io/) tokens with basic OAuth bearer `Authorization` header.

If you would like to try out the examples (in particular `me` field), you can use following header:

```
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VyTmFtZSI6IkpvaG4gRG9lIiwiYm9va3MiOlsiT0wzMDMxMFciLCJPTDk5ODQzVyJdfQ.ffqCpfgWrY40k8JWj56mUpvW0ZfWLhTqrLHwMZeXgXc
```   

### GraphQL Schema

The demo project implements following schema:

```graphql
type Author {
  id: String!
  name: String!
  bio: String
  birthDate: Date!
  deathDate: Date
  books: [Book!]!
}

type Book {
  id: String!
  title: String!
  description: String
  author: Author
}

input BookInput {
  id: String!
  title: String!
  description: String
  authorId: String!
}

enum BookSorting {
  Title
  Id
}

"Represents local date. Serialized as ISO-formatted string."
scalar Date

type Me {
  "The name of authenticated user"
  name: String!
  favouriteBooks: [Book!]!
}

type Mutation {
  addBook(book: BookInput!): Book
  deleteBook(id: ID!): Book
}

type Query {
  "Gives the list of books sorted and filtered based on the arguments"
  books(limit: Int = 5, offset: Int = 0, sortBy: BookSorting, title: String): [Book!]!

  "Returns a book with a specified ID."
  book(id: ID!): Book
  authors(limit: Int = 5, offset: Int = 0): [Author!]!
  author(id: ID!): Author

  "Information about authenticated user. Requires OAuth token."
  me: Me
}
```