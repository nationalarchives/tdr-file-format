package uk.gov.nationalarchives.fileformat

import java.util.UUID

import scala.sys.process._
import com.amazonaws.services.lambda.runtime.events.models.s3.S3EventNotification.S3EventNotificationRecord
import com.typesafe.config.{Config, ConfigFactory}
import graphql.codegen.GetOriginalPath.getOriginalPath.{Data, Variables}
import software.amazon.awssdk.services.sqs.model.SendMessageResponse
import uk.gov.nationalarchives.aws.utils.SQSUtils
import uk.gov.nationalarchives.fileformat.SiegfriedResponse._
import io.circe.parser.decode
import io.circe.generic.auto._
import io.circe.syntax._
import graphql.codegen.types.{FFIDMetadataInput, FFIDMetadataInputMatches}
import sttp.client.{HttpURLConnectionBackend, Identity, NothingT, SttpBackend}
import uk.gov.nationalarchives.aws.utils.Clients.s3
import uk.gov.nationalarchives.tdr.GraphQLClient
import uk.gov.nationalarchives.tdr.keycloak.KeycloakUtils
import ExceptionUtils._

import scala.concurrent.{ExecutionContext, Future}

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
          val siegfriedOutput = fileUtils.output(efsRootLocation, consignmentId, originalPath, config.getString("command"))
          val decoded = decode[Siegfried](siegfriedOutput)
          decoded.left.map(err => err.stackTrace)
            .map(s => ffidMetadataInput(fileId, originalPath, s))
            .map(s => sendMessage(s.asJson.noSpaces))
            .map(_ => receiptHandle)
        }).flatten[String, String]
      }
    ).flatten[String, String])
  }

  private def ffidMetadataInput(fileId: UUID, originalPath: String, s: Siegfried) = {
    //We only care about pronom results. If there are none then empty string
    val identifierName = s.identifiers.find(p => p.name == "pronom").map(_.name).getOrElse("")
    val details = s.identifiers.find(p => p.name == "pronom").map(_.details.split(";")).getOrElse(Array("", ""))
    val extension = originalPath.split("\\.").tail.headOption
    val matches: List[FFIDMetadataInputMatches] = s.files.flatMap(f => f.matches.map(m => FFIDMetadataInputMatches(extension, m.basis, Some(m.id))))
    FFIDMetadataInput(fileId, "siegfried", s.siegfried, details(0), details(1), identifierName, matches)
  }
}

object RecordProcessor {
  def apply(sqsUtils: SQSUtils, fileUtils: FileUtils)(implicit executionContext: ExecutionContext): RecordProcessor = new RecordProcessor(sqsUtils, fileUtils)(executionContext)
}



