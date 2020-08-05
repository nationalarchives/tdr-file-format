package uk.gov.nationalarchives.fileformat

import java.nio.file.Path
import java.util.UUID

import com.amazonaws.services.lambda.runtime.events.models.s3.S3EventNotification.{S3BucketEntity, S3Entity, S3EventNotificationRecord, S3ObjectEntity}
import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import com.typesafe.config.ConfigFactory
import graphql.codegen.GetOriginalPath.getOriginalPath.{Data, GetClientFileMetadata, Variables, document}
import org.mockito.ArgumentMatchers._
import org.mockito.{ArgumentCaptor, MockitoSugar}
import org.scalatest.EitherValues
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers._
import sangria.ast.Document
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.{GetObjectRequest, GetObjectResponse}
import sttp.client.asynchttpclient.WebSocketHandler
import sttp.client.asynchttpclient.future.AsyncHttpClientFutureBackend
import sttp.client.{HttpError, HttpURLConnectionBackend, Identity, NothingT, Response, SttpBackend}
import sttp.model.StatusCode
import uk.gov.nationalarchives.tdr.GraphQLClient.Extensions
import uk.gov.nationalarchives.tdr.error.{GraphQlError, HttpException}
import uk.gov.nationalarchives.tdr.keycloak.KeycloakUtils
import uk.gov.nationalarchives.tdr.{GraphQLClient, GraphQlResponse}

import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag

class FileUtilsTest extends AnyFlatSpec with MockitoSugar with EitherValues with ScalaFutures {
  implicit val ec: ExecutionContext = ExecutionContext.global
  implicit val backend: SttpBackend[Identity, Nothing, NothingT] = HttpURLConnectionBackend()

  "The getFilePath method" should "request a service account token" in {
    val client = mock[GraphQLClient[Data, Variables]]
    val keycloakUtils = mock[KeycloakUtils]

    when(keycloakUtils.serviceAccountToken[Identity](any[String], any[String])(any[SttpBackend[Identity, Nothing, NothingT]], any[ClassTag[Identity[_]]]))
      .thenReturn(Future.successful(new BearerAccessToken("token")))
    when(client.getResult[Identity](any[BearerAccessToken], any[Document], any[Option[Variables]])(any[SttpBackend[Identity, Nothing, NothingT]], any[ClassTag[Identity[_]]]))
      .thenReturn(Future.successful(GraphQlResponse(Some(Data(GetClientFileMetadata(Some("originalPath")))), List())))

    val fileUtils = FileUtils()
    fileUtils.getFilePath(keycloakUtils, client, UUID.randomUUID()).futureValue

    val configFactory = ConfigFactory.load
    val expectedId = configFactory.getString("auth.client.id")
    val expectedSecret = configFactory.getString("auth.client.secret")

    verify(keycloakUtils).serviceAccountToken(expectedId, expectedSecret)
  }

  "The getFilePath method" should "call the graphql api with the correct data" in {
    val client = mock[GraphQLClient[Data, Variables]]
    val keycloakUtils = mock[KeycloakUtils]
    val uuid = UUID.randomUUID()

    val variables = Variables(uuid)

    when(keycloakUtils.serviceAccountToken[Identity](any[String], any[String])(any[SttpBackend[Identity, Nothing, NothingT]], any[ClassTag[Identity[_]]]))
      .thenReturn(Future.successful(new BearerAccessToken("token")))
    when(client.getResult[Identity](any[BearerAccessToken], any[Document], any[Option[Variables]])(any[SttpBackend[Identity, Nothing, NothingT]], any[ClassTag[Identity[_]]]))
      .thenReturn(Future.successful(GraphQlResponse(Some(Data(GetClientFileMetadata(Some("originalPath")))), List())))

    val fileUtils = FileUtils()
    fileUtils.getFilePath(keycloakUtils, client, uuid).futureValue

    verify(client).getResult(new BearerAccessToken("token"), document, Some(variables))
  }

  "The getFilePath method" should "return an error if the original file path is empty" in {
    val client = mock[GraphQLClient[Data, Variables]]
    val keycloakUtils = mock[KeycloakUtils]
    val uuid = UUID.randomUUID()

    when(keycloakUtils.serviceAccountToken[Identity](any[String], any[String])(any[SttpBackend[Identity, Nothing, NothingT]], any[ClassTag[Identity[_]]]))
      .thenReturn(Future.successful(new BearerAccessToken("token")))
    when(client.getResult[Identity](any[BearerAccessToken], any[Document], any[Option[Variables]])(any[SttpBackend[Identity, Nothing, NothingT]], any[ClassTag[Identity[_]]]))
      .thenReturn(Future.successful(GraphQlResponse(Some(Data(GetClientFileMetadata(Some("")))), List())))

    val fileUtils = FileUtils()
    val result: Either[String, String] = fileUtils.getFilePath(keycloakUtils, client, uuid).futureValue
    result.left.value should equal("The original path is missing or empty")

  }

  "The getFilePath method" should "return the original file path" in {
    val client = mock[GraphQLClient[Data, Variables]]
    val keycloakUtils = mock[KeycloakUtils]
    val uuid = UUID.randomUUID()

    when(keycloakUtils.serviceAccountToken[Identity](any[String], any[String])(any[SttpBackend[Identity, Nothing, NothingT]], any[ClassTag[Identity[_]]]))
      .thenReturn(Future.successful(new BearerAccessToken("token")))
    when(client.getResult[Identity](any[BearerAccessToken], any[Document], any[Option[Variables]])(any[SttpBackend[Identity, Nothing, NothingT]], any[ClassTag[Identity[_]]]))
      .thenReturn(Future.successful(GraphQlResponse(Some(Data(GetClientFileMetadata(Some("originalPath")))), List())))

    val fileUtils = FileUtils()
    val result: Either[String, String] = fileUtils.getFilePath(keycloakUtils, client, uuid).futureValue
    result.map(r => r should equal("originalPath"))

  }

  "The getFilePath method" should "error if the auth server is unavailable" in {
    val client = mock[GraphQLClient[Data, Variables]]
    val keycloakUtils = mock[KeycloakUtils]

    when(keycloakUtils.serviceAccountToken[Identity](any[String], any[String])(any[SttpBackend[Identity, Nothing, NothingT]], any[ClassTag[Identity[_]]]))
      .thenThrow(HttpError("An error occurred contacting the auth server"))
    when(client.getResult[Identity](any[BearerAccessToken], any[Document], any[Option[Variables]])(any[SttpBackend[Identity, Nothing, NothingT]], any[ClassTag[Identity[_]]]))
      .thenReturn(Future.successful(GraphQlResponse(Some(Data(GetClientFileMetadata(Some("originalPath")))), List())))

    val exception = intercept[HttpError] {
      FileUtils().getFilePath(keycloakUtils, client, UUID.randomUUID())
    }
    exception.body should equal("An error occurred contacting the auth server")
  }

  "The getFilePath method" should "error if the graphql server is unavailable" in {
    val client = mock[GraphQLClient[Data, Variables]]
    val keycloakUtils = mock[KeycloakUtils]
    val uuid = UUID.randomUUID()

    val body: Either[String, String] = Left("Graphql error")

    val response = Response(body, StatusCode.ServiceUnavailable)

    when(keycloakUtils.serviceAccountToken[Identity](any[String], any[String])(any[SttpBackend[Identity, Nothing, NothingT]], any[ClassTag[Identity[_]]]))
      .thenReturn(Future.successful(new BearerAccessToken("token")))
    when(client.getResult[Identity](any[BearerAccessToken], any[Document], any[Option[Variables]])(any[SttpBackend[Identity, Nothing, NothingT]], any[ClassTag[Identity[_]]])).thenThrow(new HttpException(response))

    val res: Either[String, String] = FileUtils().getFilePath(keycloakUtils, client, uuid).futureValue
    res.left.value shouldEqual "Unexpected response from GraphQL API: Response(Left(Graphql error),503,,List(),List())"
  }

  "The getFilePath method" should "error if the graphql query returns not authorised errors" in {
    val client = mock[GraphQLClient[Data, Variables]]
    val keycloakUtils = mock[KeycloakUtils]

    val uuid = UUID.randomUUID()

    when(keycloakUtils.serviceAccountToken[Identity](any[String], any[String])(any[SttpBackend[Identity, Nothing, NothingT]], any[ClassTag[Identity[_]]]))
      .thenReturn(Future.successful(new BearerAccessToken("token")))
    val graphqlResponse: GraphQlResponse[Data] =
      GraphQlResponse(Option.empty, List(GraphQlError(GraphQLClient.Error("Not authorised message",
        List(), List(), Some(Extensions(Some("NOT_AUTHORISED")))))))
    when(client.getResult[Identity](any[BearerAccessToken], any[Document], any[Option[Variables]])(any[SttpBackend[Identity, Nothing, NothingT]], any[ClassTag[Identity[_]]]))
      .thenReturn(Future.successful(graphqlResponse))

    val res = FileUtils().getFilePath(keycloakUtils, client, uuid).futureValue

    res.left.value shouldEqual "Not authorised message"
  }

  "The getFilePath method" should "error if the graphql query returns a general error" in {
    val client = mock[GraphQLClient[Data, Variables]]
    val keycloakUtils = mock[KeycloakUtils]

    when(keycloakUtils.serviceAccountToken[Identity](any[String], any[String])(any[SttpBackend[Identity, Nothing, NothingT]], any[ClassTag[Identity[_]]]))
      .thenReturn(Future.successful(new BearerAccessToken("token")))
    val graphqlResponse: GraphQlResponse[Data] =
      GraphQlResponse(Option.empty, List(GraphQlError(GraphQLClient.Error("General error",
        List(), List(), Option.empty))))
    when(client.getResult[Identity](any[BearerAccessToken], any[Document], any[Option[Variables]])(any[SttpBackend[Identity, Nothing, NothingT]], any[ClassTag[Identity[_]]]))
      .thenReturn(Future.successful(graphqlResponse))

    val res = FileUtils().getFilePath(keycloakUtils, client, UUID.randomUUID()).futureValue
    res.left.value shouldEqual "GraphQL response contained errors: General error"
  }

  "The writeFileFromS3 method" should "write the file to the specified path" in {
    val s3Client = mock[S3Client]
    val requestCaptor: ArgumentCaptor[GetObjectRequest] = ArgumentCaptor.forClass(classOf[GetObjectRequest])
    val pathCaptor: ArgumentCaptor[Path] = ArgumentCaptor.forClass(classOf[Path])
    val mockResponse = GetObjectResponse.builder.build()
    val path = "path"
    val fileId = UUID.randomUUID()
    val bucket = new S3BucketEntity("bucket", null, "")
    val obj = new S3ObjectEntity("key", null, null, null, null)
    val record = new S3EventNotificationRecord(null, null, null, null, null, null, null, new S3Entity("", bucket, obj, ""), null)
    when(s3Client.getObject(requestCaptor.capture(), pathCaptor.capture())).thenReturn(mockResponse)
    FileUtils().writeFileFromS3(path, fileId, record, s3Client)

    val requestArg = requestCaptor.getValue
    requestArg.bucket should equal("bucket")
    requestArg.key should equal("key")
    pathCaptor.getValue.toString should equal("path")
  }

  "The writeFileFromS3 method" should "return an error if there is an error writing the file" in {
    val s3Client = mock[S3Client]
    val path = "path"
    val fileId = UUID.randomUUID()
    val bucket = new S3BucketEntity("bucket", null, "")
    val obj = new S3ObjectEntity("key", null, null, null, null)
    val record = new S3EventNotificationRecord(null, null, null, null, null, null, null, new S3Entity("", bucket, obj, ""), null)
    when(s3Client.getObject(any[GetObjectRequest], any[Path])).thenThrow(new RuntimeException("error"))
    val response = FileUtils().writeFileFromS3(path, fileId, record, s3Client)
    response.left.value should equal("error")
  }
}
