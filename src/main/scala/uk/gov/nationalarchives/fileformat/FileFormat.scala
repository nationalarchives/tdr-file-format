package uk.gov.nationalarchives.fileformat

import java.nio.file.Paths

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.events.SQSEvent
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage
import com.typesafe.config.ConfigFactory
import io.circe
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import uk.gov.nationalarchives.aws.utils.Clients.{s3, sqs}
import uk.gov.nationalarchives.aws.utils.S3EventDecoder._
import uk.gov.nationalarchives.aws.utils.SQSUtils._
import io.circe.parser.decode
import io.circe.generic.auto._
import io.circe.syntax._
import uk.gov.nationalarchives.aws.utils.SQSUtils
import uk.gov.nationalarchives.fileformat.SiegfriedRespsonse.Siegfried

import scala.io.Source.fromResource
import scala.jdk.CollectionConverters._
import scala.language.postfixOps
import scala.sys.process._

/**
 * "siegfried":"1.8.0",
 * "identifiers":[
 * {
 * "name":"pronom",
 * "details":"DROID_SignatureFile_V96.xml; container-signature-20200121.xml"
 * }
 * ]
 * "scandate":"2020-06-24T15:41:16+01:00"
 *
 * "errors": ""
 * "id":"x-fmt/111",
 * "basis":"extension match txt; text match ASCII"
 */

object Test extends App {
  val ff = new FileFormat()
  val event = new SQSEvent()
  val record = new SQSMessage()
  val body = fromResource("test.json").mkString
  record.setBody(body)

  event.setRecords(List(record).asJava)
  val a = ff.update(event, null)
  print(a)
}

class FileFormat {
  //Replace with import graphql input
  case class GraphqlInput()

  def update(event: SQSEvent, context: Context) = {
    val config = ConfigFactory.load
    val sqsUtils = SQSUtils(sqs)
    val sendMessage = sqsUtils.send(config.getString("sqs.queue.output"), _)
    val deleteMessage = sqsUtils.delete(config.getString("sqs.queue.input"), _)
    val eventsWithErrors: EventsWithErrors = decodeS3EventFromSqs(event)
    val siegfriedRespsonseOrError: List[Either[circe.Error, String]] = eventsWithErrors.events.flatMap(event => {
      event.event.getRecords.asScala.toList.map(record => {
        val key = record.getS3.getObject.getKey
        val request = GetObjectRequest
          .builder
          .bucket(record.getS3.getBucket.getName)
          .key(key)
          .build
        val efsRootLocation = config.getString("efs.root.location")
        s3.getObject(request, Paths.get(s"$efsRootLocation/$key"))
        val output: String = s"$efsRootLocation/sf -json -sig $efsRootLocation/default.sig $efsRootLocation/$key".!!
        println(output)
        decode[Siegfried](output).map(s => {
          //This will be the proper graphql input
          val input = GraphqlInput()
          sendMessage(input.asJson.noSpaces)
          event.receiptHandle
        })

      })
    })
    val (fileFormatFailed: List[circe.Error], fileFormatSucceeded: List[String]) = siegfriedRespsonseOrError.partitionMap(identity)

    val allErrors = fileFormatFailed ++ eventsWithErrors.errors

    if(allErrors.nonEmpty) {
      fileFormatSucceeded.foreach(deleteMessage)
      throw new RuntimeException(allErrors.map(_.getMessage).mkString("\n"))
    } else {
      fileFormatSucceeded
    }


  }

}
