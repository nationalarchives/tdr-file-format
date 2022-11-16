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
import net.logstash.logback.argument.StructuredArguments.value
import software.amazon.awssdk.services.sqs.model.{DeleteMessageResponse, SendMessageResponse}
import uk.gov.nationalarchives.aws.utils.Clients.{kms, sqs}
import uk.gov.nationalarchives.aws.utils.{KMSUtils, SQSUtils}
import uk.gov.nationalarchives.droid.command.DroidCommandLine
import uk.gov.nationalarchives.fileformat.FFIDExtractor.FFIDFile

import java.io.{InputStream, OutputStream}
import java.time.Instant
import scala.io.Source
import scala.jdk.CollectionConverters._
import scala.language.postfixOps

class Lambda {

  case class FFIDFileWithReceiptHandle(ffidFile: FFIDFile, receiptHandle: String)

  val configFactory: Config = ConfigFactory.load
  val kmsUtils: KMSUtils = KMSUtils(kms(configFactory.getString("kms.endpoint")), Map("LambdaFunctionName" -> configFactory.getString("function.name")))
  val lambdaConfig: Map[String, String] = kmsUtils.decryptValuesFromConfig(
    List("sqs.queue.input", "sqs.queue.output", "efs.root.location", "command")
  )

  val sqsUtils: SQSUtils = SQSUtils(sqs)

  val inputQueueUrl: String = lambdaConfig("sqs.queue.input")
  val deleteMessage: String => DeleteMessageResponse = sqsUtils.delete(inputQueueUrl, _)
  val sendMessage: String => SendMessageResponse = sqsUtils.send(lambdaConfig("sqs.queue.output"), _)

  val downloadOutput: Decoder[FFIDFile] = deriveDecoder[FFIDFile].map[FFIDFile](identity)

  val logger: Logger = Logger[Lambda]

  def extractFFID(fileWithHandle: FFIDFileWithReceiptHandle): Either[Throwable, FFIDFileWithReceiptHandle] = {
    FFIDExtractor(lambdaConfig).ffidFile(fileWithHandle.ffidFile)
      .map(_ => fileWithHandle)
  }

  def decodeBody(body: String): Option[FFIDFile] = {
    decode[FFIDFile](body).toOption
  }

  def logErrorSummary(error: Throwable): Unit = logger.error("Failed to run file format check", error)

  def process(input: InputStream, output: OutputStream): List[String] = {
//    val body = Source.fromInputStream(input).getLines().mkString
    val a = DroidCommandRunner().createCSV("/home/sam/JSC BTA Bank v Ablyazov 2015 EWHC 3871 (Comm).docx", "/tmp/droid")
    print(a)
//    val file = decodeBody(body).get
//    val a = FFIDExtractor(lambdaConfig).ffidFile(file) match {
//      case Left(value) => throw value
//      case Right(value) => value
//    }
//    print(a)
//    val startTime = Instant.now
//    val (errors, filesWithReceiptHandle) = event.getRecords.asScala.toList
//      .map(decodeBody)
//      .map(_.map(fileWithReceiptHandle =>
//        extractFFID(fileWithReceiptHandle)
//          .left.map(e => FailedMessage(e.getMessage, e, fileWithReceiptHandle.receiptHandle))
//      ).flatten)
//      .partitionMap(identity)
//
//    if(errors.nonEmpty) {
//      filesWithReceiptHandle.foreach(f => deleteMessage(f.receiptHandle))
//      errors.foreach(handleFailedMessage)
//      throw new RuntimeException(errors.map(_.getMessage).mkString("\n"))
//    } else {
//      val timeTaken = java.time.Duration.between(startTime, Instant.now).toMillis.toDouble / 1000
//      filesWithReceiptHandle.map(f => {
//        logger.info(
//          s"Lambda complete in {} seconds for file ID '{}' and consignment ID '{}'",
//          value("timeTaken", timeTaken),
//          value("fileId", f.ffidFile.fileId),
//          value("consignmentId", f.ffidFile.consignmentId)
//        )
//        f.receiptHandle
//      })
//
//    }
    Nil
  }

  private def handleFailedMessage(e: FailedMessage): Unit = {
    logErrorSummary(e)
    sqsUtils.makeMessageVisible(inputQueueUrl, e.receiptHandle)
  }
}

case class FailedMessage(message: String, cause: Throwable, receiptHandle: String)
  extends RuntimeException(message, cause)
