package uk.gov.nationalarchives.fileformat

import java.util.UUID

import com.amazonaws.services.lambda.runtime.events.models.s3.S3EventNotification.S3EventNotificationRecord
import com.typesafe.config.{Config, ConfigFactory}
import graphql.codegen.GetOriginalPath.getOriginalPath.{Data, Variables}
import io.circe.syntax._
import software.amazon.awssdk.services.sqs.model.SendMessageResponse
import sttp.client.{HttpURLConnectionBackend, Identity, NothingT, SttpBackend}
import uk.gov.nationalarchives.aws.utils.Clients.s3
import uk.gov.nationalarchives.aws.utils.SQSUtils
import uk.gov.nationalarchives.tdr.GraphQLClient
import uk.gov.nationalarchives.tdr.keycloak.KeycloakUtils

import scala.concurrent.{ExecutionContext, Future}
import scala.sys.process._
import scala.util.Try

class RecordProcessor(sqsUtils: SQSUtils, fileUtils: FileUtils)(implicit val executionContext: ExecutionContext) {
  val config: Config = ConfigFactory.load
  val sendMessage: String => SendMessageResponse = sqsUtils.send(config.getString("sqs.queue.output"), _)

  def processRecord(record: S3EventNotificationRecord, receiptHandle: String): Future[Either[String, String]] = {
    val efsRootLocation = ConfigFactory.load.getString("efs.root.location")
    val s3KeyArr = record.getS3.getObject.getKey.split("/")
    val fileId = UUID.fromString(s3KeyArr.last)
    val consignmentId = UUID.fromString(s3KeyArr.init.tail(0))
    val keycloakUtils = KeycloakUtils(config.getString("url.auth"))
    val client: GraphQLClient[Data, Variables] = new GraphQLClient[Data, Variables](config.getString("url.api"))
    implicit val backend: SttpBackend[Identity, Nothing, NothingT] = HttpURLConnectionBackend()
    fileUtils.getFilePath(keycloakUtils, client, fileId).map(_.map(
      originalPath => {
        val writeDirectory = originalPath.split("/").init.mkString("/")
        s"mkdir -p $efsRootLocation/$consignmentId/$writeDirectory".!!
        val writePath = s"$efsRootLocation/$consignmentId/$originalPath"
        val s3Response: Either[String, String] = fileUtils.writeFileFromS3(writePath, fileId, record, s3)

        s3Response.map(_ => {
          val ffidInput = fileUtils.output(efsRootLocation, consignmentId, originalPath, config.getString("command"), fileId)
          ffidInput
            .map(s => sendMessage(s.asJson.noSpaces))
            .map(_ => receiptHandle)
        }).flatten[String, String]
      }
    ).flatten[String, String])
  }
}

object RecordProcessor {
  def apply(sqsUtils: SQSUtils, fileUtils: FileUtils)(implicit executionContext: ExecutionContext): RecordProcessor = new RecordProcessor(sqsUtils, fileUtils)(executionContext)
}



