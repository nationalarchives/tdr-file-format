import Dependencies._

ThisBuild / scalaVersion := "2.13.2"
ThisBuild / version := "0.1.2"
ThisBuild / organization := "com.example"
ThisBuild / organizationName := "example"

resolvers ++= Seq[Resolver](
  "Sonatype Releases" at "https://dl.bintray.com/mockito/maven/",
  "TDR Releases" at "s3://tdr-releases-mgmt"
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
      authUtils,
      awsUtils,
      csvParser,
      generatedGraphql,
      graphqlClient,
      scalaTest % Test,
      mockito % Test,
      sqsMock % Test,
      s3Mock % Test
    )
  )

fork in Test := true
javaOptions in Test += s"-Dconfig.file=${sourceDirectory.value}/test/resources/application.conf"

assemblyMergeStrategy in assembly := {
  case PathList("META-INF", xs@_*) => MergeStrategy.discard
  case _ => MergeStrategy.first
}

assemblyJarName in assembly := "file-format.jar"

// See https://www.scala-sbt.org/1.x/docs/Using-Sonatype.html for instructions on how to publish to Sonatype.
