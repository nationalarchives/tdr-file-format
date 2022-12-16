package uk.gov.nationalarchives.fileformat

import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.Logger
import graphql.codegen.types.{FFIDMetadataInput, FFIDMetadataInputMatches}
import net.logstash.logback.argument.StructuredArguments.value
import uk.gov.nationalarchives.droid.internal.api.{ApiResult, DroidAPI}
import uk.gov.nationalarchives.fileformat.FFIDExtractor._

import java.io.{File, InputStream}
import java.net.URL
import java.nio.file.{Files, Path, Paths, StandardCopyOption}
import java.util.UUID
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success, Try}

class FFIDExtractor(api: DroidAPI, rootDirectory: String) {

  def ffidFile(file: FFIDFile): Either[Throwable, FFIDMetadataInput] = {
      Try {
        val outputPath = Paths.get(s"$rootDirectory/${file.originalPath}")
        val droidVersion = api.getDroidVersion
        val containerSignatureVersion = api.getContainerSignatureVersion
        val droidSignatureVersion = api.getBinarySignatureVersion
        val results: List[ApiResult] = api.submit(outputPath).asScala.toList

        val matches = results match {
          case Nil => List(FFIDMetadataInputMatches(None, "", None))
          case results => results.map(res => FFIDMetadataInputMatches(Option(res.getExtension), res.getMethod.getMethod, Option(res.getPuid)))
        }

        val metadataInput = FFIDMetadataInput(file.fileId, "Droid", droidVersion, droidSignatureVersion, containerSignatureVersion, "pronom", matches)

        logger.info(
          "File metadata with {} matches found for file ID {} in consignment ID {}",
          value("matchCount", matches.length),
          value("fileId", file.fileId),
          value("consignmentId", file.consignmentId)
        )
        metadataInput
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


  def apply(): FFIDExtractor = {
    val rootDirectory: String = configFactory.getString("root.directory")
    val containerSignatureVersion: String = configFactory.getString("signatures.container")
    val droidSignatureVersion: String = configFactory.getString("signatures.droid")

    def downloadSignatureFiles(fileName: String): Try[Path] = Try {
      val cdnUrl = configFactory.getString("signatures.cdn")
      val path = Paths.get(s"$rootDirectory/$fileName")
      val existingFile = new File(path.toString)
      if(!existingFile.exists()) {
        logger.debug("Downloading signature files")
        val in: InputStream = new URL(s"$cdnUrl/$fileName").openStream
        Files.copy(in, path, StandardCopyOption.REPLACE_EXISTING)
        path
      } else {
        path
      }
    }

    val api: DroidAPI = (for {
      sigPath <- downloadSignatureFiles(s"DROID_SignatureFile_$droidSignatureVersion.xml")
      containerPath <- downloadSignatureFiles(s"container-signature-$containerSignatureVersion.xml")

    } yield DroidAPI.getInstance(sigPath, containerPath)) match {
      case Failure(exception) =>
        logger.error("Error getting the droid API", exception)
        throw new RuntimeException(exception.getMessage)
      case Success(api) => api
    }

    new FFIDExtractor(api, rootDirectory)
  }
}
