package uk.gov.nationalarchives.fileformat

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.http.{HttpHeader, HttpHeaders}
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import graphql.codegen.types.FFIDMetadataInputValues
import io.circe.Printer
import io.circe.generic.auto._
import io.circe.parser.decode
import io.circe.syntax._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers.{equal, _}
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import uk.gov.nationalarchives.droid.internal.api.DroidAPI
import uk.gov.nationalarchives.fileformat.FFIDExtractor.FFIDFile
import uk.gov.nationalarchives.fileformat.Lambda.FFIDResult

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, File, RandomAccessFile}
import java.net.URI
import java.nio.file.{Files, Path, Paths}
import java.util
import java.util.UUID
import scala.io.Source.{fromFile, fromResource}
import scala.jdk.CollectionConverters.{IterableHasAsJava, ListHasAsScala, MapHasAsJava}
import scala.reflect.io.Directory
import scala.util.{Failure, Success, Using}

class TestUtils extends AnyFlatSpec with BeforeAndAfterEach with BeforeAndAfterAll with TableDrivenPropertyChecks {
  val testBinarySignatureVersion = "./src/test/resources/containers/droid_signatures.xml"
  val testContainerSignatureVersion = "./src/test/resources/containers/container_signatures.xml"

  val s3Client: S3Client = S3Client.builder
    .region(Region.EU_WEST_2)
    .endpointOverride(URI.create("http://localhost:8003/"))
    .build()

  val api = DroidAPI.builder()
    .containerSignature(Path.of(testContainerSignatureVersion))
    .binarySignature(Path.of(testBinarySignatureVersion))
    .s3Client(s3Client)
    .build()

  val wiremockS3 = new WireMockServer(8003)

  def getFile(filePath: String): String = {
    Using(fromFile(filePath)) { file => file.mkString } match {
      case Failure(exception) => throw exception
      case Success(value) => value
    }
  }

  val tnaCdn: WireMockServer = {
    val tnaCdn = new WireMockServer(9002)
    val path = "./src/test/resources/containers"
    tnaCdn.stubFor(get(urlEqualTo("/DROID_SignatureFile_V1.xml"))
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
    tnaCdn.getAllServeEvents.asScala.foreach(ev => tnaCdn.removeServeEvent(ev.getId))
  }

  override def afterEach(): Unit = {
    new File("${sys:logFile}").delete()
    new File("derby.log").delete()
    val runningFiles = new File(s"./src/test/resources/testfiles/running-files/")
    if (runningFiles.exists()) {
      new Directory(runningFiles).deleteRecursively()
    }
    wiremockS3.stop()
  }

  def getBytesForRange(filePath: String, range: String): Array[Byte] = {
    val rangeArr: Array[String] = range.split("=")(1).split("-")
    val rangeStart = rangeArr(0).toInt
    val rangeEnd = rangeArr(1).toInt
    val length = rangeEnd - rangeStart + 1
    val raf = new RandomAccessFile(filePath, "r")
    try {
      raf.seek(rangeStart)
      val buffer: Array[Byte] = new Array[Byte](length)
      raf.read(buffer) match {
        case br: Int if br == length => buffer
        case br: Int => util.Arrays.copyOf(buffer, br)
        case _ => new Array[Byte](0)
      }
    }
  }

  def stubS3HeadObject(fileName: String, urlStub: String): StubMapping = {
    val filePath = s"./src/test/resources/testfiles/$fileName"
    val bytes = Files.readAllBytes(Paths.get(filePath))
    wiremockS3.stubFor(head(urlEqualTo(urlStub))
      .willReturn(aResponse()
        .withStatus(200)
        .withHeaders(new HttpHeaders(List(new HttpHeader("Content-Length", bytes.size.toString), new HttpHeader("Last-Modified", "Mon, 03 Mar 2025 17:29:48 GMT")).asJava))
        .withBody("".getBytes)
      )
    )
  }

  def stubS3GetBytes(fileName: String, urlStub: String): Unit = {
    val filePath = s"./src/test/resources/testfiles/$fileName"
    val bytes = Files.readAllBytes(Paths.get(filePath))

    wiremockS3.stubFor(get(urlEqualTo(urlStub)).withHeader("range", equalTo("bytes=0-4095"))
      .willReturn(aResponse()
        .withStatus(200)
        .withBody(bytes.slice(0, math.min(bytes.size, 4096))),
      )
    )
    wiremockS3.stubFor(get(urlEqualTo(urlStub)).withHeader("range", equalTo("bytes=4096-8191"))
      .willReturn(aResponse()
        .withStatus(200)
        .withBody(bytes.slice(4096, math.min(bytes.size, 8192))),
      )
    )
  }

  def stubS3GetObjectList(userId: UUID, consignmentId: UUID, fileIds: List[UUID]): StubMapping = {
    val params = Map("list-type" -> equalTo("2")).asJava
    val response = <ListBucketResult xmlns="http://s3.amazonaws.com/doc/2006-03-01/">
      {fileIds.map(fileId =>
        <Contents>
          <Key>{userId}/{consignmentId}/{fileId}</Key>
          <LastModified>2009-10-12T17:50:30.000Z</LastModified>
          <ETag>"fba9dede5f27731c9771645a39863328"</ETag>
          <Size>1</Size>
        </Contents>
      )}
    </ListBucketResult>
    wiremockS3.stubFor(
      get(anyUrl())
        .withQueryParams(params)
        .willReturn(okXml(response.toString))
    )
  }

  def mockS3Error(): StubMapping = {
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
    case Right(value) => value.fileFormat
  }

  def testFFIDExtractResult(eventName: String, fileName: String, expectedPuids: List[String], expectedFileExtensionMismatch: Boolean): Unit = {
    val ffidFile = decodeInputJson(eventName)
    val urlStub = ffidFile.s3SourceBucketKey match {
      case Some(v) => s"/$v"
      case _ => s"/${ffidFile.userId}/${ffidFile.consignmentId}/${ffidFile.fileId}"
    }
    val fileWithReplacedSuffix = ffidFile.copy(originalPath = ffidFile.originalPath.replace("{suffix}", fileName.split("\\.").last))
    stubS3GetBytes(fileName, urlStub)
    stubS3HeadObject(fileName, urlStub)
    stubS3GetObjectList(ffidFile.userId, ffidFile.consignmentId, List(ffidFile.fileId))
    val result = new FFIDExtractor(api, "testbucket").ffidFile(fileWithReplacedSuffix)
    result.foreach(v => {
      v.matches.size should equal(expectedPuids.size)
      v.matches.exists(_.fileExtensionMismatch == Option(expectedFileExtensionMismatch)) should equal(true)
      expectedPuids.foreach(puid => v.matches.exists(_.puid == Option(puid)) should equal (true))
    })
  }
}
