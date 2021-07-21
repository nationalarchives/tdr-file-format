package uk.gov.nationalarchives.fileformat

import java.util.UUID

import graphql.codegen.types.FFIDMetadataInput
import io.circe.parser.decode
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers.{equal, _}
import uk.gov.nationalarchives.fileformat.AWSUtils._

class LambdaTest extends AnyFlatSpec with AWSSpec with FileSpec {

  "The update method" should "put a message in the output queue if the message is successful" in {
    new Lambda().process(createEvent("sns_ffid_event"), null)
    outputQueueHelper.availableMessageCount should equal(1)
  }

  "The update method" should "reset the visibility of a message which fails the parsing step so it can be retried" in {
    intercept[RuntimeException] {
      new Lambda().process(createEvent("sns_ffid_invalid_consignment_id"), null)
    }

    inputQueueHelper.availableMessageCount should equal(1)
  }

  "The update method" should "reset the visibility of a message which fails the FFID process so it can be retried" in {
    intercept[RuntimeException] {
      new Lambda().process(createEvent("sns_ffid_missing_file"), null)
    }

    inputQueueHelper.availableMessageCount should equal(1)
  }

  "The update method" should "put one message in the output queue, delete the successful message and leave the error messages" in {
    intercept[RuntimeException] {
      new Lambda().process(createEvent("sns_ffid_missing_file", "sns_ffid_event", "sns_ffid_invalid_consignment_id"), null)
    }

    outputQueueHelper.availableMessageCount should equal(1)
    inputQueueHelper.availableMessageCount should equal(2)
    inputQueueHelper.notVisibleMessageCount should equal(0)
  }

  "The update method" should "return the receipt handle for a successful message" in {
    val event = createEvent("sns_ffid_event")
    val originalReceiptHandle = event.getRecords.get(0).getReceiptHandle

    val response = new Lambda().process(event, null)

    response(0) should equal(originalReceiptHandle)
  }

  "The update method" should "return the receipt handle for a successful message for a file in a nested directory" in {
    val event = createEvent("sns_ffid_nested_directory_event")
    val originalReceiptHandle = event.getRecords.get(0).getReceiptHandle

    val response = new Lambda().process(event, null)

    response(0) should equal(originalReceiptHandle)
  }

  "The update method" should "throw an exception for an invalid consignment id error" in {
    val event = createEvent("sns_ffid_invalid_consignment_id")
    val exception = intercept[RuntimeException] {
      new Lambda().process(event, null)
    }
    exception.getMessage should equal("""Error extracting the file information from the incoming message {"consignmentId":  "1", "fileId":  "acea5919-25a3-4c6b-8908-fa47cc77878f", "originalPath" :  "originalPath"}""")
  }

  "The update method" should "send the correct output to the queue" in {
    new Lambda().process(createEvent("sns_ffid_event"), null)
    val msgs = outputQueueHelper.receive
    val metadata: FFIDMetadataInput = decode[FFIDMetadataInput](msgs(0).body) match {
      case Right(metadata) => metadata
      case Left(error) => throw error
    }
    metadata.fileId should equal(UUID.fromString("acea5919-25a3-4c6b-8908-fa47cc77878f"))
  }

  "The update method" should "send the correct output if the path has spaces" in {
    new Lambda().process(createEvent("sns_ffid_path_with_space_event"), null)
    val msgs = outputQueueHelper.receive
    val metadata: FFIDMetadataInput = decode[FFIDMetadataInput](msgs(0).body) match {
      case Right(metadata) => metadata
      case Left(error) => throw error
    }
    metadata.fileId should equal(UUID.fromString("acea5919-25a3-4c6b-8908-fa47cc77878f"))
  }

  "The update method" should "send the correct output if the path has backticks" in {
    new Lambda().process(createEvent("sns_ffid_path_with_backtick_event"), null)
    val msgs = outputQueueHelper.receive
    val metadata: FFIDMetadataInput = decode[FFIDMetadataInput](msgs(0).body) match {
      case Right(metadata) => metadata
      case Left(error) => throw error
    }
    metadata.fileId should equal(UUID.fromString("acea5919-25a3-4c6b-8908-fa47cc77878f"))
  }
}
