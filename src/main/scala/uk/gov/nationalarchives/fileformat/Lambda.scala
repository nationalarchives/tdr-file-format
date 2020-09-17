package uk.gov.nationalarchives.fileformat

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.events.SQSEvent
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.Logger
import io.circe.Decoder
import io.circe.generic.auto._
import io.circe.generic.semiauto.deriveDecoder
import io.circe.parser.decode
import software.amazon.awssdk.services.sqs.model.{DeleteMessageResponse, SendMessageResponse}
import uk.gov.nationalarchives.aws.utils.Clients.sqs
import uk.gov.nationalarchives.aws.utils.SQSUtils
import uk.gov.nationalarchives.fileformat.FFIDExtractor.FFIDFile

import scala.jdk.CollectionConverters._
import scala.language.postfixOps
import uk.gov.nationalarchives.fileformat.FFIDExtractor._

class Lambda {

  case class FFIDFileWithReceiptHandle(ffidFile: FFIDFile, receiptHandle: String)

  val config: Config = ConfigFactory.load
  val sqsUtils: SQSUtils = SQSUtils(sqs)

  val deleteMessage: String => DeleteMessageResponse = sqsUtils.delete(config.getString("sqs.queue.input"), _)
  val sendMessage: String => SendMessageResponse = sqsUtils.send(config.getString("sqs.queue.output"), _)

  val downloadOutput: Decoder[FFIDFile] = deriveDecoder[FFIDFile].map[FFIDFile](identity)

  val logger: Logger = Logger[Lambda]

  def process(event: SQSEvent, context: Context): List[String] = {
    def extractFFID(fileWithHandle: FFIDFileWithReceiptHandle): Either[ErrorSummary, String] = {
      FFIDExtractor(sqsUtils, config).ffidFile(fileWithHandle.ffidFile)
        .map(_ => fileWithHandle.receiptHandle)
    }

    def decodeBody(record: SQSMessage): Either[ErrorSummary, FFIDFileWithReceiptHandle] = {
      decode[FFIDFile](record.getBody)
        .left.map(_.errorSummary(s"Error extracting the file information from the incoming message ${record.getBody}"))
        .map(ffidFile => FFIDFileWithReceiptHandle(ffidFile, record.getReceiptHandle))
    }

    val (errors, receiptHandles) = event.getRecords.asScala.toList
      .map(decodeBody)
      .map(_.map(extractFFID).flatten)
      .partitionMap(identity)

    if(errors.nonEmpty) {
      def logErrorSummary(errorSummary: ErrorSummary): Unit = logger.error(errorSummary.message, errorSummary.err)
      receiptHandles.foreach(deleteMessage)
      errors.foreach(logErrorSummary)
      throw new RuntimeException(errors.map(_.message).mkString("\n"))
    } else {
      receiptHandles
    }
  }
}
