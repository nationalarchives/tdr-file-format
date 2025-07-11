package uk.gov.nationalarchives.fileformat

import org.mockito.MockitoSugar
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers._
import org.scalatest.prop.{TableDrivenPropertyChecks, TableFor2}

import java.io.FileNotFoundException

class SignatureFilesTest extends AnyFlatSpec with MockitoSugar with TableDrivenPropertyChecks {

  val signatureTypes: TableFor2[String, String] = Table(
    ("signatureType", "fileName"),
    ("droid", "DROID_SignatureFile_V120.xml"),
    ("container", "container-signature-20240715.xml")
  )

  forAll(signatureTypes) { (signatureType, fileName) =>
    "findSignatureFile" should s"find and return file path of $signatureType signature" in {
      val fileResponse = SignatureFiles().findSignatureFile(signatureType)
      fileResponse.getFileName.toString should be(fileName)
    }
  }

  "findSignatureFile" should "throw an exception if the given signatureType doesn't exist" in {
    val ex = intercept[FileNotFoundException] {
      SignatureFiles().findSignatureFile("xyz")
    }
    ex.getMessage should be("Signature file for xyz not found locally.")
  }
}
