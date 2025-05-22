package uk.gov.nationalarchives.fileformat

import com.typesafe.scalalogging.Logger
import graphql.codegen.types.FFIDMetadataInputValues
import io.circe.Printer
import io.circe.generic.auto._
import io.circe.parser.decode
import io.circe.syntax._
import uk.gov.nationalarchives.droid.internal.api.DroidAPI
import uk.gov.nationalarchives.fileformat.FFIDExtractor.FFIDFile
import uk.gov.nationalarchives.fileformat.Lambda.FFIDResult

import java.io.{InputStream, OutputStream}
import scala.io.Source
import scala.language.postfixOps
import scala.util.{Failure, Success}

class Lambda(api: DroidAPI) {
  private val ffidExtractor: FFIDExtractor = FFIDExtractor(api)

  private def extractFFID(ffidFile: FFIDFile): Either[Throwable, FFIDMetadataInputValues] = for {
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
  private val signatureFiles = SignatureFiles()
  val logger: Logger = Logger[Lambda]

  def apply(): Lambda = {
    val api: DroidAPI = (for {
      containerPath <- signatureFiles.downloadSignatureFile("container")
      sigPath <- signatureFiles.downloadSignatureFile("droid")
    } yield DroidAPI.builder()
      .containerSignature(containerPath)
      .binarySignature(sigPath)
      .build()) match {
      case Failure(exception) =>
        logger.error("Error getting the droid API", exception)
        throw new RuntimeException(exception.getMessage)
      case Success(api) => api
    }
    new Lambda(api)
  }

  case class FFIDResult(fileFormat: FFIDMetadataInputValues)
}
