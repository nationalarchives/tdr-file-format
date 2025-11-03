package uk.gov.nationalarchives.fileformat

import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.Logger
import uk.gov.nationalarchives.fileformat.SignatureFiles.SignatureFileType.{ContainerSignature, DroidSignature, SignatureFileType}

import java.io._
import java.nio.file.{Path, Paths}

class SignatureFiles(existingFiles: List[File]) {

  def findSignatureFile(fileType: SignatureFileType): Path = {
    existingFiles
      .find(_.getName.startsWith(fileType.toString))
      .map(f => Paths.get(f.getPath)).getOrElse(throw new FileNotFoundException(s"${fileType.toString} signature file not found locally."))
  }
}

object SignatureFiles {
  val logger: Logger = Logger[SignatureFiles]

  def apply(): SignatureFiles = {
    val filter: FilenameFilter = (_: File, name: String) =>
      name.startsWith(ContainerSignature.toString) || name.startsWith(DroidSignature.toString)
    val existingFiles = new File(getClass.getResource("/").toURI).listFiles(filter).toList
    new SignatureFiles(existingFiles)
  }

  object SignatureFileType extends Enumeration {
    private val configFactory: Config = ConfigFactory.load
    private val containerSignatureName = configFactory.getString("containers.signature.name")
    private val containerSignatureVersion = configFactory.getString("containers.version")
    private val droidSignatureName = configFactory.getString("droid.signature.name")
    private val droidSignatureVersion = configFactory.getString("droid.version")

    type SignatureFileType = Value
    val ContainerSignature: Value = Value(containerSignatureName + containerSignatureVersion)
    val DroidSignature: Value = Value(droidSignatureName + droidSignatureVersion)
  }
}
