package uk.gov.nationalarchives.fileformat

import java.net.URLDecoder
import java.nio.file.Paths
import java.util.UUID

import com.amazonaws.services.lambda.runtime.events.models.s3.S3EventNotification.S3EventNotificationRecord
import com.typesafe.config.ConfigFactory
import graphql.codegen.GetOriginalPath.getOriginalPath.{Data, Variables, document}
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import sttp.client.{Identity, NothingT, SttpBackend}
import uk.gov.nationalarchives.tdr.keycloak.KeycloakUtils
import uk.gov.nationalarchives.tdr.{GraphQLClient, GraphQlResponse}

import scala.concurrent.{ExecutionContext, Future}
import scala.sys.process._
import scala.util.Try

class FileUtils()(implicit val executionContext: ExecutionContext) {

  def getFilePath(keycloakUtils: KeycloakUtils, client: GraphQLClient[Data, Variables], fileId: UUID)(implicit backend: SttpBackend[Identity, Nothing, NothingT]): Future[String] = {

    val config = ConfigFactory.load
    val queryResult: Future[GraphQlResponse[Data]] = for {
      token <- keycloakUtils.serviceAccountToken(config.getString("auth.client.id"), config.getString("auth.client.secret"))
      result <- client.getResult(token, document, Option(Variables(fileId)))
    } yield result

    queryResult.map(graphqlResponse => {
      graphqlResponse.errors match {
        case Nil => {
          val originalPath = graphqlResponse.data.get.getClientFileMetadata.originalPath
          if (originalPath.getOrElse("").isEmpty) {
            throw new RuntimeException(s"The original path for file '$fileId' is missing or empty")
          }
          originalPath.get
        }
        case errors => {
          val errorSummaries = errors.mkString(", ")
          throw new RuntimeException(s"Could not find original path for file '$fileId': $errorSummaries")
        }
      }
    })
  }

  // TODO: Delete fileId param
  def writeFileFromS3(path: String, fileId: UUID, record: S3EventNotificationRecord, s3: S3Client): Try[String] = {
    val s3Obj = record.getS3
    val key = s3Obj.getObject.getKey
    val request = GetObjectRequest
      .builder
      .bucket(s3Obj.getBucket.getName)
      .key(URLDecoder.decode(key, "utf-8"))
      .build
    Try {
      s3.getObject(request, Paths.get(path))
      key
    }
  }

  def output(efsRootLocation: String, consignmentId: UUID, originalPath: String, command: String): String =
    s"$efsRootLocation/$command -json -sig $efsRootLocation/default.sig $efsRootLocation/$consignmentId/$originalPath".!!
}

object FileUtils {
  def apply()(implicit executionContext: ExecutionContext): FileUtils = new FileUtils()(executionContext)
}
