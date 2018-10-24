name := "sangria-scalaio-demo"
version := "0.1.0-SNAPSHOT"

description := "A demo project used in my scala.io talk."

scalaVersion := "2.12.7"
scalacOptions ++= Seq("-deprecation", "-feature")

mainClass := Some("finalServer.Server")

libraryDependencies ++= Seq(
  "org.sangria-graphql" %% "sangria" % "1.4.2",
  "org.sangria-graphql" %% "sangria-slowlog" % "0.1.8",
  "org.sangria-graphql" %% "sangria-circe" % "1.2.1",

  "com.typesafe.akka" %% "akka-http" % "10.1.3",
  "de.heikoseeberger" %% "akka-http-circe" % "1.21.0",

  "io.circe" %%	"circe-core" % "0.9.3",
  "io.circe" %% "circe-parser" % "0.9.3",
  "io.circe" %% "circe-generic" % "0.9.3",
  "io.circe" %% "circe-optics" % "0.9.3",

  "com.pauldijou" %% "jwt-circe" % "0.19.0",

  "com.typesafe.slick" %% "slick" % "3.2.3",
  "com.h2database" % "h2" % "1.4.197",
  "org.slf4j" % "slf4j-nop" % "1.7.21",

  "org.scalatest" %% "scalatest" % "3.0.5" % Test
)

Revolver.settings
