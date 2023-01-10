package uk.gov.nationalarchives.fileformat

import org.mockito.ArgumentMatchers.any
import org.mockito.{ArgumentCaptor, MockitoSugar}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers._
import org.scalatest.prop.{TableDrivenPropertyChecks, TableFor1}

import java.io.File
import java.net.URI
import java.net.http.HttpResponse.BodyHandler
import java.net.http.{HttpClient, HttpHeaders, HttpRequest, HttpResponse}
import java.util.{Collections, Optional}
import javax.net.ssl.SSLSession
import scala.io.Source
import scala.jdk.CollectionConverters._

class SignatureFilesTest extends AnyFlatSpec with MockitoSugar with TableDrivenPropertyChecks {

  val signatureTypes: TableFor1[String] = Table(
    "signatureType",
    "droid",
    "container"
  )

  forAll(signatureTypes) { signatureType =>
    "downloadSignatureFile" should s"not call the droid api if the files exist for the $signatureType signature file" in {
      val client = mock[HttpClient]
      val versionCaptor: ArgumentCaptor[HttpRequest] = ArgumentCaptor.forClass(classOf[HttpRequest])
      when(client.send[Any](versionCaptor.capture(), any[BodyHandler[Any]]))
        .thenReturn(
          createResponse(200, Source.fromResource("containers/droid_version.xml").mkString),
          createResponse(200, "test")
        )
      val containerSignatureFile = new File("container-signature-1.xml")
      val droidSignatureFile = new File("DROID_SignatureFile_V1")
      val existingFiles = List(containerSignatureFile, droidSignatureFile)
      new SignatureFiles(client, existingFiles).downloadSignatureFile(signatureType)
      versionCaptor.getAllValues.size should equal(0)
    }

    "downloadSignatureFile" should s"not call the droid api if the files do not exist for the $signatureType" in {
      val client = mock[HttpClient]
      val versionCaptor: ArgumentCaptor[HttpRequest] = ArgumentCaptor.forClass(classOf[HttpRequest])
      when(client.send[Any](versionCaptor.capture(), any[BodyHandler[Any]]))
        .thenReturn(
          createResponse(200, Source.fromResource("containers/droid_version.xml").mkString),
          createResponse(200, "test")
        )
      new SignatureFiles(client, Nil).downloadSignatureFile(signatureType)
      versionCaptor.getAllValues.size should equal(2)
    }

    "downloadSignatureFile" should s"return an error if the API returns an error for the $signatureType signature" in {
      val client = mock[HttpClient]
      val versionCaptor: ArgumentCaptor[HttpRequest] = ArgumentCaptor.forClass(classOf[HttpRequest])
      when(client.send[Any](versionCaptor.capture(), any[BodyHandler[Any]]))
        .thenThrow(new Exception("An error"))
      val fileResponse = new SignatureFiles(client, Nil).downloadSignatureFile(signatureType)
      fileResponse.isFailure should be(true)
    }
  }

  def createResponse[T](status: Int, responseBody: T): HttpResponse[T] = new HttpResponse[T] {

    override def statusCode(): Int = status

    override def request(): HttpRequest = HttpRequest.newBuilder.build

    override def previousResponse(): Optional[HttpResponse[T]] = Optional.empty()

    override def headers(): HttpHeaders = {
      val map = Map[String, java.util.List[String]]("last-modified" -> Collections.singletonList("Thu, 1 Jan 1970 00:00:00 GMT"))
      HttpHeaders.of(map.asJava, (_, _) => true)
    }

    override def body(): T = responseBody

    override def sslSession(): Optional[SSLSession] = Optional.empty

    override def uri(): URI = URI.create("http://localhost")

    override def version(): HttpClient.Version = HttpClient.Version.HTTP_2
  }
}
