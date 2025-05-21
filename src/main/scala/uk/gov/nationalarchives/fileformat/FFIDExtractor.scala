package uk.gov.nationalarchives.fileformat

import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.Logger
import graphql.codegen.types.{FFIDMetadataInputMatches, FFIDMetadataInputValues}
import net.logstash.logback.argument.StructuredArguments.value
import uk.gov.nationalarchives.droid.internal.api.{ApiResult, DroidAPI}
import uk.gov.nationalarchives.fileformat.FFIDExtractor._

import java.net.URI
import java.util.UUID
import scala.jdk.CollectionConverters._
import scala.util.Try

class FFIDExtractor(api: DroidAPI, bucketName: String) {
  private def s3BucketOverride(file: FFIDFile): String = file.s3SourceBucket match {
    case Some(v) => v
    case _ => bucketName
  }

  private def s3ObjectKeyOverride(file: FFIDFile): String = file.s3SourceBucketKey match {
    case Some(v) => v
    case _ => s"${file.userId}/${file.consignmentId}/${file.fileId}"
  }

  def ffidFile(file: FFIDFile): Either[Throwable, FFIDMetadataInputValues] = {
    Try {
      val s3Bucket = s3BucketOverride(file)
      val s3ObjectPrefix = s3ObjectKeyOverride(file)
      val droidVersion = api.getDroidVersion
      val containerSignatureVersion = api.getContainerSignatureVersion
      val droidSignatureVersion = api.getBinarySignatureVersion
      val results: List[ApiResult] = api.submit(URI.create(s"s3://$s3Bucket/$s3ObjectPrefix")).asScala.toList

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
  private val configFactory: Config = ConfigFactory.load

  case class FFIDFile(consignmentId: UUID, fileId: UUID, originalPath: String, userId: UUID, s3SourceBucket: Option[String] = None, s3SourceBucketKey: Option[String] = None)

  val logger: Logger = Logger[FFIDExtractor]
  private val bucketName = configFactory.getString("s3.bucket")

  def apply(api: DroidAPI): FFIDExtractor = {
    new FFIDExtractor(api, bucketName)
  }
}
