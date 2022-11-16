package uk.gov.nationalarchives.fileformat

import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.Logger
import graphql.codegen.types.{FFIDMetadataInput, FFIDMetadataInputMatches}
import uk.gov.nationalarchives.fileformat.FFIDExtractor._

import java.util.UUID
import scala.util.Try

class FFIDExtractor(commandRunner: DroidCommandRunner, rootDirectory: String) {

  def ffidFile(file: FFIDFile): Either[Throwable, FFIDMetadataInput] = {
      Try {
        val outputPath = s"$rootDirectory/${file.originalPath}"
        val droidVersion = commandRunner.version
        val signatures = commandRunner.signatureVersions
        val containerSignatureVersion = signatures.containerSignatureVersion
        val droidSignatureVersion = signatures.droidSignatureVersion
        val matches: List[FFIDMetadataInputMatches] = commandRunner.getCSVMatches(outputPath, s"$rootDirectory/${file.consignmentId}")

        val metadataInput = FFIDMetadataInput(file.fileId, "Droid", droidVersion, droidSignatureVersion, containerSignatureVersion, "pronom", matches)
        println(metadataInput)
        metadataInput
      }
    .toEither.left.map(err => {
      err.printStackTrace()
      new RuntimeException(s"Error processing file id ${file.fileId} with original path ${file.originalPath}", err)
    })
  }
}

object FFIDExtractor {
  val configFactory: Config = ConfigFactory.load
  case class FFIDFile(consignmentId: UUID, fileId: UUID, originalPath: String, userId: UUID)
  val logger: Logger = Logger[FFIDExtractor]


  def apply(): FFIDExtractor = {
    val commandRunner = DroidCommandRunner()
    new FFIDExtractor(commandRunner, configFactory.getString("root.directory"))
  }
}
