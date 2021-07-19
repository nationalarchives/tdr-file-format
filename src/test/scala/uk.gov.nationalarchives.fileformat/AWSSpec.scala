package uk.gov.nationalarchives.fileformat

import com.github.tomakehurst.wiremock.client.WireMock.{post, urlEqualTo}
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, Suite}
import uk.gov.nationalarchives.fileformat.AWSUtils._

import scala.language.postfixOps

trait AWSSpec extends BeforeAndAfterEach with BeforeAndAfterAll { this: Suite =>

  override def beforeAll(): Unit = {
    wiremockKmsEndpoint.start()
    wiremockKmsEndpoint.stubFor(post(urlEqualTo("/")))
    api.start()
    inputQueueHelper.createQueue
    outputQueueHelper.createQueue

    super.beforeAll()
  }

  override def afterAll(): Unit = {
    super.afterAll()

    wiremockKmsEndpoint.stop()
    api.shutdown
  }

  override def afterEach(): Unit = {
    super.afterEach()

    inputQueueHelper.receive.foreach(inputQueueHelper.delete)
    outputQueueHelper.receive.foreach(inputQueueHelper.delete)
  }
}
