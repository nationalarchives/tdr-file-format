package uk.gov.nationalarchives.fileformat

import com.github.tomakehurst.wiremock.client.WireMock.{post, urlEqualTo}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import uk.gov.nationalarchives.fileformat.AWSUtils._

import scala.language.postfixOps

class AWSSpec extends AnyFlatSpec with BeforeAndAfterEach with BeforeAndAfterAll {

  override def beforeAll(): Unit = {
    wiremockKmsEndpoint.start()
    wiremockKmsEndpoint.stubFor(post(urlEqualTo("/")))
    api.start()
    inputQueueHelper.createQueue
    outputQueueHelper.createQueue
  }

  override def afterAll(): Unit = {
    wiremockKmsEndpoint.stop()
    api.shutdown
  }

  override def afterEach(): Unit = {
    inputQueueHelper.receive.foreach(inputQueueHelper.delete)
    outputQueueHelper.receive.foreach(inputQueueHelper.delete)
  }
}
