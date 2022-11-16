import Dependencies._

ThisBuild / scalaVersion := "2.13.10"
ThisBuild / version := "0.1.2"
ThisBuild / organization := "com.example"
ThisBuild / organizationName := "example"

resolvers ++= Seq[Resolver](
  "Sonatype Releases" at "https://dl.bintray.com/mockito/maven/",
  Resolver.mavenLocal
)

lazy val root = (project in file("."))
  .settings(
    name := "file-format",
    libraryDependencies ++= Seq(
      typesafe,
      circeCore,
      circeGeneric,
      circeParser,
      droidCommandLine,
      droidResults,
      csvParser,
      javaxXml,
      generatedGraphql,
      log4j,
      scalaLogging,
      awsS3,
      s3Mock % Test,
      scalaTest % Test,
      mockito % Test,
      elasticMq % Test,
      elasticMqSqs % Test,
      wiremock % Test
    )
  )

(Test / fork) := true
(Test / javaOptions) += s"-Dconfig.file=${sourceDirectory.value}/test/resources/application.conf"
(Test / envVars) := Map("AWS_ACCESS_KEY_ID" -> "accesskey", "AWS_SECRET_ACCESS_KEY" -> "secret")


assembly / assemblyMergeStrategy := {
  case PathList("META-INF", xs@_*) =>
    xs map {
      _.toLowerCase
    } match {
      case "services" :: _ => MergeStrategy.filterDistinctLines
      case "spring.factories" :: _ => MergeStrategy.concat
      case "spring.schemas" :: _ => MergeStrategy.concat
      case "spring.handlers" :: _ => MergeStrategy.concat
      case "spring-jpa.xml" :: _ => MergeStrategy.singleOrError
      case "spring-results.xml" :: _ => MergeStrategy.singleOrError
      case "ui-spring.xml" :: _ => MergeStrategy.singleOrError
      case "spring-signature.xml" :: _ => MergeStrategy.singleOrError
      case "report-spring.xml" :: _ => MergeStrategy.singleOrError
      case "cxf" :: "bus-extensions.txt" :: _ => MergeStrategy.concat("\n\n\n")
      case _ => MergeStrategy.discard
    }
  case "reference.conf" => MergeStrategy.concat
  case _ => MergeStrategy.first
}
(assembly / assemblyJarName) := "file-format.jar"
