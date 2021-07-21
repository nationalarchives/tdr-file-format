package uk.gov.nationalarchives.fileformat

import java.util.UUID

import org.mockito.MockitoSugar
import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers._
import uk.gov.nationalarchives.aws.utils.SQSUtils
import uk.gov.nationalarchives.fileformat.FFIDExtractor.FFIDFile

class FFIDExtractorTest extends AnyFlatSpec with FileSpec with MockitoSugar with EitherValues {

  "The ffid method" should "return the correct droid and signature version" in {
    val result = FFIDExtractor(sqsUtils, config("result_some_parent_ids")).ffidFile(ffidFile)

    val ffid = result.right.value
    ffid.softwareVersion should equal("6.5")
    ffid.containerSignatureFileVersion should equal("container-signature-20200121.xml")
  }

  "The ffid method" should "filter out any entries with parent ids" in {
    val result = FFIDExtractor(sqsUtils, config("result_some_parent_ids")).ffidFile(ffidFile)
    val ffid = result.right.value
    ffid.matches.size should equal(1)
  }

  "The ffid method" should "return the correct value if the extension and puid are empty" in {
    val result = FFIDExtractor(sqsUtils, config("result_empty_ext_and_puid")).ffidFile(ffidFile)
    val ffid = result.right.value
    val m = ffid.matches.head
    m.extension.isEmpty should be(true)
    m.puid.isEmpty should be(true)
  }

  "The ffid method" should "return more than one result for multiple result rows" in {
    val result = FFIDExtractor(sqsUtils, config("result_multiple_rows")).ffidFile(ffidFile)
    val ffid = result.right.value
    ffid.matches.size should equal(3)
  }

  "The ffid method" should "return an error if there is an error running the droid commands" in {
    val result = FFIDExtractor(sqsUtils, config("invalid_command")).ffidFile(ffidFile)
    result.left.value.getMessage should equal("Error processing file id acea5919-25a3-4c6b-8908-fa47cc77878f with original path originalPath")
    result.left.value.getCause.getMessage should equal("Nonzero exit value: 1")
  }

  "The ffid method" should "return a correct value if there are quotes in the filename" in {
    val result = FFIDExtractor(sqsUtils, config("result_one_record")).ffidFile(ffidFileWithQuote)
    result.isRight should be (true)
  }

  def sqsUtils: SQSUtils = mock[SQSUtils]
  def config(commandArg: String): Map[String, String] =
    Map("command" -> s"test.sh $commandArg.csv", "efs.root.location" -> "./src/test/resources/testfiles", "sqs.queue.output" -> "output")

  def ffidFile: FFIDFile = FFIDFile(UUID.fromString("f0a73877-6057-4bbb-a1eb-7c7b73cab586"), UUID.fromString("acea5919-25a3-4c6b-8908-fa47cc77878f"), "originalPath")
  def ffidFileWithQuote: FFIDFile = FFIDFile(UUID.fromString("f0a73877-6057-4bbb-a1eb-7c7b73cab586"), UUID.fromString("acea5919-25a3-4c6b-8908-fa47cc77878f"), """rootDirectory/originalPath"withQu'ote""")
}
