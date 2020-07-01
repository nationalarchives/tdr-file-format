package uk.gov.nationalarchives.fileformat

import java.util.UUID

import com.amazonaws.services.lambda.runtime.events.models.s3.S3EventNotification.S3EventNotificationRecord
import com.typesafe.config.{Config, ConfigFactory}
import software.amazon.awssdk.services.sqs.model.SendMessageResponse
import uk.gov.nationalarchives.aws.utils.SQSUtils
import uk.gov.nationalarchives.fileformat.SiegfriedRespsonse.Siegfried
import io.circe.parser.decode
import io.circe.generic.auto._
import io.circe.syntax._
import graphql.codegen.types.{FFIDMetadataInput, FFIDMetadataInputMatches}
import scala.sys.process._

import scala.concurrent.ExecutionContext

class RecordProcessor(sqsUtils: SQSUtils, fileUtils: FileUtils)(implicit val executionContext: ExecutionContext) {
  val config: Config = ConfigFactory.load
  val sendMessage: String => SendMessageResponse = sqsUtils.send(config.getString("sqs.queue.output"), _)

  def processRecord(record: S3EventNotificationRecord, receiptHandle: String) = {
    val efsRootLocation = ConfigFactory.load.getString("efs.root.location")
    val fileId = UUID.fromString(record.getS3.getObject.getKey.split("/").last)
    fileUtils.getFilePath(fileId).map(_.map(
      originalPath => {
        val s3Response: Either[String, String] = fileUtils.writeFileFromS3(s"$efsRootLocation/$originalPath", fileId, record)

        s3Response.map(_ => {
          val output: String = s"$efsRootLocation/sf -json -sig $efsRootLocation/default.sig $efsRootLocation/$originalPath".!!
          decode[Siegfried](output).left.map(err => err.getCause.getMessage)
            .map(s => ffidMetadataInput(fileId, originalPath, s))
            .map(s => sendMessage(s.asJson.noSpaces))
            .map(_ => receiptHandle)
        }).flatten[String, String]
      }
    ).flatten[String, String])
  }

  private def ffidMetadataInput(fileId: UUID, originalPath: String, s: Siegfried) = {
    val identifier = s.identifiers.filter(p => p.name == "pronom").head
    val details = identifier.details.split(";")
    val extension = originalPath.split("\\.").tail.headOption
    val matches: List[FFIDMetadataInputMatches] = s.files.flatMap(f => f.matches.map(m => FFIDMetadataInputMatches(extension, m.basis, Some(m.id))))
    FFIDMetadataInput(fileId, "siegfried", s.siegfried, details(0), details(1), identifier.name, matches)
  }
}

object RecordProcessor {
  def apply(sqsUtils: SQSUtils, fileUtils: FileUtils)(implicit executionContext: ExecutionContext): RecordProcessor = new RecordProcessor(sqsUtils, fileUtils)(executionContext)
}



