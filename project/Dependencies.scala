import sbt._

object Dependencies {
  private val circeVersion = "0.14.2"
  private val elasticMqVersion = "1.3.9"
  private val awsVersion = "2.17.243"

  lazy val scalaTest = "org.scalatest" %% "scalatest" % "3.2.13"
  lazy val typesafe = "com.typesafe" % "config" % "1.4.2"
  lazy val s3Sdk = "software.amazon.awssdk" % "s3" % awsVersion
  lazy val sqsSdk = "software.amazon.awssdk" % "sqs" % awsVersion
  lazy val kmsSdk = "software.amazon.awssdk" % "kms" % awsVersion
  lazy val lambdaJavaCore = "com.amazonaws" % "aws-lambda-java-core" % "1.2.1"
  lazy val lambdaJavaEvents = "com.amazonaws" % "aws-lambda-java-events" % "3.11.0"
  lazy val circeCore = "io.circe" %% "circe-core" % circeVersion
  lazy val circeGeneric = "io.circe" %% "circe-generic" % circeVersion
  lazy val circeParser = "io.circe" %% "circe-parser" % circeVersion
  lazy val scalaLogging = "com.typesafe.scala-logging" %% "scala-logging" % "3.9.5"
  lazy val mockito = "org.mockito" %% "mockito-scala" % "1.17.12"
  lazy val elasticMq = "org.elasticmq" %% "elasticmq-server" % elasticMqVersion
  lazy val elasticMqSqs = "org.elasticmq" %% "elasticmq-rest-sqs" % elasticMqVersion
  lazy val wiremock = "com.github.tomakehurst" % "wiremock" % "2.27.2"
  lazy val droidApi = "uk.gov.nationalarchives" % "droid-api" % "6.6.0-SNAPSHOT"
}
