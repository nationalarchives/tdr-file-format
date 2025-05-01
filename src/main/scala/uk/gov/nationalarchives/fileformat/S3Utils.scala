package uk.gov.nationalarchives.fileformat

import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.Logger
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import uk.gov.nationalarchives.aws.utils.s3.S3Clients.s3
import uk.gov.nationalarchives.fileformat.FFIDExtractor.FFIDFile

import java.io.File
import java.nio.file.Paths
import scala.util.Try

class S3Utils(s3Client: S3Client, bucketName: String, rootDirectory: String) {

  val logger: Logger = Logger[S3Utils]

  private def s3BucketOverride(file: FFIDFile): String = file.s3SourceBucket match {
    case Some(v) => v
    case _ => bucketName
  }

  private def s3ObjectKeyOverride(file: FFIDFile): String = file.s3SourceBucketKey match {
    case Some(v) => v
    case _ => s"${file.userId}/${file.consignmentId}/${file.fileId}"
  }

  def downloadFile(file: FFIDFile): Either[Throwable, Unit] = {
    val outputPath = Paths.get(s"$rootDirectory/${file.originalPath}")
    val originalFile = new File(outputPath.toString)
    if(originalFile.exists()) {
      logger.info(s"File ${file.fileId} already exists on the lambda file system. Skipping")
      Right()
    } else {
      val request = GetObjectRequest.builder
        .bucket(s3BucketOverride(file))
        .key(s3ObjectKeyOverride(file))
        .build()

      val outputDirectory = file.originalPath.split("/").init.mkString("/")
      new File(s"$rootDirectory/$outputDirectory").mkdirs()
      val getObject = Try(s3Client.getObject(request, outputPath))
      getObject.toEither.map(_ => {
        logger.info(s"File ${file.fileId} does not exist on Lambda file system. Downloading")
      })
    }

  }

}
object S3Utils {
  def apply(): S3Utils = {
    val configFactory = ConfigFactory.load
    new S3Utils(
      s3(configFactory.getString("s3.endpoint")),
      configFactory.getString("s3.bucket"),
      configFactory.getString("root.directory")
    )
  }
}
