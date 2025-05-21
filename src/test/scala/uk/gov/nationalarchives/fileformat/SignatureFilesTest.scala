package uk.gov.nationalarchives.fileformat

import org.mockito.ArgumentMatchers.any
import org.mockito.{ArgumentCaptor, MockitoSugar}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers._
import org.scalatest.prop.{TableDrivenPropertyChecks, TableFor1}

import java.net.http.HttpResponse.BodyHandler
import java.net.http.{HttpClient, HttpRequest}

class SignatureFilesTest extends AnyFlatSpec with MockitoSugar with TableDrivenPropertyChecks {

  val signatureTypes: TableFor1[String] = Table(
    "signatureType",
    "droid",
    "container"
  )

  forAll(signatureTypes) { signatureType =>
    "downloadSignatureFile" should s"return an error if the API returns an error for the $signatureType signature" in {
      val client = mock[HttpClient]
      val versionCaptor: ArgumentCaptor[HttpRequest] = ArgumentCaptor.forClass(classOf[HttpRequest])
      when(client.send[Any](versionCaptor.capture(), any[BodyHandler[Any]]))
        .thenThrow(new Exception("An error"))
      val fileResponse = new SignatureFiles(client, Nil).downloadSignatureFile(signatureType)
      fileResponse.isFailure should be(true)
    }
  }
}
