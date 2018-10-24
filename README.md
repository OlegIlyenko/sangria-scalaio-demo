## Sangria Scala.io 2018 Demo

[![Build Status](https://travis-ci.com/OlegIlyenko/sangria-scalaio-demo.svg?branch=master)](https://travis-ci.com/OlegIlyenko/sangria-scalaio-demo)

A demo project that demonstrates a [GraphQL](https://graphql.org) API implemented with [sangria](https://github.com/sangria-graphql/sangria), [akka-http](https://github.com/akka/akka-http), [circe](https://github.com/circe/circe).

### Getting Started

After starting the server with

```bash
sbt run

# or, if you want to watch the source code changes
 
sbt ~reStart
``` 

You can run queries interactively using [graphql-playground](https://github.com/prisma/graphql-playground) by opening [http://localhost:8080](http://localhost:8080) in a browser or query the `/graphql` endpoint directly. The HTTP endpoint follows [GraphQL best practices for handling the HTTP requests](http://graphql.org/learn/serving-over-http/#http-methods-headers-and-body).

### Code structure

This demo contains several packages under `src/main/scala`:

* **common** - common definitions that are used by all examples. It also contains some plumbing (like full HTTP routing) to make more advanced examples more simple.
* **demos** - step by step walkthrough from the most basic examples to more advanced GraphQL servers that use DB, auth, etc.
* **finalServer** - the final server implementation that includes all demonstrated elements
* **model** - defines model for the `Book` and `Author` case classes as well as repositories (including SQL-based implementation)

### GraphQL schema

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