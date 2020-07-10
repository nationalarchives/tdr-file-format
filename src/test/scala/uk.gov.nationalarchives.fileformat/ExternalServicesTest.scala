package uk.gov.nationalarchives.fileformat

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import uk.gov.nationalarchives.fileformat.AWSUtils._

import scala.concurrent.ExecutionContext
import scala.io.Source.fromResource
import scala.sys.process._
import scala.language.postfixOps

class ExternalServicesTest extends AnyFlatSpec with BeforeAndAfterEach with BeforeAndAfterAll with ScalaFutures {

  override implicit def patienceConfig: PatienceConfig = PatienceConfig(timeout = scaled(Span(5, Seconds)), interval = scaled(Span(100, Millis)))

  val wiremockGraphqlServer = new WireMockServer(9001)
  val wiremockAuthServer = new WireMockServer(9002)

  implicit val ec: ExecutionContext = ExecutionContext.global

  val graphQlPath = "/graphql"
  val authPath = "/auth/realms/tdr/protocol/openid-connect/token"

  def graphQlUrl: String = wiremockGraphqlServer.url(graphQlPath)


  // This only needs to return the original file path query data
  def graphqlOriginalPath: StubMapping = wiremockGraphqlServer.stubFor(post(urlEqualTo(graphQlPath))
    .willReturn(okJson(fromResource(s"json/original_path_response.json").mkString)))

  def authOk: StubMapping = wiremockAuthServer.stubFor(post(urlEqualTo(authPath))
    .willReturn(okJson(fromResource(s"json/access_token.json").mkString)))

  override def beforeAll(): Unit = {
    s3Api.start
    wiremockGraphqlServer.start()
    wiremockAuthServer.start()
    api.start()
    inputQueueHelper.createQueue
    outputQueueHelper.createQueue
  }

  override def beforeEach(): Unit = {
    graphqlOriginalPath
    authOk
    createBucket
  }

  override def afterAll(): Unit = {
    wiremockGraphqlServer.stop()
    wiremockAuthServer.stop()
    api.shutdown()
    api.shutdown
  }

  override def afterEach(): Unit = {
    deleteBucket()
    wiremockAuthServer.resetAll()
    wiremockGraphqlServer.resetAll()
    inputQueueHelper.receive.foreach(inputQueueHelper.delete)
    outputQueueHelper.receive.foreach(inputQueueHelper.delete)
    "rm -f ./src/test/resources/testfiles/originalPath".!
    "rm -rf ./src/test/resources/testfiles/f0a73877-6057-4bbb-a1eb-7c7b73cab586".!

  }
}
