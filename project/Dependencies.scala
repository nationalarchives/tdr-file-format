import sbt._

object Dependencies {
  lazy val scalaTest = "org.scalatest" %% "scalatest" % "3.1.1"
  lazy val typesafe = "com.typesafe" % "config" % "1.4.0"
  lazy val s3Sdk = "software.amazon.awssdk" % "s3" % "2.13.18"
  lazy val lambdaJavaCore = "com.amazonaws" % "aws-lambda-java-core" % "1.2.1"
  lazy val lambdaJavaEvents = "com.amazonaws" % "aws-lambda-java-events" % "3.1.0"
  lazy val circeCore = "io.circe" %% "circe-core" % "0.13.0"
  lazy val circeGeneric = "io.circe" %% "circe-generic" % "0.13.0"
  lazy val circeParser = "io.circe" %% "circe-parser" % "0.13.0"
  lazy val awsUtils =  "uk.gov.nationalarchives.aws.utils" %% "tdr-aws-utils" % "0.1.3"
  lazy val authUtils = "uk.gov.nationalarchives" %% "tdr-auth-utils" % "0.0.19"
  lazy val generatedGraphql =  "uk.gov.nationalarchives" %% "tdr-generated-graphql" % "0.0.60"
  lazy val graphqlClient = "uk.gov.nationalarchives" %% "tdr-graphql-client" % "0.0.15"
  lazy val csvParser = "com.github.tototoshi" %% "scala-csv" % "1.3.6"
  lazy val scalaLogging = "com.typesafe.scala-logging" %% "scala-logging" % "3.9.2"
  lazy val logback = "ch.qos.logback" % "logback-classic" % "1.2.3"
  lazy val mockito = "org.mockito" %% "mockito-scala" % "1.14.1"
  lazy val sqsMock = "io.findify" %% "sqsmock" % "0.3.4"
}
