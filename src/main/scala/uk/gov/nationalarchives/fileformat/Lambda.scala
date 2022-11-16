package uk.gov.nationalarchives.fileformat

import com.typesafe.scalalogging.Logger
import io.circe.Printer
import io.circe.generic.auto._
import io.circe.parser.decode
import io.circe.syntax._
import uk.gov.nationalarchives.fileformat.FFIDExtractor.FFIDFile

import java.io.{InputStream, OutputStream}
import scala.io.Source
import scala.language.postfixOps

class Lambda {

  val s3Utils: S3Utils = S3Utils()
  val ffidExtractor: FFIDExtractor = FFIDExtractor()
  val logger: Logger = Logger[Lambda]

  def process(input: InputStream, output: OutputStream): Unit = {
    val body = Source.fromInputStream(input).getLines().mkString
    val ffidResult = for {
      ffidFile <- decode[FFIDFile](body)
      _ <- s3Utils.downloadFile(ffidFile)
      result <- ffidExtractor.ffidFile(ffidFile)
    } yield result.asJson.printWith(Printer.noSpaces)

    ffidResult match {
      case Left(err) => throw new RuntimeException(err.getMessage)
      case Right(outputJson) => output.write(outputJson.getBytes())
    }
  }
}
