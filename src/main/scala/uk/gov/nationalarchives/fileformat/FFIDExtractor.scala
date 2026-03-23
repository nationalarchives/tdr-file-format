package uk.gov.nationalarchives.fileformat

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.Logger
import graphql.codegen.types.{FFIDMetadataInputMatches, FFIDMetadataInputValues}
import net.logstash.logback.argument.StructuredArguments.value
import uk.gov.nationalarchives.droid.internal.api.DroidAPI
import uk.gov.nationalarchives.droid.internal.api.DroidAPI.APIResult
import uk.gov.nationalarchives.fileformat.FFIDExtractor._
import uk.gov.nationalarchives.fileformat.SignatureFiles.SignatureFileType.{ContainerSignature, DroidSignature}

import java.net.URI
import java.nio.file.Paths
import java.util.UUID
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._

class FFIDExtractor(api: DroidAPI, bucketName: String, timeout: FiniteDuration = FFIDExtractor.defaultTimeout) {
  private def s3BucketOverride(file: FFIDFile): String = file.s3SourceBucket match {
    case Some(v) => v
    case _ => bucketName
  }

  private def s3ObjectKeyOverride(file: FFIDFile): String = file.s3SourceBucketKey match {
    case Some(v) => v
    case _ => s"${file.userId}/${file.consignmentId}/${file.fileId}"
  }

  private def fileExtension(filePath: String): String = {
    Paths.get(filePath).getFileName.toString.split("\\.").last
  }

  private val emptyMatch = List(FFIDMetadataInputMatches(None, "", None, None, None))

  private def identify(file: FFIDFile): IO[FFIDMetadataInputValues] = IO.interruptible {
    val s3Bucket = s3BucketOverride(file)
    val s3ObjectPrefix = s3ObjectKeyOverride(file)
    val extension = fileExtension(file.originalPath)
    val droidVersion = api.getDroidVersion
    val containerSignatureVersion = api.getContainerSignatureVersion
    val droidSignatureVersion = api.getBinarySignatureVersion
    val results: List[APIResult] = api.submit(URI.create(s"s3://$s3Bucket/$s3ObjectPrefix"), extension).asScala.toList

    val matches = results.flatMap(_.identificationResults().asScala) match {
      case Nil => emptyMatch
      case results => results.map(res =>
        FFIDMetadataInputMatches(Option(res.extension()), res.method().getMethod, Option(res.puid()), Option(res.fileExtensionMismatch()), Option(res.name()))
      )
    }

    logger.info(
      "File metadata with {} matches found for file ID {} in consignment ID {}",
      value("matchCount", matches.length),
      value("fileId", file.fileId),
      value("consignmentId", file.consignmentId)
    )
    FFIDMetadataInputValues(file.fileId, "Droid", droidVersion, droidSignatureVersion, containerSignatureVersion, "pronom", matches)
  }

  def ffidFile(file: FFIDFile): Either[Throwable, FFIDMetadataInputValues] = {
    val onTimeout: IO[FFIDMetadataInputValues] = IO {
      logger.warn(
        "Timeout after {} processing file ID {} in consignment ID {}",
        value("timeout", timeout.toString),
        value("fileId", file.fileId),
        value("consignmentId", file.consignmentId)
      )
      FFIDMetadataInputValues(file.fileId, "Droid", "", "", "", "pronom", emptyMatch)
    }

    identify(file)
      .timeoutTo(timeout, onTimeout)
      .handleErrorWith { err =>
        logger.error(
          "Error processing file ID {}' in consignment ID {}", value("fileId", file.fileId),
          value("consignmentId", file.consignmentId)
        )
        IO.raiseError(new RuntimeException(s"Error processing file id ${file.fileId} with original path ${file.originalPath}", err))
      }
      .attempt
      .unsafeRunSync()
  }
}

object FFIDExtractor {
  private val configFactory: Config = ConfigFactory.load
  private val signatureFiles = SignatureFiles()

  case class FFIDFile(consignmentId: UUID, fileId: UUID, originalPath: String, userId: UUID, s3SourceBucket: Option[String] = None, s3SourceBucketKey: Option[String] = None)

  private val logger: Logger = Logger[FFIDExtractor]
  private val bucketName = configFactory.getString("s3.bucket")
  private val defaultTimeout: FiniteDuration = configFactory.getDuration("fileformat.timeout").toMillis.millis

  def apply(): FFIDExtractor = {
    val api: DroidAPI =
      DroidAPI.builder()
        .containerSignature(signatureFiles.findSignatureFile(ContainerSignature))
        .binarySignature(signatureFiles.findSignatureFile(DroidSignature))
        .build()
    new FFIDExtractor(api, bucketName)
  }
}
