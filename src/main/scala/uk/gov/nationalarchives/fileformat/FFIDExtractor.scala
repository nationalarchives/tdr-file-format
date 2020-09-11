package uk.gov.nationalarchives.fileformat

import java.io.File
import java.util.UUID

import com.github.tototoshi.csv.CSVReader
import com.typesafe.config.Config
import graphql.codegen.types.{FFIDMetadataInput, FFIDMetadataInputMatches}
import software.amazon.awssdk.services.sqs.model.SendMessageResponse
import uk.gov.nationalarchives.aws.utils.SQSUtils
import uk.gov.nationalarchives.fileformat.FFIDExtractor._
import scala.sys.process._
import io.circe.syntax._

import scala.util.Try

class FFIDExtractor(sqsUtils: SQSUtils, config: Config) {
  val sendMessage: String => SendMessageResponse = sqsUtils.send(config.getString("sqs.queue.output"), _)

  def ffidFile(file: FFIDFile): Either[String, FFIDMetadataInput] = {
    Try {
      val efsRootLocation = config.getString("efs.root.location")
      val command = s"$efsRootLocation/${config.getString("command")}"
      val droidVersion = s"$command -v".!!.split("\n")(1)
      val signatureOutput = s"$command -x".!!.split("\n")
      val containerSignatureVersion = signatureOutput(1).split(" ").last
      val droidSignatureVersion = signatureOutput(2).split(" ").last
      s"""$command -a  $efsRootLocation/${file.consignmentId}/${file.originalPath} -p $efsRootLocation/${file.fileId}.droid""".!!
      s"$command -p $efsRootLocation/${file.fileId}.droid -E $efsRootLocation/${file.fileId}.csv".!!
      val reader = CSVReader.open(new File(s"$efsRootLocation/${file.fileId}.csv"))
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
      val metadataInput = FFIDMetadataInput(file.fileId, "Droid", droidVersion, droidSignatureVersion, containerSignatureVersion, "pronom", matches)
      sendMessage(metadataInput.asJson.noSpaces)
      metadataInput
    }.toEither.left.map(err => err.stackTrace)
  }
}

object FFIDExtractor {
  case class FFIDFile(consignmentId: UUID, fileId: UUID, originalPath: String)

  implicit class ErrorFunction(err: Throwable) {
    def stackTrace: String = {
      err.printStackTrace()
      err.getMessage
    }
  }

  def apply(sqsUtils: SQSUtils, config: Config): FFIDExtractor = new FFIDExtractor(sqsUtils, config)
}


