import sbt._

object Dependencies {
  private val circeVersion = "0.14.3"
  private val elasticMqVersion = "1.3.14"

  lazy val scalaTest = "org.scalatest" %% "scalatest" % "3.2.14"
  lazy val awsS3 = "software.amazon.awssdk" % "s3" % "2.17.233"
  lazy val typesafe = "com.typesafe" % "config" % "1.4.2"
  lazy val s3Sdk = "software.amazon.awssdk" % "s3" % "2.13.18"
  lazy val circeCore = "io.circe" %% "circe-core" % circeVersion
  lazy val circeGeneric = "io.circe" %% "circe-generic" % circeVersion
  lazy val circeParser = "io.circe" %% "circe-parser" % circeVersion
  lazy val droidCommandLine = "uk.gov.nationalarchives" % "droid-command-line" % "6.5.2"
  lazy val droidResults = "uk.gov.nationalarchives" % "droid-results" % "6.5.2"
  lazy val generatedGraphql =  "uk.gov.nationalarchives" %% "tdr-generated-graphql" % "0.0.281"
  lazy val log4j = "org.apache.logging.log4j" % "log4j-core" % "2.19.0"
  lazy val javaxXml =  "org.glassfish.jaxb" % "jaxb-runtime" % "2.3.1"
  lazy val csvParser = "com.github.tototoshi" %% "scala-csv" % "1.3.10"
  lazy val scalaLogging = "com.typesafe.scala-logging" %% "scala-logging" % "3.9.5"
  lazy val mockito = "org.mockito" %% "mockito-scala" % "1.17.12"
  lazy val elasticMq = "org.elasticmq" %% "elasticmq-server" % elasticMqVersion
  lazy val elasticMqSqs = "org.elasticmq" %% "elasticmq-rest-sqs" % elasticMqVersion
  lazy val wiremock = "com.github.tomakehurst" % "wiremock" % "2.27.2"
  lazy val s3Mock = "io.findify" %% "s3mock" % "0.2.6"
}
