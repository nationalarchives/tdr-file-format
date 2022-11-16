package uk.gov.nationalarchives.fileformat

import graphql.codegen.types.FFIDMetadataInputMatches
import org.mockito.ArgumentMatchers.any
import org.mockito.MockitoSugar
import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers._
import uk.gov.nationalarchives.fileformat.DroidCommandRunner.SignatureVersions
import uk.gov.nationalarchives.fileformat.FFIDExtractor.FFIDFile

import java.util.UUID

//noinspection ScalaDeprecation
class FFIDExtractorTest extends AnyFlatSpec with MockitoSugar with EitherValues {
  val rootDirectory = "test"

  "The ffid method" should "return the correct droid and signature version" in {
    val mockCommandRunner = mock[DroidCommandRunner]
    val testDroidVersion = "TEST_DROID_VERSION"
    val testBinarySignatureVersion = "TEST_BINARY_SIGNATURE_VERSION"
    val testContainerSignatureVersion = "TEST_CONTAINER_SIGNATURE_VERSION"

    when(mockCommandRunner.version).thenReturn(testDroidVersion)
    when(mockCommandRunner.signatureVersions).thenReturn(SignatureVersions(testBinarySignatureVersion, testContainerSignatureVersion))
    when(mockCommandRunner.getCSVMatches(any[String], any[String])).thenReturn(List())

    val result = new FFIDExtractor(mockCommandRunner, rootDirectory).ffidFile(ffidFile)

    val ffid = result.right.value
    ffid.softwareVersion should equal(testDroidVersion)
    ffid.containerSignatureFileVersion should equal(testContainerSignatureVersion)
    ffid.binarySignatureFileVersion should equal(testBinarySignatureVersion)
  }

  "The ffid method" should "return the correct value if the extension and puid are empty" in {
    val commandRunner = mock[DroidCommandRunner]
    val mockMatches = FFIDMetadataInputMatches(None, "Extension", None)

    when(commandRunner.version).thenReturn("")
    when(commandRunner.signatureVersions).thenReturn(SignatureVersions("", ""))
    when(commandRunner.getCSVMatches(any[String], any[String])).thenReturn(List(mockMatches))

    val result = new FFIDExtractor(commandRunner, rootDirectory).ffidFile(ffidFile)
    val ffid = result.right.value
    val m = ffid.matches.head
    m.extension.isEmpty should be(true)
    m.puid.isEmpty should be(true)
  }

  "The ffid method" should "return more than one result for multiple result rows" in {
    val commandRunner = mock[DroidCommandRunner]
    val apiResults = for {
      count <- List("1", "2", "3")
      res <- FFIDMetadataInputMatches(Option(s"extension$count"), "Extension", Option(s"puid$count")) :: Nil
    } yield res

    when(commandRunner.version).thenReturn("")
    when(commandRunner.signatureVersions).thenReturn(SignatureVersions("", ""))
    when(commandRunner.getCSVMatches(any[String], any[String])).thenReturn(apiResults)

    val result = new FFIDExtractor(commandRunner, rootDirectory).ffidFile(ffidFile)
    val ffid = result.right.value
    ffid.matches.size should equal(3)
  }

  "The ffid method" should "return an error if there is an error running the droid commands" in {
    val commandRunner = mock[DroidCommandRunner]
    when(commandRunner.version).thenReturn("")
    when(commandRunner.signatureVersions).thenReturn(SignatureVersions("", ""))
    when(commandRunner.getCSVMatches(any[String], any[String])).thenThrow(new Exception("Droid error processing files"))
    val file = ffidFile
    val result = new FFIDExtractor(commandRunner, rootDirectory).ffidFile(file)
    result.left.value.getMessage should equal(s"Error processing file id ${file.fileId} with original path originalPath")
    result.left.value.getCause.getMessage should equal("Droid error processing files")
  }

  "The ffid method" should "return a correct value if there are quotes in the filename" in {
    val commandRunner = mock[DroidCommandRunner]
    when(commandRunner.version).thenReturn("")
    when(commandRunner.signatureVersions).thenReturn(SignatureVersions("", ""))
    when(commandRunner.getCSVMatches(any[String], any[String])).thenReturn(List())

    val result = new FFIDExtractor(commandRunner, rootDirectory).ffidFile(ffidFileWithQuote)
    result.isRight should be (true)
  }

  val userId: UUID = UUID.randomUUID()
  def ffidFile: FFIDFile = FFIDFile(UUID.randomUUID(), UUID.randomUUID(), "originalPath", userId)
  def ffidFileWithQuote: FFIDFile = FFIDFile(UUID.randomUUID(), UUID.randomUUID(), """rootDirectory/originalPath"withQu'ote""", userId)
}
