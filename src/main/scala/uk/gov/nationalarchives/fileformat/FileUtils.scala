package uk.gov.nationalarchives.fileformat

import java.io.File
import java.net.URLDecoder
import java.nio.file.Paths
import java.util.UUID

import com.amazonaws.services.lambda.runtime.events.models.s3.S3EventNotification.S3EventNotificationRecord
import com.typesafe.config.ConfigFactory
import graphql.codegen.GetOriginalPath.getOriginalPath.{Data, Variables, document}
import graphql.codegen.types.{FFIDMetadataInput, FFIDMetadataInputMatches}
import io.circe
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
import scala.io.Source
import scala.util.Try
import com.github.tototoshi.csv._

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
    implicit class OptFunction(strOpt: Option[String]) {
      def toOptEmpty: Option[String] = if(strOpt.getOrElse("").isEmpty) Option.empty else strOpt
    }
    result.map(_.map(_.getClientFileMetadata.originalPath.toOptEmpty.toRight("The original path is missing or empty")).flatten)
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

  private def ffidInput(efsRootLocation: String, consignmentId: UUID, originalPath: String, command: String, fileId: UUID) = {
    case class DroidOutput(id: String,parentId: String, uri: String, filePath: String, name: String, method: String, status: String, size: String, resultType: String, ext: String, lastModified: String, extensionMismatch: String, hash: String, formatCount: String, puid: String, mimeType: String, formatName: String, formatVersion: String)
    val droidVersion = s"$efsRootLocation/$command -v".!!.split("\n")(1)
    val signatureOutput = s"$efsRootLocation/$command -x".!!.split("\n")
    val containerSignatureVersion = signatureOutput(1).split(" ").last
    val droidSignatureVersion = signatureOutput(2).split(" ").last
    s"$efsRootLocation/$command -a  $efsRootLocation/$consignmentId/$originalPath -p result.droid".!
    s"$efsRootLocation/$command -p result.droid -E result.csv".!
    val reader = CSVReader.open(new File("result.csv"))
    implicit class OptFunction(str: String) {
      def toOpt: Option[String] = if (str.isEmpty) Option.empty else Some(str)
    }
    val readerResult: List[List[String]] = reader.all.tail
    val filteredResult = readerResult.filter(o => o.length > 1 && o(1).isEmpty)
    val matches: List[FFIDMetadataInputMatches] = filteredResult.map(o => FFIDMetadataInputMatches(o(9).toOpt, o(5), o(14).toOpt))

    FFIDMetadataInput(fileId, "Droid", droidVersion, droidSignatureVersion, containerSignatureVersion, "pronom", matches)
  }

  def output(efsRootLocation: String, consignmentId: UUID, originalPath: String, command: String, fileId: UUID): Either[String, FFIDMetadataInput] = {
    Try(ffidInput(efsRootLocation, consignmentId, originalPath, command, fileId)).toEither.left.map(_.getMessage)
  }
}

object FileUtils {
  def apply()(implicit executionContext: ExecutionContext): FileUtils = new FileUtils()(executionContext)
}
