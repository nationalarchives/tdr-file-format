package uk.gov.nationalarchives.fileformat

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.events.SQSEvent
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.Logger
import io.circe.generic.auto._
import io.circe.parser.decode
import software.amazon.awssdk.services.sqs.model.{DeleteMessageResponse, SendMessageResponse}
import uk.gov.nationalarchives.fileformat.FFIDExtractor.FFIDFile

import java.time.Instant
import scala.annotation.unused
import scala.jdk.CollectionConverters._
import scala.language.postfixOps

class Lambda {
  case class FFIDFileWithReceiptHandle(ffidFile: FFIDFile, receiptHandle: String)

  val configFactory: Config = ConfigFactory.load
  val awsUtils = new AWSUtils()
  val lambdaConfig: Map[String, String] =
    awsUtils.decryptValuesFromConfig(List("sqs.queue.input", "sqs.queue.output", "efs.root.location", "command"), Map("LambdaFunctionName" -> configFactory.getString("function.name")))

  val inputQueueUrl: String = lambdaConfig("sqs.queue.input")
  val deleteMessage: String => DeleteMessageResponse = awsUtils.delete(inputQueueUrl, _)
  val sendMessage: String => SendMessageResponse = awsUtils.send(lambdaConfig("sqs.queue.output"), _)
  val ffidExtractor: FFIDExtractor = FFIDExtractor(awsUtils, lambdaConfig)

  val logger: Logger = Logger[Lambda]

  def extractFFID(fileWithHandle: FFIDFileWithReceiptHandle): Either[Throwable, FFIDFileWithReceiptHandle] = {
    ffidExtractor.ffidFile(fileWithHandle.ffidFile)
      .map(_ => fileWithHandle)
  }

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
          s"Lambda complete in $timeTaken seconds for file ID '${f.ffidFile.fileId}' and consignment ID '${f.ffidFile.consignmentId}'"
        )
        f.receiptHandle
      })

    }
  }

  private def handleFailedMessage(e: FailedMessage): Unit = {
    logErrorSummary(e)
    awsUtils.makeMessageVisible(inputQueueUrl, e.receiptHandle)
  }
}

case class FailedMessage(message: String, cause: Throwable, receiptHandle: String)
  extends RuntimeException(message, cause)
