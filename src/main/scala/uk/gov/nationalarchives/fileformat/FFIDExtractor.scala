package uk.gov.nationalarchives.fileformat

import net.logstash.logback.argument.StructuredArguments.value
import java.io.File
import java.util.UUID

import com.github.tototoshi.csv.CSVReader
import com.typesafe.scalalogging.Logger
import graphql.codegen.types.{FFIDMetadataInputMatches, FFIDMetadataInputValues}
import io.circe.syntax._
import software.amazon.awssdk.services.sqs.model.SendMessageResponse
import uk.gov.nationalarchives.aws.utils.sqs.SQSUtils
import uk.gov.nationalarchives.fileformat.FFIDExtractor._

import scala.sys.process._
import scala.util.Try

class FFIDExtractor(sqsUtils: SQSUtils, config: Map[String, String]) {
  val sendMessage: String => SendMessageResponse = sqsUtils.send(config("sqs.queue.output"), _)
  val logger: Logger = Logger[FFIDExtractor]

  def ffidFile(file: FFIDFile): Either[Throwable, FFIDMetadataInputValues] = {
    Try {
      val efsRootLocation = config("efs.root.location")
      val command = s"$efsRootLocation/${config("command")}"
      //Outputs droid version to stdout
      val droidVersion = s"$command -v".!!.split("\n")(1)
      //Outputs signature version to stdout
      val signatureOutput = s"$command -x".!!.split("\n")
      val containerSignatureVersion = signatureOutput(1).split(" ").last
      val droidSignatureVersion = signatureOutput(2).split(" ").last
      val consignmentPath = s"""$efsRootLocation/${file.consignmentId}"""
      val pathWithQuotesReplaced = file.originalPath
        .replaceAll(""""""", """\\\"""")
        .replaceAll("`", "\\\\`")
      val filePath = s""""$consignmentPath/$pathWithQuotesReplaced""""
      //Adds the file to a profile and runs it. The output is a .droid profile file.
      val droidCommand = s"""$command -a  $filePath -p $consignmentPath/${file.fileId}.droid"""
      Seq("bash", "-c", droidCommand).!!
      //Exports the profile as a csv
      s"$command -p $consignmentPath/${file.fileId}.droid -E $consignmentPath/${file.fileId}.csv".!!
      val reader = CSVReader.open(new File(s"$consignmentPath/${file.fileId}.csv"))
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
      val metadataInput = FFIDMetadataInputValues(file.fileId, "Droid", droidVersion, droidSignatureVersion, containerSignatureVersion, "pronom", matches)

      sendMessage(metadataInput.asJson.noSpaces)
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

  def apply(sqsUtils: SQSUtils, config: Map[String, String]): FFIDExtractor = new FFIDExtractor(sqsUtils, config)
}


