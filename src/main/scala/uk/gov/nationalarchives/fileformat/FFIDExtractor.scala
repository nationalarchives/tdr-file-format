package uk.gov.nationalarchives.fileformat

import net.logstash.logback.argument.StructuredArguments.value
import java.io.File
import java.util.UUID

import com.github.tototoshi.csv.CSVReader
import com.typesafe.scalalogging.Logger
import graphql.codegen.types.{FFIDMetadataInput, FFIDMetadataInputMatches}
import io.circe.syntax._
import software.amazon.awssdk.services.sqs.model.SendMessageResponse
import uk.gov.nationalarchives.aws.utils.SQSUtils
import uk.gov.nationalarchives.fileformat.FFIDExtractor._

import scala.sys.process._
import scala.util.Try

class FFIDExtractor(droidCommandRunner: DroidCommandRunner, config: Map[String, String]) {
  val logger: Logger = Logger[FFIDExtractor]

  def ffidFile(file: FFIDFile): Either[Throwable, FFIDMetadataInput] = {
    Try {
      //Outputs droid version to stdout
      val droidVersion = droidCommandRunner.version
      //Outputs signature version to stdout
      val signatureOutput = droidCommandRunner.signatureVersions
      val consignmentPath = s"""/tmp/${file.consignmentId}"""
      val filePath = s"$consignmentPath/${file.originalPath}"
      val outputPrefix = s"$consignmentPath/${file.fileId}"
      droidCommandRunner.createCSV(filePath, outputPrefix)
      val reader = CSVReader.open(new File(s"$outputPrefix.csv"))
      implicit class OptFunction(str: String) {
        def toOpt: Option[String] = if (str.isEmpty) Option.empty else Some(str)
      }
      val matches: List[FFIDMetadataInputMatches] = reader.all.tail.filter(o => o.length > 1 && o(1).isEmpty)
        .map(o => {
          val extension = o(9).toOpt
          val identificationBasis = o(5)
          val puid = o(14).toOpt
          FFIDMetadataInputMatches(extension, identificationBasis, puid)
        })
      if(matches.isEmpty) {
        throw new RuntimeException(s"${file.fileId} with original path ${file.originalPath} has no matches")
      }
      val metadataInput = FFIDMetadataInput(file.fileId, "Droid", droidVersion, signatureOutput.droidSignatureVersion, signatureOutput.containerSignatureVersion, "pronom", matches)

      logger.info(
        "File metadata with {} matches found for file ID {} in consignment ID {}",
        value("matchCount", matches.length),
        value("fileId", file.fileId),
        value("consignmentId", file.consignmentId)
      )
      metadataInput
    }.toEither.left.map(err => {
      logger.error(
        "Error processing file ID {}' in consignment ID {}",
        value("fileId", file.fileId),
        value("consignmentId", file.consignmentId)
      )
      new RuntimeException(s"Error processing file id ${file.fileId} with original path ${file.originalPath}", err)
    })
  }
}

object FFIDExtractor {

  case class FFIDFile(consignmentId: UUID, fileId: UUID, originalPath: String)

  def apply(config: Map[String, String]): FFIDExtractor = new FFIDExtractor(DroidCommandRunner(), config)
}


