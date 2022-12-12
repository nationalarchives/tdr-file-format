package uk.gov.nationalarchives.fileformat

import com.github.tomakehurst.wiremock.WireMockServer
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, Suite}
import com.github.tomakehurst.wiremock.client.WireMock.{get, okXml, urlEqualTo}

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
}
