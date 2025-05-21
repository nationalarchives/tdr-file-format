package uk.gov.nationalarchives.fileformat

import io.circe.DecodingFailure
import org.scalatest.matchers.should.Matchers.{equal, _}
import org.scalatest.prop.{TableDrivenPropertyChecks, TableFor2}

class LambdaTest extends TestUtils with TableDrivenPropertyChecks {
  "The process method" should "return an error if the consignment id is invalid" in {
    val exception = intercept[DecodingFailure] {
      new Lambda(api).process(createEvent(decodeInputJson("ffid_invalid_consignment_id")), null)
    }
    exception.getMessage should equal("DecodingFailure at .consignmentId: Got value '\"1\"' with wrong type, expecting string")
  }

  "The process method" should "return an error if the file id is missing" in {
    mockS3Error()
    val exception = intercept[RuntimeException] {
      new Lambda(api).process(createEvent(decodeInputJson("ffid_missing_file")), null)
    }
    exception.getMessage should equal("(Service: S3, Status Code: 404, Request ID: null) (SDK Attempt Count: 1)")
  }

  val testFiles: TableFor2[String, List[String]] = Table(
    ("FileName", "ExpectedPuids"),
    ("Test.docx", List("fmt/412")),
    ("Test.xlsx", List("fmt/214")),
    ("Test.pdf", List("fmt/276"))
  )

  forAll(testFiles) { (fileName, expectedPuids) =>
    "The process method" should s"put return the correct format for $fileName" in {
      testValidFileFormatEvent("ffid_event", fileName, expectedPuids)
    }

    "The process method" should s"put return the correct format for $fileName where S3 source bucket and key are overridden" in {
      testValidFileFormatEvent("ffid_event_s3_source_detail", fileName, expectedPuids)
    }

    "The process method" should s"return the correct format for a nested directory for $fileName" in {
      testValidFileFormatEvent("ffid_nested_directory_event", fileName, expectedPuids)
    }

    "The process method" should s"return the correct format for a file with a backtick for $fileName" in {
      testValidFileFormatEvent("ffid_path_with_backtick_event", fileName, expectedPuids)
    }

    "The process method" should s"return the correct format for a file with a space for $fileName" in {
      testValidFileFormatEvent("ffid_path_with_space_event", fileName, expectedPuids)
    }
  }
}
