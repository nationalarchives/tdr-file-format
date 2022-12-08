package uk.gov.nationalarchives.fileformat

import graphql.codegen.types.FFIDMetadataInput
import io.circe.Printer
import io.circe.generic.auto._
import io.circe.parser.decode
import io.circe.syntax._
import uk.gov.nationalarchives.fileformat.FFIDExtractor.FFIDFile
import uk.gov.nationalarchives.fileformat.Lambda.FFIDResult

import java.io.{InputStream, OutputStream}
import scala.io.Source
import scala.language.postfixOps

class Lambda {

  val s3Utils: S3Utils = S3Utils()

  private val ffidExtractor: FFIDExtractor = FFIDExtractor()

  private def extractFFID(ffidFile: FFIDFile): Either[Throwable, FFIDMetadataInput] = for {
    _ <- s3Utils.downloadFile(ffidFile)
    metadata <- ffidExtractor.ffidFile(ffidFile)
  } yield metadata

  def process(inputStream: InputStream, outputStream: OutputStream): Unit = {
    val inputString = Source.fromInputStream(inputStream).mkString
    (for {
      ffidFile <- decode[FFIDFile](inputString)
      extractedFFID <- extractFFID(ffidFile)
    } yield extractedFFID) match {
      case Left(error) => throw error
      case Right(ffidResult) => outputStream.write(FFIDResult(ffidResult).asJson.printWith(Printer.noSpaces).getBytes())
    }
  }
}
object Lambda {
  case class FFIDResult(fileFormat: FFIDMetadataInput)
}
