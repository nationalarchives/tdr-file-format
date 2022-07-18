package uk.gov.nationalarchives.fileformat

import net.logstash.logback.argument.StructuredArguments.value

import java.io.File
import java.util.UUID
import com.typesafe.scalalogging.Logger
import graphql.codegen.types.{FFIDMetadataInput, FFIDMetadataInputMatches}
import io.circe.syntax._
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.sqs.model.SendMessageResponse
import uk.gov.nationalarchives.aws.utils.{S3Utils, SQSUtils}
import uk.gov.nationalarchives.aws.utils.Clients.s3
import uk.gov.nationalarchives.droid.internal.api.DroidAPI
import uk.gov.nationalarchives.fileformat.FFIDExtractor._

import java.nio.file.{Path, Paths}
import scala.jdk.CollectionConverters._
import scala.util.Try

class FFIDExtractor(sqsUtils: SQSUtils, config: Map[String, String]) {
  val sigPath: Path =  Paths.get("DROID_SignatureFile_V104.xml")
  val containerPath: Path = Paths.get("container-signature-20220311.xml")
  val api: DroidAPI = DroidAPI.getInstance(sigPath, containerPath)
  val sendMessage: String => SendMessageResponse = sqsUtils.send(config("sqs.queue.output"), _)
  val logger: Logger = Logger[FFIDExtractor]

  def ffidFile(file: FFIDFile): Either[Throwable, FFIDMetadataInput] = {
    Try {
      val tmpLocation = config("efs.root.location")
      val droidVersion = api.getDroidVersion
      val containerSignatureVersion = api.getContainerSignatureVersion
      val droidSignatureVersion = api.getBinarySignatureVersion
      val results = api.submit(Paths.get(s"$tmpLocation/${file.consignmentId}/${file.originalPath}"))


      val matches = results.getResults.asScala.toList match {
        case Nil => List(FFIDMetadataInputMatches(None, "", None))
        case results => results.map(res => FFIDMetadataInputMatches(Option(res.getExtId), res.getMethod.getMethod, Option(res.getPuid)))
      }

      val metadataInput = FFIDMetadataInput(file.fileId, "Droid", droidVersion, droidSignatureVersion, containerSignatureVersion, "pronom", matches)
      println(metadataInput)
      sendMessage(metadataInput.asJson.noSpaces)
      logger.info(
        "File metadata with {} matches found for file ID {} in consignment ID {}",
        value("matchCount", matches.length),
        value("fileId", file.fileId),
        value("consignmentId", file.consignmentId)
      )
      metadataInput
    }.toEither.left.map(err => {
      logger.error(
        "Error processing file ID {}' in consignment ID {}",
        value("fileId", file.fileId),
        value("consignmentId", file.consignmentId)
      )
      new RuntimeException(s"Error processing file id ${file.fileId} with original path ${file.originalPath}", err)
    })
  }
}

object FFIDExtractor {

  case class FFIDFile(consignmentId: UUID, fileId: UUID, originalPath: String)

  def apply(sqsUtils: SQSUtils, config: Map[String, String]): FFIDExtractor = new FFIDExtractor(sqsUtils, config)
}


