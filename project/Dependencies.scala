import sbt._

object Dependencies {
  private val circeVersion = "0.14.6"

  lazy val scalaTest = "org.scalatest" %% "scalatest" % "3.2.18"
  lazy val typesafe = "com.typesafe" % "config" % "1.4.3"
  lazy val s3Sdk = "software.amazon.awssdk" % "s3" % "2.18.24"
  lazy val circeCore = "io.circe" %% "circe-core" % circeVersion
  lazy val circeGeneric = "io.circe" %% "circe-generic" % circeVersion
  lazy val circeParser = "io.circe" %% "circe-parser" % circeVersion
  lazy val s3Utils =  "uk.gov.nationalarchives" %% "s3-utils" % "0.1.154"
  lazy val generatedGraphql =  "uk.gov.nationalarchives" %% "tdr-generated-graphql" % "0.0.371"
  lazy val csvParser = "com.github.tototoshi" %% "scala-csv" % "1.3.10"
  lazy val scalaLogging = "com.typesafe.scala-logging" %% "scala-logging" % "3.9.5"
  lazy val logback = "ch.qos.logback" % "logback-classic" % "1.5.3"
  lazy val logstashLogbackEncoder = "net.logstash.logback" % "logstash-logback-encoder" % "7.4"
  lazy val mockito = "org.mockito" %% "mockito-scala" % "1.17.30"
  lazy val wiremock = "com.github.tomakehurst" % "wiremock" % "3.0.1"
  lazy val droidApi = "uk.gov.nationalarchives" % "droid-api" % "6.7.0"
  lazy val apacheCommons = "org.apache.commons" % "commons-lang3" % "3.14.0"
  // This is an older version of this dependency but the newer version won't work with Droid without some major changes.
  // Scala Steward configured to ignore it.
  lazy val javaxXml =  "org.glassfish.jaxb" % "jaxb-runtime" % "2.3.7"
}
