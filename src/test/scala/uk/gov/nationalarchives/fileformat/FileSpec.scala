package uk.gov.nationalarchives.fileformat

import com.github.tomakehurst.wiremock.WireMockServer
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, Suite}
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.{get, okXml, urlEqualTo}

import scala.sys.process._
import scala.io.Source.fromResource

trait FileSpec extends BeforeAndAfterEach with BeforeAndAfterAll { this: Suite =>

  def tnaCdn: WireMockServer = {
    val tnaCdn = new WireMockServer(9002)
    tnaCdn.stubFor(get(urlEqualTo("/DROID_SignatureFile_1.xml"))
      .willReturn(okXml(fromResource("containers/droid_signatures.xml").mkString)))
    tnaCdn.stubFor(get(urlEqualTo("/container-signature-1.xml"))
      .willReturn(okXml(fromResource("containers/container_signatures.xml").mkString)))
    tnaCdn
  }

  override def beforeAll(): Unit = {
    tnaCdn.start()
    super.beforeAll()
  }

  // Create the files and then try to ls them in the test script. This mimics reading from the existing file on EFS
  // and should catch problems where there are spaces in the filename.
  override def beforeEach(): Unit = {
    "mkdir -p ./src/test/resources/testfiles/rootDirectory/subDirectory".!
    "touch ./src/test/resources/testfiles/originalPath".!
    "touch ./src/test/resources/testfiles/pathwith`".!!
    Seq("bash", "-c", """touch "./src/test/resources/testfiles/rootDirectory/originalPath\"withQu'ote"""").!!
    """touch "./src/test/resources/testfiles/path with space" """.!
    "touch ./src/test/resources/testfiles/rootDirectory/subDirectory/originalPath".!

    super.beforeEach()
  }

  override def afterEach(): Unit = {
    super.afterEach()
    "rm -f ./src/test/resources/testfiles/originalPath".!
    "rm -rf ./src/test/resources/testfiles/rootDirectory".!
    "rm -f './src/test/resources/testfiles/path with space'".!
    "rm -f './src/test/resources/testfiles/pathwith`'".!
    "rm -rf ./src/test/resources/testfiles/f0a73877-6057-4bbb-a1eb-7c7b73cab586".!
    "rm -rf ./src/test/resources/testfiles/*.xml".!
  }
}
