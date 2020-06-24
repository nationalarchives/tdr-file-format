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
}
