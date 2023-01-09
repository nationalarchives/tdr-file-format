package uk.gov.nationalarchives.fileformat

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, anyUrl, get, okXml, urlEqualTo}
import graphql.codegen.types.{FFIDMetadataInput, FFIDMetadataInputValues}
import io.circe.{DecodingFailure, Printer}
import io.circe.generic.auto._
import io.circe.parser.decode
import io.circe.syntax._
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers.{equal, _}
import org.scalatest.prop.{TableDrivenPropertyChecks, TableFor2}
import uk.gov.nationalarchives.fileformat.FFIDExtractor.FFIDFile
import uk.gov.nationalarchives.fileformat.Lambda.FFIDResult

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, File}
import java.nio.file.{Files, Paths}
import scala.io.Source.{fromFile, fromResource}
import scala.reflect.io.Directory
import scala.util.{Failure, Success, Using}

class LambdaTest extends AnyFlatSpec with BeforeAndAfterEach with BeforeAndAfterAll with TableDrivenPropertyChecks {
  val wiremockS3 = new WireMockServer(8003)

  def getFile(filePath: String): String = {
    Using(fromFile(filePath)) { file => file.mkString } match {
      case Failure(exception) => throw exception
      case Success(value) => value
    }
  }

  def tnaCdn: WireMockServer = {
    val tnaCdn = new WireMockServer(9002)
    val path = "./src/test/resources/containers"
    tnaCdn.stubFor(get(urlEqualTo("/DROID_SignatureFile_1.xml"))
      .willReturn(okXml(getFile(s"$path/droid_signatures.xml"))))
    tnaCdn.stubFor(get(urlEqualTo("/container-signature-1.xml"))
      .willReturn(okXml(getFile(s"$path/container_signatures.xml"))))
    tnaCdn
  }

  override def beforeAll(): Unit = {
    tnaCdn.start()
  }

  override def afterAll(): Unit = {
    tnaCdn.stop()
  }

  override def beforeEach(): Unit = {
    val testFilesPath = "./src/test/resources/testfiles"
    new File(s"$testFilesPath/running-files").mkdir()
    wiremockS3.start()
  }

  override def afterEach(): Unit = {
    new File("${sys:logFile}").delete()
    new File("derby.log").delete()
    val runningFiles = new File(s"./src/test/resources/testfiles/running-files/")
    if(runningFiles.exists()) {
      new Directory(runningFiles).deleteRecursively()
    }
    wiremockS3.stop()
  }

  def mockFileDownload(ffidFile: FFIDFile, fileName: String) = {
    val filePath = s"./src/test/resources/testfiles/$fileName"
    val bytes = Files.readAllBytes(Paths.get(filePath))
    wiremockS3.stubFor(get(urlEqualTo(s"/${ffidFile.userId}/${ffidFile.consignmentId}/${ffidFile.fileId}"))
      .willReturn(aResponse().withStatus(200).withBody(bytes))
    )
  }

  def mockS3Error() = {
    wiremockS3.stubFor(get(anyUrl())
      .willReturn(aResponse().withStatus(404))
    )
  }

  def decodeInputJson(fileName: String): FFIDFile = decode[FFIDFile](fromResource(s"json/$fileName.json").mkString) match {
    case Left(err) => throw err
    case Right(value) => value
  }

  def createEvent(ffidFile: FFIDFile) = new ByteArrayInputStream(ffidFile.asJson.printWith(Printer.noSpaces).getBytes())

  def decodeOutput(outputStream: ByteArrayOutputStream): FFIDMetadataInputValues = decode[FFIDResult](outputStream.toByteArray.map(_.toChar).mkString) match {
    case Left(err) => throw err
    case Right(value) => value.fileFormat.metadataInputValues.head
  }

  def testValidFileFormatEvent(eventName: String, fileName: String, expectedPuids: List[String]): Unit = {
    val ffidFile = decodeInputJson(eventName)
    val fileWithReplacedSuffix = ffidFile.copy(originalPath = ffidFile.originalPath.replace("{suffix}", fileName.split("\\.").last))
    mockFileDownload(fileWithReplacedSuffix, fileName)
    val outputStream = new ByteArrayOutputStream()
    new Lambda().process(createEvent(fileWithReplacedSuffix), outputStream)
    val decodedOutput = decodeOutput(outputStream)
    decodedOutput.matches.size should equal(expectedPuids.size)
    expectedPuids.foreach(puid => {
      decodedOutput.matches.exists(_.puid == Option(puid)) should equal(true)
    })
  }

  "The process method" should "return an error if the consignment id is invalid" in {
    val exception = intercept[DecodingFailure] {
      new Lambda().process(createEvent(decodeInputJson("ffid_invalid_consignment_id")), null)
    }
    exception.getMessage should equal("Got value '\"1\"' with wrong type, expecting string: DownField(consignmentId)")
  }

  "The process method" should "return an error if the file id is missing" in {
    mockS3Error()
    val exception = intercept[RuntimeException] {
      new Lambda().process(createEvent(decodeInputJson("ffid_missing_file")), null)
    }
    exception.getMessage should equal("null (Service: S3, Status Code: 404, Request ID: null)")
  }

  val testFiles: TableFor2[String, List[String]] = Table(
    ("FileName","ExpectedPuids"),
    ("Test.docx", List("fmt/412")),
    ("Test.xlsx", List("fmt/214")),
    ("Test.pdf", List("fmt/276"))
  )

  forAll(testFiles) { (fileName, expectedPuids) =>
    "The process method" should s"put return the correct format for $fileName" in {
      testValidFileFormatEvent("ffid_event", fileName, expectedPuids)
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
