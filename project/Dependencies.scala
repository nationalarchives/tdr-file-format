import sbt._

object Dependencies {
  private val circeVersion = "0.14.14"

  lazy val scalaTest = "org.scalatest" %% "scalatest" % "3.2.19"
  lazy val typesafe = "com.typesafe" % "config" % "1.4.5"
  lazy val s3Sdk = "software.amazon.awssdk" % "s3" % "2.31.47"
  lazy val circeCore = "io.circe" %% "circe-core" % circeVersion
  lazy val circeGeneric = "io.circe" %% "circe-generic" % circeVersion
  lazy val circeParser = "io.circe" %% "circe-parser" % circeVersion
  lazy val s3Utils =  "uk.gov.nationalarchives" %% "s3-utils" % "0.1.278"
  lazy val generatedGraphql =  "uk.gov.nationalarchives" %% "tdr-generated-graphql" % "0.0.431"
  lazy val csvParser = "com.github.tototoshi" %% "scala-csv" % "2.0.0"
  lazy val scalaLogging = "com.typesafe.scala-logging" %% "scala-logging" % "3.9.6"
  lazy val logback = "ch.qos.logback" % "logback-classic" % "1.5.18"
  lazy val logstashLogbackEncoder = "net.logstash.logback" % "logstash-logback-encoder" % "8.1"
  lazy val mockito = "org.mockito" %% "mockito-scala" % "2.0.0"
  lazy val wiremock = "com.github.tomakehurst" % "wiremock" % "3.0.1"
  lazy val droidApi = "uk.gov.nationalarchives" % "droid-api" % "6.9.4"
  lazy val apacheCommons = "org.apache.commons" % "commons-lang3" % "3.18.0"
  lazy val javaxXml =  "org.glassfish.jaxb" % "jaxb-runtime" % "4.0.5"
  lazy val byteBuddy = "net.bytebuddy" % "byte-buddy" % "1.17.7"
}
