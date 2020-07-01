package uk.gov.nationalarchives.fileformat

import java.nio.file.Paths
import java.util.UUID

import com.amazonaws.services.lambda.runtime.events.models.s3.S3EventNotification.S3EventNotificationRecord
import com.typesafe.config.ConfigFactory
import graphql.codegen.GetOriginalPath.getOriginalPath.{Data, Variables, document}
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import uk.gov.nationalarchives.fileformat.SiegfriedRespsonse.Siegfried
import uk.gov.nationalarchives.tdr.error.NotAuthorisedError
import uk.gov.nationalarchives.tdr.{GraphQLClient, GraphQlResponse}
import uk.gov.nationalarchives.tdr.keycloak.KeycloakUtils

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

class FileUtils(keycloakUtils: KeycloakUtils, s3: S3Client)(implicit val executionContext: ExecutionContext) {

  def getFilePath(fileId: UUID): Future[Either[String, String]] = {
    val config = ConfigFactory.load
    val client = new GraphQLClient[Data, Variables](config.getString("url.api"))
    val queryResult: Future[Either[String, GraphQlResponse[Data]]] = (for {
      token <- keycloakUtils.serviceAccountToken(config.getString("auth.client.id"), config.getString("auth.client.secret"))
      result <- client.getResult(token, document, Option(Variables(fileId)))
    } yield Right(result)) recover(e => {
      Left(e.getMessage)
    })

    val result = queryResult.map {
      case Right(response) => response.errors match {
        case Nil => Right(response.data.get)
        case List(authError: NotAuthorisedError) => Left(authError.message)
        case errors => Left(s"GraphQL response contained errors: ${errors.map(e => e.message).mkString}")
      }
      case Left(e) => Left(e)
    }
    result.map(_.map(_.getClientFileMetadata.originalPath.toRight("No original path")).flatten)
  }

  def writeFileFromS3(path: String, fileId: UUID, record: S3EventNotificationRecord): Either[String, String] = {
    val s3Obj = record.getS3
    val key = s3Obj.getObject.getKey
    val request = GetObjectRequest
      .builder
      .bucket(s3Obj.getBucket.getName)
      .key(key)
      .build
    Try{
      s3.getObject(request, Paths.get(path))
      key
    }.toEither.left.map(_.getMessage)
  }
}

object FileUtils {
  def apply(keycloakUtils: KeycloakUtils, s3Client: S3Client)(implicit executionContext: ExecutionContext): FileUtils = new FileUtils(keycloakUtils, s3Client)(executionContext)
}
