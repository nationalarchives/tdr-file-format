package uk.gov.nationalarchives.fileformat

import java.util.UUID

import com.amazonaws.services.lambda.runtime.events.models.s3.S3EventNotification.{S3BucketEntity, S3Entity, S3EventNotificationRecord, S3ObjectEntity}
import graphql.codegen.GetOriginalPath.getOriginalPath.{Data, Variables}
import graphql.codegen.types.{FFIDMetadataInput, FFIDMetadataInputMatches}
import org.mockito.{ArgumentCaptor, MockitoSugar}
import org.mockito.ArgumentMatchers._
import org.scalatest.matchers.should.Matchers._
import org.scalatest.EitherValues
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpec
import software.amazon.awssdk.services.s3.S3Client
import uk.gov.nationalarchives.aws.utils.SQSUtils
import uk.gov.nationalarchives.fileformat.SiegfriedResponse.{Files, Identifiers, Matches, Siegfried}
import uk.gov.nationalarchives.tdr.GraphQLClient
import uk.gov.nationalarchives.tdr.keycloak.KeycloakUtils
import io.circe.generic.auto._
import io.circe.parser.decode
import io.circe.syntax._
import org.scalatest.time.{Millis, Seconds, Span}
import software.amazon.awssdk.services.sqs.model.SendMessageResponse
import sttp.client.{HttpURLConnectionBackend, Identity, NothingT, SttpBackend}

import scala.concurrent.{ExecutionContext, Future}

class RecordProcessorTest extends AnyFlatSpec with MockitoSugar with EitherValues with ScalaFutures {
  implicit val ec: ExecutionContext = ExecutionContext.global

  override implicit def patienceConfig: PatienceConfig = PatienceConfig(timeout = scaled(Span(5, Seconds)), interval = scaled(Span(100, Millis)))
  implicit val backend: SttpBackend[Identity, Nothing, NothingT] = HttpURLConnectionBackend()

  "The processRecord method" should "send a message to the sqs queue" in {
    val sqsUtils = mock[SQSUtils]
    val fileUtils = mock[FileUtils]
    val fileId = UUID.fromString("dd233982-130a-4b71-83ed-44e8c8b1a36c")

    when(fileUtils.getFilePath(any[KeycloakUtils], any[GraphQLClient[Data, Variables]], any[UUID])(any[SttpBackend[Identity, Nothing, NothingT]])).thenReturn(Future(Right("originalPath.txt")))
    when(fileUtils.output(any[String], any[UUID], any[String], any[String], any[UUID])).thenReturn(ffidInput)
    when(fileUtils.writeFileFromS3(any[String], any[UUID], any[S3EventNotificationRecord], any[S3Client])).thenReturn(Right("key"))

    val messageCaptor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])
    when(sqsUtils.send(any[String], messageCaptor.capture())).thenReturn(SendMessageResponse.builder.build())
    val record: S3EventNotificationRecord = s3Record(fileId)
    RecordProcessor(sqsUtils, fileUtils).processRecord(record, "receiptHandle").futureValue

    val expectedMessage = FFIDMetadataInput(fileId, "droid", "droidVersion", "identifier1", "identifier2", "pronom", List(FFIDMetadataInputMatches(Some("txt"), "basis", Some("id")))).asJson.noSpaces

    messageCaptor.getValue should equal(expectedMessage)
  }

  "The processRecord method" should "return the correct reciept handle" in {
    val sqsUtils = mock[SQSUtils]
    val fileUtils = mock[FileUtils]
    val fileId = UUID.randomUUID()
    when(fileUtils.getFilePath(any[KeycloakUtils], any[GraphQLClient[Data, Variables]], any[UUID])(any[SttpBackend[Identity, Nothing, NothingT]])).thenReturn(Future(Right("originalPath.txt")))
    when(fileUtils.output(any[String], any[UUID], any[String], any[String], any[UUID])).thenReturn(ffidInput)
    when(fileUtils.writeFileFromS3(any[String], any[UUID], any[S3EventNotificationRecord], any[S3Client])).thenReturn(Right("key"))

    when(sqsUtils.send(any[String], any[String])).thenReturn(SendMessageResponse.builder.build())
    val record: S3EventNotificationRecord = s3Record(fileId)
    val result: Either[String, String] = RecordProcessor(sqsUtils, fileUtils).processRecord(record, "receiptHandle").futureValue

    result.getOrElse("") should equal("receiptHandle")
  }

  "The processRecord method" should "return an error if there is an error getting the original file path" in {
    val sqsUtils = mock[SQSUtils]
    val fileUtils = mock[FileUtils]
    val fileId = UUID.randomUUID()

    when(fileUtils.getFilePath(any[KeycloakUtils], any[GraphQLClient[Data, Variables]], any[UUID])(any[SttpBackend[Identity, Nothing, NothingT]])).thenReturn(Future(Left("error")))
    when(fileUtils.output(any[String], any[UUID], any[String], any[String], any[UUID])).thenReturn(ffidInput)
    when(fileUtils.writeFileFromS3(any[String], any[UUID], any[S3EventNotificationRecord], any[S3Client])).thenReturn(Right("key"))

    when(sqsUtils.send(any[String], any[String])).thenReturn(SendMessageResponse.builder.build())
    val record: S3EventNotificationRecord = s3Record(fileId)
    val result: Either[String, String] = RecordProcessor(sqsUtils, fileUtils).processRecord(record, "receiptHandle").futureValue

    result.left.value should equal("error")
  }

  "The processRecord method" should "return an error if there is an error writing the file to the file system" in {
    val sqsUtils = mock[SQSUtils]
    val fileUtils = mock[FileUtils]
    val fileId = UUID.randomUUID()
    when(fileUtils.getFilePath(any[KeycloakUtils], any[GraphQLClient[Data, Variables]], any[UUID])(any[SttpBackend[Identity, Nothing, NothingT]])).thenReturn(Future(Right("originalPath.txt")))
    when(fileUtils.output(any[String], any[UUID], any[String], any[String], any[UUID])).thenReturn(ffidInput)
    when(fileUtils.writeFileFromS3(any[String], any[UUID], any[S3EventNotificationRecord], any[S3Client])).thenReturn(Left("error"))

    when(sqsUtils.send(any[String], any[String])).thenReturn(SendMessageResponse.builder.build())
    val record: S3EventNotificationRecord = s3Record(fileId)
    val result: Either[String, String] = RecordProcessor(sqsUtils, fileUtils).processRecord(record, "receiptHandle").futureValue

    result.left.value should equal("error")
  }

  "The processRecord method" should "return an error if the siegfried response is incorrect" in {
    val sqsUtils = mock[SQSUtils]
    val fileUtils = mock[FileUtils]
    val fileId = UUID.randomUUID()

    when(fileUtils.getFilePath(any[KeycloakUtils], any[GraphQLClient[Data, Variables]], any[UUID])(any[SttpBackend[Identity, Nothing, NothingT]])).thenReturn(Future(Right("originalPath.txt")))
    when(fileUtils.output(any[String], any[UUID], any[String], any[String], any[UUID])).thenReturn(Left("error"))
    when(fileUtils.writeFileFromS3(any[String], any[UUID], any[S3EventNotificationRecord], any[S3Client])).thenReturn(Right("key"))

    when(sqsUtils.send(any[String], any[String])).thenReturn(SendMessageResponse.builder.build())
    val record: S3EventNotificationRecord = s3Record(fileId)
    val result: Either[String, String] = RecordProcessor(sqsUtils, fileUtils).processRecord(record, "receiptHandle").futureValue

    result.left.value should equal("error")
  }

  "The processRecord method" should "send the correct file extension where one is provided" in {
    val sqsUtils = mock[SQSUtils]
    val fileUtils = mock[FileUtils]
    val fileId = UUID.randomUUID()
    when(fileUtils.getFilePath(any[KeycloakUtils], any[GraphQLClient[Data, Variables]], any[UUID])(any[SttpBackend[Identity, Nothing, NothingT]])).thenReturn(Future(Right("originalPath.txt")))
    when(fileUtils.output(any[String], any[UUID], any[String], any[String], any[UUID])).thenReturn(ffidInput)
    when(fileUtils.writeFileFromS3(any[String], any[UUID], any[S3EventNotificationRecord], any[S3Client])).thenReturn(Right("key"))

    val messageCaptor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])
    when(sqsUtils.send(any[String], messageCaptor.capture())).thenReturn(SendMessageResponse.builder.build())
    val record: S3EventNotificationRecord = s3Record(fileId)
    RecordProcessor(sqsUtils, fileUtils).processRecord(record, "receiptHandle").futureValue

    decode[FFIDMetadataInput](messageCaptor.getValue).map(input =>
      input.matches.head.extension.get should equal("txt")
    )
  }

  "The processRecord method" should "send an empty file extension where none is provided" in {
    val sqsUtils = mock[SQSUtils]
    val fileUtils = mock[FileUtils]
    val fileId = UUID.randomUUID()
    when(fileUtils.getFilePath(any[KeycloakUtils], any[GraphQLClient[Data, Variables]], any[UUID])(any[SttpBackend[Identity, Nothing, NothingT]])).thenReturn(Future(Right("originalPath")))
    when(fileUtils.output(any[String], any[UUID], any[String], any[String], any[UUID])).thenReturn(ffidInputNoExtension)
    when(fileUtils.writeFileFromS3(any[String], any[UUID], any[S3EventNotificationRecord], any[S3Client])).thenReturn(Right("key"))

    val messageCaptor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])
    when(sqsUtils.send(any[String], messageCaptor.capture())).thenReturn(SendMessageResponse.builder.build())
    val record: S3EventNotificationRecord = s3Record(fileId)
    RecordProcessor(sqsUtils, fileUtils).processRecord(record, "receiptHandle").futureValue

    decode[FFIDMetadataInput](messageCaptor.getValue).map(input =>
      input.matches.head.extension should equal(None)
    )
  }

  "The processRecord method" should "send the correct file versions" in {
    val sqsUtils = mock[SQSUtils]
    val fileUtils = mock[FileUtils]
    val fileId = UUID.randomUUID()
    when(fileUtils.getFilePath(any[KeycloakUtils], any[GraphQLClient[Data, Variables]], any[UUID])(any[SttpBackend[Identity, Nothing, NothingT]])).thenReturn(Future(Right("originalPath")))
    when(fileUtils.output(any[String], any[UUID], any[String], any[String], any[UUID])).thenReturn(ffidInput)
    when(fileUtils.writeFileFromS3(any[String], any[UUID], any[S3EventNotificationRecord], any[S3Client])).thenReturn(Right("key"))

    val messageCaptor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])
    when(sqsUtils.send(any[String], messageCaptor.capture())).thenReturn(SendMessageResponse.builder.build())
    val record: S3EventNotificationRecord = s3Record(fileId)
    RecordProcessor(sqsUtils, fileUtils).processRecord(record, "receiptHandle").futureValue

    decode[FFIDMetadataInput](messageCaptor.getValue).map(input => {
      input.containerSignatureFileVersion should equal("identifier2")
      input.binarySignatureFileVersion should equal("identifier1")
    })
  }

  "The processRecord method" should "send multiple matches if they are returned by siegfried" in {
    val sqsUtils = mock[SQSUtils]
    val fileUtils = mock[FileUtils]
    val fileId = UUID.randomUUID()
    val matches = List(FFIDMetadataInputMatches(Some("txt"), "pronom", Some("fmt/44")), FFIDMetadataInputMatches(Some("pdf"), "pronom", Some("fmt/44")))
    val ffidInput = Right(FFIDMetadataInput(UUID.randomUUID(), "Droid", "1", "1", "1", "pronom", matches))
    when(fileUtils.getFilePath(any[KeycloakUtils], any[GraphQLClient[Data, Variables]], any[UUID])(any[SttpBackend[Identity, Nothing, NothingT]])).thenReturn(Future(Right("originalPath")))
    when(fileUtils.output(any[String], any[UUID], any[String], any[String], any[UUID])).thenReturn(ffidInput)
    when(fileUtils.writeFileFromS3(any[String], any[UUID], any[S3EventNotificationRecord], any[S3Client])).thenReturn(Right("key"))

    val messageCaptor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])
    when(sqsUtils.send(any[String], messageCaptor.capture())).thenReturn(SendMessageResponse.builder.build())
    val record: S3EventNotificationRecord = s3Record(fileId)
    RecordProcessor(sqsUtils, fileUtils).processRecord(record, "receiptHandle").futureValue

    decode[FFIDMetadataInput](messageCaptor.getValue).map(input =>
      input.matches.size should equal(2)
    )
  }

  private def s3Record(fileId: UUID) = {
    val bucket = new S3BucketEntity("bucket", null, "")
    val obj = new S3ObjectEntity(s"${UUID.randomUUID()}/f0a73877-6057-4bbb-a1eb-7c7b73cab586/$fileId", null, null, null, null)
    new S3EventNotificationRecord(null, null, null, null, null, null, null, new S3Entity("", bucket, obj, ""), null)
  }



  private def ffidInput = Right(FFIDMetadataInput(UUID.fromString("dd233982-130a-4b71-83ed-44e8c8b1a36c"), "droid", "droidVersion", "identifier1", "identifier2", "pronom", List(FFIDMetadataInputMatches(Some("txt"), "basis", Some("id")))))
  private def ffidInputNoExtension = Right(FFIDMetadataInput(UUID.fromString("dd233982-130a-4b71-83ed-44e8c8b1a36c"), "droid", "droidVersion", "identifier1", "identifier2", "pronom", List(FFIDMetadataInputMatches(Option.empty, "basis", Some("id")))))
//  private def siegfriedJson = {
//    val identifiers = Identifiers("pronom", "identifier1;identifier2")
//    val matches = Matches("ns", "id", "format", "version", "mime", "basis", "warning")
//    val file = Files("filename", 1.0, "modified", "errors", List(matches))
//    Siegfried("siegfriedVersion", "date", "signature", "created", List(identifiers), List(file))
//  }
}
