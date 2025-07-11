package uk.gov.nationalarchives.fileformat

import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.Logger
import graphql.codegen.types.{FFIDMetadataInputMatches, FFIDMetadataInputValues}
import net.logstash.logback.argument.StructuredArguments.value
import uk.gov.nationalarchives.droid.internal.api.DroidAPI
import uk.gov.nationalarchives.droid.internal.api.DroidAPI.APIResult
import uk.gov.nationalarchives.fileformat.FFIDExtractor._

import java.net.URI
import java.nio.file.Paths
import java.util.UUID
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success, Try}

class FFIDExtractor(api: DroidAPI, bucketName: String) {
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

  def ffidFile(file: FFIDFile): Either[Throwable, FFIDMetadataInputValues] = {
    Try {
      val s3Bucket = s3BucketOverride(file)
      val s3ObjectPrefix = s3ObjectKeyOverride(file)
      val extension = fileExtension(file.originalPath)
      val droidVersion = api.getDroidVersion
      val containerSignatureVersion = api.getContainerSignatureVersion
      val droidSignatureVersion = api.getBinarySignatureVersion
      val results: List[APIResult] = api.submit(URI.create(s"s3://$s3Bucket/$s3ObjectPrefix"), extension).asScala.toList

      val matches = results.flatMap(_.identificationResults().asScala) match {
        case Nil => List(FFIDMetadataInputMatches(None, "", None, None, None))
        case results => results.map(res => {
          FFIDMetadataInputMatches(Option(res.extension()), res.method().getMethod, Option(res.puid()), Option(res.fileExtensionMismatch()), Option(res.name()))
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
  private val configFactory: Config = ConfigFactory.load
  private val signatureFiles = SignatureFiles()

  case class FFIDFile(consignmentId: UUID, fileId: UUID, originalPath: String, userId: UUID, s3SourceBucket: Option[String] = None, s3SourceBucketKey: Option[String] = None)

  val logger: Logger = Logger[FFIDExtractor]
  private val bucketName = configFactory.getString("s3.bucket")

  def apply(): FFIDExtractor = {
    val api: DroidAPI =
      DroidAPI.builder()
        .containerSignature(signatureFiles.findSignatureFile("container"))
        .binarySignature(signatureFiles.findSignatureFile("droid"))
        .build()
    new FFIDExtractor(api, bucketName)
  }
}
