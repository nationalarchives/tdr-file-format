package uk.gov.nationalarchives.fileformat

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.events.SQSEvent
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.Logger
import io.circe.generic.auto._
import io.circe.parser.decode
import net.logstash.logback.argument.StructuredArguments.value
import software.amazon.awssdk.services.sqs.model.{DeleteMessageResponse, SendMessageResponse}
import uk.gov.nationalarchives.aws.utils.Clients.sqs
import uk.gov.nationalarchives.aws.utils.SQSUtils
import uk.gov.nationalarchives.fileformat.FFIDExtractor.FFIDFile

import java.time.Instant
import scala.annotation.unused
import scala.jdk.CollectionConverters._
import scala.language.postfixOps

class Lambda {

  case class FFIDFileWithReceiptHandle(ffidFile: FFIDFile, receiptHandle: String)

  val configFactory: Config = ConfigFactory.load

  val sqsUtils: SQSUtils = SQSUtils(sqs)

  val inputQueueUrl: String = configFactory.getString("sqs.queue.input")
  val deleteMessage: String => DeleteMessageResponse = sqsUtils.delete(inputQueueUrl, _)
  val s3Utils: S3Utils = S3Utils()

  val ffidExtractor: FFIDExtractor = FFIDExtractor(sqsUtils)
  val logger: Logger = Logger[Lambda]

  def extractFFID(fileWithHandle: FFIDFileWithReceiptHandle): Either[Throwable, FFIDFileWithReceiptHandle] = for {
    _ <- s3Utils.downloadFile(fileWithHandle.ffidFile)
    _ <- ffidExtractor.ffidFile(fileWithHandle.ffidFile)
  } yield fileWithHandle

  def decodeBody(record: SQSMessage): Either[FailedMessage, FFIDFileWithReceiptHandle] = {
    decode[FFIDFile](record.getBody)
      .left.map(e => FailedMessage(
        s"Error extracting the file information from the incoming message ${record.getBody}",
        e,
        record.getReceiptHandle)
      )
      .map(ffidFile => FFIDFileWithReceiptHandle(ffidFile, record.getReceiptHandle))
  }

  def logErrorSummary(error: Throwable): Unit = logger.error("Failed to run file format check", error)

  def process(event: SQSEvent, @unused context: Context): List[String] = {
    val startTime = Instant.now
    val (errors, filesWithReceiptHandle) = event.getRecords.asScala.toList
      .map(decodeBody)
      .map(_.map(fileWithReceiptHandle =>
        extractFFID(fileWithReceiptHandle)
          .left.map(e => FailedMessage(e.getMessage, e, fileWithReceiptHandle.receiptHandle))
      ).flatten)
      .partitionMap(identity)

    if(errors.nonEmpty) {
      filesWithReceiptHandle.foreach(f => deleteMessage(f.receiptHandle))
      errors.foreach(handleFailedMessage)
      throw new RuntimeException(errors.map(_.getMessage).mkString("\n"))
    } else {
      val timeTaken = java.time.Duration.between(startTime, Instant.now).toMillis.toDouble / 1000
      filesWithReceiptHandle.map(f => {
        logger.info(
          s"Lambda complete in {} seconds for file ID '{}' and consignment ID '{}'",
          value("timeTaken", timeTaken),
          value("fileId", f.ffidFile.fileId),
          value("consignmentId", f.ffidFile.consignmentId)
        )
        f.receiptHandle
      })

    }
  }

  private def handleFailedMessage(e: FailedMessage): Unit = {
    logErrorSummary(e)
    sqsUtils.makeMessageVisible(inputQueueUrl, e.receiptHandle)
  }
}

case class FailedMessage(message: String, cause: Throwable, receiptHandle: String)
  extends RuntimeException(message, cause)
