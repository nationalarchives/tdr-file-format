package uk.gov.nationalarchives.fileformat

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import uk.gov.nationalarchives.fileformat.AWSUtils._

import scala.language.postfixOps

class SqsSpec extends AnyFlatSpec with BeforeAndAfterEach with BeforeAndAfterAll {

  override def beforeAll(): Unit = {
    api.start()
    inputQueueHelper.createQueue
    outputQueueHelper.createQueue
  }

  override def afterAll(): Unit = {
    api.shutdown
  }

  override def afterEach(): Unit = {
    inputQueueHelper.receive.foreach(inputQueueHelper.delete)
    outputQueueHelper.receive.foreach(inputQueueHelper.delete)
  }
}
