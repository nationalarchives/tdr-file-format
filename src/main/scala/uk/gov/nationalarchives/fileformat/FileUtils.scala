package uk.gov.nationalarchives.fileformat

import java.io.File
import java.net.URLDecoder
import java.nio.file.Paths
import java.util.UUID

import com.amazonaws.services.lambda.runtime.events.models.s3.S3EventNotification.S3EventNotificationRecord
import com.typesafe.config.ConfigFactory
import graphql.codegen.GetOriginalPath.getOriginalPath.{Data, Variables, document}
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import sttp.client.{HttpURLConnectionBackend, Identity, NothingT, Response, SttpBackend}
import sttp.client.asynchttpclient.WebSocketHandler
import sttp.client.asynchttpclient.future.AsyncHttpClientFutureBackend
import uk.gov.nationalarchives.tdr.error.NotAuthorisedError
import uk.gov.nationalarchives.tdr.{GraphQLClient, GraphQlResponse}
import uk.gov.nationalarchives.tdr.keycloak.KeycloakUtils

import scala.sys.process._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

class FileUtils()(implicit val executionContext: ExecutionContext) {

  def getFilePath(keycloakUtils: KeycloakUtils, client: GraphQLClient[Data, Variables], fileId: UUID)(implicit backend: SttpBackend[Identity, Nothing, NothingT]): Future[Either[String, String]] = {

    val config = ConfigFactory.load
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

    implicit class OptFunctions(opt: Option[String]) {
      def toRightNotEmpty(leftMsg: String): Either[String, String] = if (opt.isEmpty || opt.getOrElse("").isEmpty) Left(leftMsg) else Right(opt.get)
    }

    result.map(_.map(_.getClientFileMetadata.originalPath.toRightNotEmpty("The original path is missing or empty")).flatten)
  }

  def writeFileFromS3(path: String, fileId: UUID, record: S3EventNotificationRecord, s3: S3Client): Either[String, String] = {
    val s3Obj = record.getS3
    val key = s3Obj.getObject.getKey
    val request = GetObjectRequest
      .builder
      .bucket(s3Obj.getBucket.getName)
      .key(URLDecoder.decode(key, "utf-8"))
      .build
    Try{
      s3.getObject(request, Paths.get(path))
      key
    }.toEither.left.map(_.getMessage)
  }

  def output(efsRootLocation: String, consignmentId: UUID, originalPath: String, command: String): String =
    s"$efsRootLocation/$command -json -sig $efsRootLocation/default.sig $efsRootLocation/$consignmentId/$originalPath".!!
}

object FileUtils {
  def apply()(implicit executionContext: ExecutionContext): FileUtils = new FileUtils()(executionContext)
}
