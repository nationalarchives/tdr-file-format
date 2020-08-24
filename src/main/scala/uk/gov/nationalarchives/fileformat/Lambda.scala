package uk.gov.nationalarchives.fileformat

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.events.SQSEvent
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.Logger
import software.amazon.awssdk.services.sqs.model.DeleteMessageResponse
import uk.gov.nationalarchives.aws.utils.Clients.sqs
import uk.gov.nationalarchives.aws.utils.S3EventDecoder._
import uk.gov.nationalarchives.aws.utils.SQSUtils

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.jdk.CollectionConverters._
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

class Lambda {

  val config: Config = ConfigFactory.load
  val sqsUtils: SQSUtils = SQSUtils(sqs)

  val logger = Logger[Lambda]

  val deleteMessage: String => DeleteMessageResponse = sqsUtils.delete(config.getString("sqs.queue.input"), _)

  def process(event: SQSEvent, context: Context): List[String] = {
    val eventsWithErrors: EventsWithErrors = decodeS3EventFromSqs(event)

    val events = eventsWithErrors.events
    val parsingErrors = eventsWithErrors.errors

    val fileUtils = FileUtils()
    val recordProcessor = RecordProcessor(sqsUtils, fileUtils)
    val processingResult: Seq[Future[Try[String]]] = events
          .flatMap(e => e.event.getRecords.asScala
          .map(r => {
            recordProcessor.processRecord(r, e.receiptHandle)
              // Convert all Futures to Future[Try] so that we capture _all_ the errors
              .map(Success(_))
              .recover{ case error => Failure(error) }
          }))

    val receiptHandleOrError: Seq[Try[String]] = Await.result(Future.sequence(processingResult), 1000 seconds)

    val fileFormatSucceeded: Seq[String] = receiptHandleOrError.collect{ case Success(receiptHandle) => receiptHandle }
    val fileFormatError: Seq[Throwable] = receiptHandleOrError.collect{ case Failure(error) => error }

    val allErrors: Seq[Throwable] = fileFormatError ++ parsingErrors

    logErrors(allErrors)

    if (allErrors.nonEmpty) {
      fileFormatSucceeded.foreach(deleteMessage)

      val errorMessages = allErrors.map(_.getMessage)
      throw new RuntimeException(errorMessages.mkString("\n"))
    } else {
      fileFormatSucceeded.toList
    }
  }

  private def logErrors(errors: Seq[Throwable]): Unit = {
    errors.map(error => logger.error("Error processing file", error))
  }
}
