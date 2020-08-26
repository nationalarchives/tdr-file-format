package uk.gov.nationalarchives.fileformat

import java.util.UUID

import graphql.codegen.types.FFIDMetadataInput
import io.circe.parser.decode
import org.scalatest.matchers.should.Matchers.{equal, _}
import uk.gov.nationalarchives.fileformat.AWSUtils._

class LambdaTest extends ExternalServicesTest {

  "The update method" should "put a message in the output queue if the message is successful " in {
    putFile("testfile")
    new Lambda().process(createEvent("sns_s3_event"), null)
    val msgs = outputQueueHelper.receive
    msgs.size should equal(1)
  }

  "The update method" should "put one message in the output queue, delete the successful message and leave the key error message" in {
    putFile("testfile")
    intercept[RuntimeException] {
      new Lambda().process(createEvent("sns_s3_event", "sns_s3_no_key"), null)
    }
    val outputMessages = outputQueueHelper.receive
    val inputMessages = inputQueueHelper.receive
    outputMessages.size should equal(1)
    inputMessages.size should equal(1)
  }


  "The update method" should "leave the queues unchanged if there are no successful messages" in {
    intercept[RuntimeException] {
      new Lambda().process(createEvent("sns_s3_no_key"), null)
    }
    val outputMessages = outputQueueHelper.receive
    val inputMessages = inputQueueHelper.receive
    outputMessages.size should equal(0)
    inputMessages.size should equal(1)
  }

  "The update method" should "return the receipt handle for a successful message" in {
    putFile("testfile")
    val event = createEvent("sns_s3_event")
    val response = new Lambda().process(event, null)
    response(0) should equal(receiptHandle(event.getRecords.get(0).getBody))
  }

  "The update method" should "throw an exception for a no key error" in {
    val event = createEvent("sns_s3_no_key")
    val exception = intercept[RuntimeException] {
      new Lambda().process(event, null)
    }
    exception.getMessage.contains("The resource you requested does not exist (Service: S3, Status Code: 404, Request ID: null, Extended Request ID: null)") should be(true)
  }

  "The update method" should "send the correct output to the queue" in {
    putFile("testfile")
    new Lambda().process(createEvent("sns_s3_event"), null)
    val msgs = outputQueueHelper.receive
    val metadata: FFIDMetadataInput = decode[FFIDMetadataInput](msgs(0).body) match {
      case Right(metadata) => metadata
      case Left(error) => throw error
    }
    metadata.fileId should equal(UUID.fromString("acea5919-25a3-4c6b-8908-fa47cc77878f"))
  }
}
