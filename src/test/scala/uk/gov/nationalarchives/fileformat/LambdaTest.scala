package uk.gov.nationalarchives.fileformat

import io.circe.DecodingFailure
import org.scalatest.matchers.should.Matchers.{equal, _}

class LambdaTest extends TestUtils {
  "The process method" should "return an error if the consignment id is invalid" in {
    val exception = intercept[DecodingFailure] {
      new Lambda().process(createEvent(decodeInputJson("ffid_invalid_consignment_id")), null)
    }
    exception.getMessage should equal("DecodingFailure at .consignmentId: Got value '\"1\"' with wrong type, expecting string")
  }

  "The process method" should "return an error if the file id is missing" in {
    mockS3Error()
    val exception = intercept[RuntimeException] {
      new Lambda().process(createEvent(decodeInputJson("ffid_missing_file")), null)
    }
    exception.getMessage should equal("Error processing file id ed66ade1-1984-4f7c-947c-54101148bef0 with original path nonExistentFile")
  }

  "The process method" should "return an error if the original path is missing" in {
    val exception = intercept[DecodingFailure] {
      new Lambda().process(createEvent(decodeInputJson("ffid_event_missing_original_path")), null)
    }
    exception.getMessage should equal("DecodingFailure at .originalPath: Missing required field")
  }

  "The process method" should "return an error if the user id is missing" in {
    val exception = intercept[DecodingFailure] {
      new Lambda().process(createEvent(decodeInputJson("ffid_event_missing_user_id")), null)
    }
    exception.getMessage should equal("DecodingFailure at .userId: Missing required field")
  }
}
