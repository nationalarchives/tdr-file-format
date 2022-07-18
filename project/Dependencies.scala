import sbt._

object Dependencies {
  private val circeVersion = "0.14.2"
  private val elasticMqVersion = "1.3.9"
  private val scalaCacheVersion = "0.28.0"

  lazy val scalaTest = "org.scalatest" %% "scalatest" % "3.2.13"
  lazy val typesafe = "com.typesafe" % "config" % "1.4.2"
  lazy val s3Sdk = "software.amazon.awssdk" % "s3" % "2.13.18"
  lazy val lambdaJavaCore = "com.amazonaws" % "aws-lambda-java-core" % "1.2.1"
  lazy val lambdaJavaEvents = "com.amazonaws" % "aws-lambda-java-events" % "3.11.0"
  lazy val circeCore = "io.circe" %% "circe-core" % circeVersion
  lazy val circeGeneric = "io.circe" %% "circe-generic" % circeVersion
  lazy val circeParser = "io.circe" %% "circe-parser" % circeVersion
  lazy val awsUtils =  "uk.gov.nationalarchives" %% "tdr-aws-utils" % "0.1.35"
  lazy val generatedGraphql =  "uk.gov.nationalarchives" %% "tdr-generated-graphql" % "0.0.256"
  lazy val csvParser = "com.github.tototoshi" %% "scala-csv" % "1.3.10"
  lazy val scalaLogging = "com.typesafe.scala-logging" %% "scala-logging" % "3.9.5"
  lazy val logback = "ch.qos.logback" % "logback-classic" % "1.2.11"
  lazy val logstashLogbackEncoder = "net.logstash.logback" % "logstash-logback-encoder" % "7.2"
  lazy val mockito = "org.mockito" %% "mockito-scala" % "1.17.12"
  lazy val elasticMq = "org.elasticmq" %% "elasticmq-server" % elasticMqVersion
  lazy val elasticMqSqs = "org.elasticmq" %% "elasticmq-rest-sqs" % elasticMqVersion
  lazy val wiremock = "com.github.tomakehurst" % "wiremock" % "2.27.2"
  lazy val apacheCommons = "org.apache.commons" % "commons-lang3" % "3.12.0"
  lazy val droidApi = "uk.gov.nationalarchives" % "droid-api" % "6.6.0-SNAPSHOT"
  lazy val s3Mock = "io.findify" %% "s3mock" % "0.2.6"
  // This is an older version of this dependency but the newer version won't work with Droid without some major changes.
  // I'll configure Scala Steward to ignore it.
  lazy val javaxXml =  "org.glassfish.jaxb" % "jaxb-runtime" % "2.3.1"
}
