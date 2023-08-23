package uk.gov.nationalarchives.fileformat

import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.Logger
import graphql.codegen.types.{FFIDMetadataInputMatches, FFIDMetadataInputValues}
import net.logstash.logback.argument.StructuredArguments.value
import uk.gov.nationalarchives.droid.internal.api.{ApiResult, DroidAPI}
import uk.gov.nationalarchives.fileformat.FFIDExtractor._

import java.nio.file.Paths
import java.util.UUID
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success, Try}

class FFIDExtractor(api: DroidAPI, rootDirectory: String) {

  def ffidFile(file: FFIDFile): Either[Throwable, FFIDMetadataInputValues] = {
    Try {
      val outputPath = Paths.get(s"$rootDirectory/${file.originalPath}")
      val droidVersion = api.getDroidVersion
      val containerSignatureVersion = api.getContainerSignatureVersion
      val droidSignatureVersion = api.getBinarySignatureVersion
      val results: List[ApiResult] = api.submit(outputPath).asScala.toList

      val matches = results match {
        case Nil => List(FFIDMetadataInputMatches(None, "", None, None, None))
        case results => results.map(res => {
          FFIDMetadataInputMatches(Option(res.getExtension), res.getMethod.getMethod, Option(res.getPuid), Option(res.isFileExtensionMismatch), Option(res.getName))
        })
      }

      logger.info(
        "File metadata with {} matches found for file ID {} in consignment ID {}",
        value("matchCount", matches.length),
        value("fileId", file.fileId),
        value("consignmentId", file.consignmentId)
      )
      FFIDMetadataInputValues(file.fileId, "Droid", droidVersion, droidSignatureVersion, containerSignatureVersion, "pronom", matches)
    }
      .toEither.left.map(err => {
        err.printStackTrace()
        logger.error(
          "Error processing file ID {}' in consignment ID {}", value("fileId", file.fileId),
          value("consignmentId", file.consignmentId)
        )
        new RuntimeException(s"Error processing file id ${file.fileId} with original path ${file.originalPath}", err)
      })
  }
}

object FFIDExtractor {
  val configFactory: Config = ConfigFactory.load

  case class FFIDFile(consignmentId: UUID, fileId: UUID, originalPath: String, userId: UUID)

  val logger: Logger = Logger[FFIDExtractor]
  val rootDirectory: String = configFactory.getString("root.directory")
  private val signatureFiles = SignatureFiles()

  def apply(): FFIDExtractor = {
    val api: DroidAPI = (for {
      containerPath <- signatureFiles.downloadSignatureFile("container")
      sigPath <- signatureFiles.downloadSignatureFile("droid")
    } yield DroidAPI.getInstance(sigPath, containerPath)) match {
      case Failure(exception) =>
        logger.error("Error getting the droid API", exception)
        throw new RuntimeException(exception.getMessage)
      case Success(api) => api
    }
    new FFIDExtractor(api, rootDirectory)
  }
}
