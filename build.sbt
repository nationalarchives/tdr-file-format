import Dependencies._

ThisBuild / scalaVersion := "2.13.17"
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
      wiremock % Test,
      byteBuddy % Test
    )
  )

(Test / fork) := true
(Test / javaOptions) += s"-Dconfig.file=${sourceDirectory.value}/test/resources/application.conf"
(Test / envVars) := Map("AWS_ACCESS_KEY_ID" -> "accesskey", "AWS_SECRET_ACCESS_KEY" -> "secret")

(assembly / assemblyMergeStrategy) := {
  //Exclude DROID build transitory dependency signature file
  case filePath if filePath matches(".*container-signature-20[0-9]{6}\\.xml") =>
    CustomMergeStrategy("PreferLocalSignatureOverDependency") { allSignaturesMatchingThePattern =>
      allSignaturesMatchingThePattern.find(_.isProjectDependency) match {
        case Some(selectedSignature) =>
          Right(Vector(Assembly.JarEntry(filePath, selectedSignature.stream)))
        case None =>
          Right(Vector())
      }
    }
  case PathList("META-INF", "MANIFEST.MF") => MergeStrategy.discard
  case _ => MergeStrategy.first
}

(assembly / assemblyJarName) := "file-format.jar"
