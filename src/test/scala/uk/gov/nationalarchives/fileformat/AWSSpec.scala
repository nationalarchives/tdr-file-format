package uk.gov.nationalarchives.fileformat

import io.findify.s3mock.S3Mock
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, Suite}
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.{CreateBucketRequest, PutObjectRequest}
import uk.gov.nationalarchives.fileformat.AWSUtils._

import java.io.File
import java.net.URI
import java.nio.file.Path
import scala.language.postfixOps

trait AWSSpec extends BeforeAndAfterEach with BeforeAndAfterAll { this: Suite =>

  def createS3Mock(): Unit = {
    val s3Api: S3Mock = S3Mock(port = 8003, dir = "/tmp/s3")
    val s3Client: S3Client = S3Client.builder
      .region(Region.EU_WEST_2)
      .endpointOverride(URI.create("http://localhost:8003/"))
      .build()
    s3Api.start

    s3Client.createBucket(CreateBucketRequest.builder.bucket("testbucket").build)
    val path: Path = new File(getClass.getResource(s"/testfiles/testfile").getPath).toPath
    val putObjectRequest: PutObjectRequest = PutObjectRequest.builder.bucket("testbucket").key("9a5f9f7e-0e1d-4bc6-8c81-a7d305acf324/f0a73877-6057-4bbb-a1eb-7c7b73cab586/acea5919-25a3-4c6b-8908-fa47cc77878f").build
    s3Client.putObject(putObjectRequest, path)
  }

  override def beforeAll(): Unit = {
    super.beforeAll()
  }

  override def afterAll(): Unit = {
    super.afterAll()
  }

  override def beforeEach(): Unit = {
    inputQueueHelper.createQueue
    outputQueueHelper.createQueue

    super.beforeEach()
  }

  override def afterEach(): Unit = {
    super.afterEach()

    inputQueueHelper.deleteQueue()
    outputQueueHelper.deleteQueue()
  }
}
