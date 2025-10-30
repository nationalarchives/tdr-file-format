package uk.gov.nationalarchives.fileformat

import com.typesafe.config.{Config, ConfigFactory}
import org.mockito.MockitoSugar
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers._
import org.scalatest.prop.{TableDrivenPropertyChecks, TableFor2}
import uk.gov.nationalarchives.fileformat.SignatureFiles.SignatureFileType
import uk.gov.nationalarchives.fileformat.SignatureFiles.SignatureFileType.{ContainerSignature, DroidSignature, SignatureFileType}

class SignatureFilesTest extends AnyFlatSpec with MockitoSugar with TableDrivenPropertyChecks {
  private val configFactory: Config = ConfigFactory.load
  private val containerSignatureName = configFactory.getString("containers.signature.name")
  private val containerSignatureVersion = configFactory.getString("containers.version")
  private val droidSignatureName = configFactory.getString("droid.signature.name")
  private val droidSignatureVersion = configFactory.getString("droid.version")

  val signatureTypes: TableFor2[SignatureFileType, String] = Table(
    ("signatureType", "fileName"),
    (DroidSignature, droidSignatureName + droidSignatureVersion + ".xml"),
    (ContainerSignature, containerSignatureName + containerSignatureVersion + ".xml")
  )

  forAll(signatureTypes) { (signatureType, fileName) =>
    "findSignatureFile" should s"find and return file path of $signatureType signature" in {
      val fileResponse = SignatureFiles().findSignatureFile(signatureType)
      fileResponse.getFileName.toString should be(fileName)
    }
  }

  "SignatureFileType" should "contain the correct values from the application config" in {
    SignatureFileType.values.size should be(2)
    SignatureFileType.ContainerSignature.toString should equal(containerSignatureName + containerSignatureVersion)
    SignatureFileType.DroidSignature.toString should equal(droidSignatureName + droidSignatureVersion)
  }
}
