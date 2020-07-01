import Dependencies._

ThisBuild / scalaVersion := "2.13.2"
ThisBuild / version := "0.1.2"
ThisBuild / organization := "com.example"
ThisBuild / organizationName := "example"

resolvers ++= Seq[Resolver](
  "Sonatype Releases" at "https://dl.bintray.com/mockito/maven/",
  "TDR Releases" at "s3://tdr-releases-mgmt"
)

lazy val root = (project in file("."))
  .settings(
    name := "file-format",
    libraryDependencies ++= Seq(
      typesafe,
      lambdaJavaCore,
      lambdaJavaEvents,
      circeCore,
      circeGeneric,
      circeParser,
      authUtils,
      awsUtils,
      generatedGraphql,
      graphqlClient,
      scalaTest % Test
    )
  )

// See https://www.scala-sbt.org/1.x/docs/Using-Sonatype.html for instructions on how to publish to Sonatype.
