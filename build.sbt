import Dependencies._

ThisBuild / scalaVersion := "2.13.10"
ThisBuild / version := "0.1.2"
ThisBuild / organization := "com.example"
ThisBuild / organizationName := "example"

resolvers ++= Seq[Resolver](
  "Sonatype Releases" at "https://dl.bintray.com/mockito/maven/",
  "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots",
  Resolver.mavenLocal
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
      awsUtils,
      csvParser,
      generatedGraphql,
      scalaLogging,
      logback,
      logstashLogbackEncoder,
      droidApi,
      javaxXml,
      apacheCommons % Test,
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

(assembly / assemblyMergeStrategy) := {
  case PathList("META-INF", "MANIFEST.MF") => MergeStrategy.discard
  case _ => MergeStrategy.first
}

(assembly / assemblyJarName) := "file-format.jar"
