package uk.gov.nationalarchives.fileformat


import com.typesafe.scalalogging.Logger
import io.circe.generic.auto._
import io.circe.syntax._
import software.amazon.awssdk.services.sqs.model.SendMessageResponse
import uk.gov.nationalarchives.droid.internal.api.DroidAPI
import uk.gov.nationalarchives.fileformat.FFIDExtractor._

import java.nio.file.{Path, Paths}
import java.util.UUID
import scala.jdk.CollectionConverters._
import scala.util.Try

class FFIDExtractor(awsUtils: AWSUtils, config: Map[String, String]) {
  val sigPath: Path =  Paths.get(getClass.getResource("/DROID_SignatureFile_V104.xml").getPath)
  val containerPath: Path = Paths.get(getClass.getResource("/container-signature-20220311.xml").getPath)
  val api: DroidAPI = DroidAPI.getInstance(sigPath, containerPath)
  val sendMessage: String => SendMessageResponse = awsUtils.send(config("sqs.queue.output"), _)
  val logger: Logger = Logger[FFIDExtractor]

  def ffidFile(file: FFIDFile): Either[Throwable, FFIDMetadataInput] = {
    Try {
      val tmpLocation = config("efs.root.location")
      val droidVersion = api.getDroidVersion
      val containerSignatureVersion = api.getContainerSignatureVersion
      val droidSignatureVersion = api.getBinarySignatureVersion
      val results = api.submit(Paths.get(s"$tmpLocation/${file.consignmentId}/${file.originalPath}"))


      val matches = results.getResults.asScala.toList match {
        case Nil => List(FFIDMetadataInputMatches(None, "", None))
        case results =>
          results.map(res => FFIDMetadataInputMatches(Option(res.getExtId), res.getMethod.getMethod, Option(res.getPuid)))
      }

      val metadataInput = FFIDMetadataInput(file.fileId, "Droid", droidVersion, droidSignatureVersion, containerSignatureVersion, "pronom", matches)
      println(metadataInput)
      sendMessage(metadataInput.asJson.noSpaces)
      logger.info(
        s"File metadata with ${matches.length} matches found for file ID ${file.fileId} in consignment ID ${file.consignmentId}"
      )
      metadataInput
    }.toEither.left.map(err => {
      logger.error(
        s"Error processing file ID ${file.fileId}' in consignment ID ${file.consignmentId}"
      )
      new RuntimeException(s"Error processing file id ${file.fileId} with original path ${file.originalPath}", err)
    })
  }
}

object FFIDExtractor {

  case class FFIDFile(consignmentId: UUID, fileId: UUID, originalPath: String)
  case class FFIDMetadataInput(fileId: UUID, software: String, softwareVersion: String, binarySignatureFileVersion: String, containerSignatureFileVersion: String, method: String, matches: List[FFIDMetadataInputMatches])
  case class FFIDMetadataInputMatches(extension : Option[String], identificationBasis: String, puid: Option[String])

  def apply(sqsUtils: AWSUtils, config: Map[String, String]): FFIDExtractor = new FFIDExtractor(sqsUtils, config)
}


