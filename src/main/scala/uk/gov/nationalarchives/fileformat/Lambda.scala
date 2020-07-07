package uk.gov.nationalarchives.fileformat

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.events.SQSEvent
import com.typesafe.config.{Config, ConfigFactory}
import software.amazon.awssdk.services.sqs.model.DeleteMessageResponse
import uk.gov.nationalarchives.aws.utils.Clients.sqs
import uk.gov.nationalarchives.aws.utils.S3EventDecoder._
import uk.gov.nationalarchives.aws.utils.SQSUtils

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.jdk.CollectionConverters._
import scala.language.postfixOps

class Lambda {

  val config: Config = ConfigFactory.load
  val sqsUtils: SQSUtils = SQSUtils(sqs)

  val deleteMessage: String => DeleteMessageResponse = sqsUtils.delete(config.getString("sqs.queue.input"), _)

  def process(event: SQSEvent, context: Context): List[String] = {
    val eventsWithErrors: EventsWithErrors = decodeS3EventFromSqs(event)
    val fileUtils = FileUtils()
    val recordProcessor = RecordProcessor(sqsUtils, fileUtils)
    val processingResult: List[Future[Either[String, String]]] = eventsWithErrors.events
          .flatMap(e => e.event.getRecords.asScala
          .map(r => recordProcessor.processRecord(r, e.receiptHandle)))

    val receiptHandleOrError = Await.result(Future.sequence(processingResult), 1000 seconds)
    val (fileFormatFailed: List[String], fileFormatSucceeded: List[String]) = receiptHandleOrError.partitionMap(identity)
    val allErrors = fileFormatFailed ++ eventsWithErrors.errors.map(_.getCause.getMessage)
    if (allErrors.nonEmpty) {
      fileFormatSucceeded.foreach(deleteMessage)
      throw new RuntimeException(allErrors.mkString("\n"))
    } else {
      fileFormatSucceeded
    }
  }
}
