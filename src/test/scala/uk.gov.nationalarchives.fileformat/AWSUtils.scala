package uk.gov.nationalarchives.fileformat

import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.Charset

import com.amazonaws.services.lambda.runtime.events.SQSEvent
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.common.FileSource
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.extension.{Parameters, ResponseDefinitionTransformer}
import com.github.tomakehurst.wiremock.http.{Request, ResponseDefinition}
import io.circe.generic.auto._
import io.circe.parser.decode
import org.elasticmq.rest.sqs.SQSRestServerBuilder
import org.mockito.MockitoSugar
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model._

import scala.io.Source.fromResource
import scala.jdk.CollectionConverters._

object AWSUtils extends MockitoSugar {

  val port = 8001

  val inputQueueName = "testqueueinput"
  val outputQueueName = "testqueueoutput"

  val api = SQSRestServerBuilder.withPort(port).withAWSRegion(Region.EU_WEST_2.toString).start()
  val inputQueueUrl = s"http://localhost:$port/queue/$inputQueueName"
  val outputQueueUrl = s"http://localhost:$port/queue/$outputQueueName"

  val inputQueueHelper: QueueHelper = QueueHelper(inputQueueUrl)
  val outputQueueHelper: QueueHelper = QueueHelper(outputQueueUrl)

  val wiremockKmsEndpoint = new WireMockServer(new WireMockConfiguration().port(9003).extensions(new ResponseDefinitionTransformer {
    override def transform(request: Request, responseDefinition: ResponseDefinition, files: FileSource, parameters: Parameters): ResponseDefinition = {
      case class KMSRequest(CiphertextBlob: String)
      decode[KMSRequest](request.getBodyAsString) match {
        case Left(err) => throw err
        case Right(req) =>
          val charset = Charset.defaultCharset()
          val plainText = charset.newDecoder.decode(ByteBuffer.wrap(req.CiphertextBlob.getBytes(charset))).toString
          ResponseDefinitionBuilder
            .like(responseDefinition)
            .withBody(s"""{"Plaintext": "$plainText"}""")
            .build()
      }
    }
    override def getName: String = ""
  }))

  def createEvent(locations: String*): SQSEvent = {
    val event = new SQSEvent()

    val records = locations.map(location => {
      val record = new SQSMessage()
      val body = fromResource(s"json/$location.json").mkString
      record.setBody(body)
      val sendResponse = inputQueueHelper.send(body)
      record.setMessageId(sendResponse.messageId)
      record
    })

    val inputQueueMessages = inputQueueHelper.receive
    records.foreach(record => {
      val receiptHandle = inputQueueMessages.filter(_.messageId == record.getMessageId).head.receiptHandle
      record.setReceiptHandle(receiptHandle)
    })

    event.setRecords(records.asJava)
    event
  }

  case class QueueHelper(queueUrl: String) {
    val sqsClient: SqsClient = SqsClient.builder()
      .region(Region.EU_WEST_2)
      .endpointOverride(URI.create("http://localhost:8001"))
      .build()

    def send(body: String): SendMessageResponse = sqsClient.sendMessage(SendMessageRequest
      .builder.messageBody(body).queueUrl(queueUrl).build())

    def receive: List[Message] = sqsClient.receiveMessage(ReceiveMessageRequest
      .builder
      .maxNumberOfMessages(10)
      .queueUrl(queueUrl)
      .build).messages.asScala.toList

    def createQueue: CreateQueueResponse = sqsClient.createQueue(CreateQueueRequest.builder.queueName(queueUrl.split("/")(4)).build())

    def deleteQueue: DeleteQueueResponse = sqsClient.deleteQueue(DeleteQueueRequest.builder.queueUrl(queueUrl).build)

    def delete(msg: Message): DeleteMessageResponse = sqsClient.deleteMessage(DeleteMessageRequest
      .builder.queueUrl(queueUrl).receiptHandle(msg.receiptHandle()).build)

    def availableMessageCount: Int = attribute(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES).toInt

    def notVisibleMessageCount: Int = attribute(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE).toInt

    private def attribute(name: QueueAttributeName): String = sqsClient
      .getQueueAttributes(
        GetQueueAttributesRequest
          .builder
          .queueUrl(queueUrl)
          .attributeNames(name)
          .build
      ).attributes.get(name)
  }
}
