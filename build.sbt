import Dependencies._

ThisBuild / scalaVersion := "2.13.16"
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
      s3Utils,
      csvParser,
      generatedGraphql,
      scalaLogging,
      logback,
      logstashLogbackEncoder,
      droidApi,
      javaxXml,
      apacheCommons % Test,
      scalaTest % Test,
      mockito % Test,
      wiremock % Test
    )
  )

dependencyOverrides += "commons-logging" % "commons-logging" % "1.3.5"

(Test / fork) := true
(Test / javaOptions) += s"-Dconfig.file=${sourceDirectory.value}/test/resources/application.conf"
(Test / envVars) := Map("AWS_ACCESS_KEY_ID" -> "accesskey", "AWS_SECRET_ACCESS_KEY" -> "secret")

(assembly / assemblyMergeStrategy) := {
  case PathList("META-INF", "MANIFEST.MF") => MergeStrategy.discard
  case _ => MergeStrategy.first
}

(assembly / assemblyJarName) := "file-format.jar"
