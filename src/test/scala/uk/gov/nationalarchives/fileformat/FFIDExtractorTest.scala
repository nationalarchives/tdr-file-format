package uk.gov.nationalarchives.fileformat

import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, get, urlEqualTo}
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import org.mockito.ArgumentMatchers.any
import org.mockito.MockitoSugar
import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers._
import uk.gov.nationalarchives.droid.core.interfaces.IdentificationMethod
import uk.gov.nationalarchives.droid.internal.api.{ApiResult, DroidAPI}
import uk.gov.nationalarchives.fileformat.FFIDExtractor.FFIDFile

import java.net.URI
import java.nio.file.{Files, Path, Paths}
import java.util.UUID
import scala.jdk.CollectionConverters._

//noinspection ScalaDeprecation
class FFIDExtractorTest extends TestUtils with MockitoSugar with EitherValues {
  val rootDirectory = "test"
  val userId: UUID = UUID.randomUUID()
  val consignmentId = UUID.randomUUID()
  val fileId = UUID.randomUUID()

  "The ffid method" should "return the correct droid and signature version" in {
    val testBinarySignatureVersion = "./src/test/resources/containers/droid_signatures.xml"
    val testContainerSignatureVersion = "./src/test/resources/containers/container_signatures.xml"

    val api = DroidAPI.builder()
      .containerSignature(Path.of(testContainerSignatureVersion))
      .binarySignature(Path.of(testBinarySignatureVersion))
      .s3Client(s3Client)
      .build()

    stubS3GetObjectList(userId, consignmentId, List(fileId))
    stubS3GetBytes("Test.docx", s"/$userId/$consignmentId/$fileId")
    val result = new FFIDExtractor(api, "testbucket").ffidFile(ffidFile)

    val ffid = result.right.value
    ffid.matches.size should equal(1)
    ffid.softwareVersion should equal("6.9.0")
    ffid.containerSignatureFileVersion should equal("30")
    ffid.binarySignatureFileVersion should equal("107")
  }

//  "The ffid method" should "return the correct value if the extension and puid are empty" in {
//    val api = mock[DroidAPI]
//    val mockResult = new ApiResult(null, IdentificationMethod.EXTENSION, null, "testName", false)
//    when(api.submit(any[Path])).thenReturn(List(mockResult).asJava)
//
//    val result = new FFIDExtractor(api, rootDirectory).ffidFile(ffidFile)
//    val ffid = result.right.value
//    val m = ffid.matches.head
//    m.extension.isEmpty should be(true)
//    m.puid.isEmpty should be(true)
//  }
//
//  "the ffid method" should "return a file extension mismatch if one exists" in {
//    val api = mock[DroidAPI]
//    val mockResult = new ApiResult(null, IdentificationMethod.EXTENSION, null, "testName", true)
//    when(api.submit(any[Path])).thenReturn(List(mockResult).asJava)
//
//    val result = new FFIDExtractor(api, rootDirectory).ffidFile(ffidFile)
//    val ffid = result.right.value
//    val m = ffid.matches.head
//    m.fileExtensionMismatch.contains(true)
//  }
//
//  "the ffid method" should "return a file format name if one exists" in {
//    val api = mock[DroidAPI]
//    val mockResult = new ApiResult(null, IdentificationMethod.EXTENSION, null, ".formatName", true)
//    when(api.submit(any[Path])).thenReturn(List(mockResult).asJava)
//
//    val result = new FFIDExtractor(api, rootDirectory).ffidFile(ffidFile)
//    val ffid = result.right.value
//    val m = ffid.matches.head
//    m.formatName.contains(".formatName")
//  }
//
//  "The ffid method" should "return more than one result for multiple result rows" in {
//    val api = mock[DroidAPI]
//    val apiResults = for {
//      count <- List("1", "2", "3")
//      res <- new ApiResult(s"extension$count", IdentificationMethod.EXTENSION, s"puid$count", s"testName$count", false) :: Nil
//    } yield res
//
//    when(api.submit(any[Path])).thenReturn(apiResults.asJava)
//
//    val result = new FFIDExtractor(api, rootDirectory).ffidFile(ffidFile)
//    val ffid = result.right.value
//    ffid.matches.size should equal(3)
//  }
//
//  "The ffid method" should "return an error if there is an error running the droid commands" in {
//    val api = mock[DroidAPI]
//    when(api.submit(any[Path])).thenThrow(new Exception("Droid error processing files"))
//    val file = ffidFile
//    val result = new FFIDExtractor(api, rootDirectory).ffidFile(file)
//    result.left.value.getMessage should equal(s"Error processing file id ${file.fileId} with original path originalPath")
//    result.left.value.getCause.getMessage should equal("Droid error processing files")
//  }
//
//  "The ffid method" should "return a correct value if there are quotes in the filename" in {
//    val api = mock[DroidAPI]
//    when(api.submit(any[Path])).thenReturn(List().asJava)
//
//    val result = new FFIDExtractor(api, rootDirectory).ffidFile(ffidFileWithQuote)
//    result.isRight should be(true)
//  }



  def ffidFile: FFIDFile = FFIDFile(consignmentId, fileId, "originalPath", userId)

  def ffidFileWithQuote: FFIDFile = FFIDFile(UUID.randomUUID(), UUID.randomUUID(), """rootDirectory/originalPath"withQu'ote""", userId)
}
