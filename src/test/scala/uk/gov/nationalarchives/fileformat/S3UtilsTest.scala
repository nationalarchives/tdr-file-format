package uk.gov.nationalarchives.fileformat

import org.mockito.ArgumentMatchers.any
import org.mockito.{ArgumentCaptor, Mockito, MockitoSugar}
import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers._
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.{GetObjectRequest, GetObjectResponse, S3Exception}
import uk.gov.nationalarchives.fileformat.FFIDExtractor.FFIDFile

import java.io.File
import java.nio.file.Path
import java.util.UUID

class S3UtilsTest extends AnyFlatSpec with MockitoSugar with EitherValues {

  "downloadFiles" should "download the files to the correct directory from the default S3 bucket and key" in {
    val s3Client = Mockito.mock(classOf[S3Client])
    val requestCaptor: ArgumentCaptor[GetObjectRequest] = ArgumentCaptor.forClass(classOf[GetObjectRequest])
    val pathCaptor: ArgumentCaptor[Path] = ArgumentCaptor.forClass(classOf[Path])
    when(s3Client.getObject(requestCaptor.capture(), pathCaptor.capture())).thenReturn(GetObjectResponse.builder.build())

    val bucket = "bucket"
    val rootDirectory = "root"
    val path = "path"
    val utils = new S3Utils(s3Client, bucket, rootDirectory)
    val ffidFile = FFIDFile(UUID.randomUUID, UUID.randomUUID, path, UUID.randomUUID)
    utils.downloadFile(ffidFile)

    val objectRequest = requestCaptor.getValue
    objectRequest.bucket should equal(bucket)
    objectRequest.key should equal(s"${ffidFile.userId}/${ffidFile.consignmentId}/${ffidFile.fileId}")

    val pathRequest = pathCaptor.getValue
    pathRequest.toString should equal(s"$rootDirectory/$path")
  }

  "downloadFiles" should "download the files to the correct directory from the specified S3 source bucket and key" in {
    val s3Client = Mockito.mock(classOf[S3Client])
    val requestCaptor: ArgumentCaptor[GetObjectRequest] = ArgumentCaptor.forClass(classOf[GetObjectRequest])
    val pathCaptor: ArgumentCaptor[Path] = ArgumentCaptor.forClass(classOf[Path])
    when(s3Client.getObject(requestCaptor.capture(), pathCaptor.capture())).thenReturn(GetObjectResponse.builder.build())

    val bucketOverride = "sourcebucket"
    val bucketKeyOverride = "bucket/key/value"
    val rootDirectory = "root"
    val path = "path"
    val utils = new S3Utils(s3Client, bucketOverride, rootDirectory)
    val ffidFile = FFIDFile(UUID.randomUUID, UUID.randomUUID, path, UUID.randomUUID, Some(bucketOverride), Some(bucketKeyOverride))
    utils.downloadFile(ffidFile)

    val objectRequest = requestCaptor.getValue
    objectRequest.bucket should equal(bucketOverride)
    objectRequest.key should equal(bucketKeyOverride)

    val pathRequest = pathCaptor.getValue
    pathRequest.toString should equal(s"$rootDirectory/$path")
  }

  "downloadFiles" should "skip the download if the file already exists" in {
    val testFile = new File("path")
    testFile.createNewFile()
    val s3Client = Mockito.mock(classOf[S3Client])
    val utils = new S3Utils(s3Client, "bucket",".")
    val ffidFile = FFIDFile(UUID.randomUUID, UUID.randomUUID, "path", UUID.randomUUID)
    utils.downloadFile(ffidFile)

    verifyZeroInteractions(s3Client)
    testFile.delete()
  }

  "downloadFiles" should "return an error if the download fails" in {
    val s3Client = Mockito.mock(classOf[S3Client])
    val error = S3Exception.builder.message("Error downloading from S3").build
    when(s3Client.getObject(any[GetObjectRequest], any[Path])).thenThrow(error)
    val utils = new S3Utils(s3Client, "bucket", ".")
    val ffidFile = FFIDFile(UUID.randomUUID, UUID.randomUUID, "path", UUID.randomUUID)

    val res = utils.downloadFile(ffidFile)
    res.left.value.getMessage should equal("Error downloading from S3")

  }
}
