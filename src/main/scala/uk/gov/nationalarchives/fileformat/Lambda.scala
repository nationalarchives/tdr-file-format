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
import uk.gov.nationalarchives.aws.utils.Clients.{kms, sqs}
import uk.gov.nationalarchives.aws.utils.{KMSUtils, SQSUtils}
import uk.gov.nationalarchives.fileformat.FFIDExtractor.FFIDFile

import scala.jdk.CollectionConverters._
import scala.language.postfixOps
import uk.gov.nationalarchives.fileformat.FFIDExtractor._

class Lambda {

  case class FFIDFileWithReceiptHandle(ffidFile: FFIDFile, receiptHandle: String)

  val configFactory: Config = ConfigFactory.load
  val kmsUtils: KMSUtils = KMSUtils(kms(configFactory.getString("kms.endpoint")), Map("LambdaFunctionName" -> configFactory.getString("function.name")))
  val lambdaConfig: Map[String, String] = kmsUtils.decryptValuesFromConfig(
    List("sqs.queue.input", "sqs.queue.output", "efs.root.location", "command")
  )

  val sqsUtils: SQSUtils = SQSUtils(sqs)

  val deleteMessage: String => DeleteMessageResponse = sqsUtils.delete(lambdaConfig("sqs.queue.input"), _)
  val sendMessage: String => SendMessageResponse = sqsUtils.send(lambdaConfig("sqs.queue.output"), _)

  val downloadOutput: Decoder[FFIDFile] = deriveDecoder[FFIDFile].map[FFIDFile](identity)

  val logger: Logger = Logger[Lambda]

  def extractFFID(fileWithHandle: FFIDFileWithReceiptHandle): Either[ErrorSummary, String] = {
    FFIDExtractor(sqsUtils, lambdaConfig).ffidFile(fileWithHandle.ffidFile)
      .map(_ => fileWithHandle.receiptHandle)
  }

  def decodeBody(record: SQSMessage): Either[ErrorSummary, FFIDFileWithReceiptHandle] = {
    decode[FFIDFile](record.getBody)
      .left.map(_.errorSummary(s"Error extracting the file information from the incoming message ${record.getBody}"))
      .map(ffidFile => FFIDFileWithReceiptHandle(ffidFile, record.getReceiptHandle))
  }

  def logErrorSummary(errorSummary: ErrorSummary): Unit = logger.error(errorSummary.message, errorSummary.err)

  def process(event: SQSEvent, context: Context): List[String] = {
    val (errors, receiptHandles) = event.getRecords.asScala.toList
      .map(decodeBody)
      .map(_.map(extractFFID).flatten)
      .partitionMap(identity)

    if(errors.nonEmpty) {
      receiptHandles.foreach(deleteMessage)
      errors.foreach(logErrorSummary)
      throw new RuntimeException(errors.map(_.message).mkString("\n"))
    } else {
      receiptHandles
    }
  }
}
