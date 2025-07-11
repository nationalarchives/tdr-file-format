package uk.gov.nationalarchives.fileformat

import com.typesafe.scalalogging.Logger

import java.io._
import java.nio.file.{Path, Paths}

class SignatureFiles(existingFiles: List[File]) {

  def findSignatureFile(fileType: String): Path =
    existingFiles
      .find(_.getName.toLowerCase.startsWith(fileType))
      .map(f => Paths.get(f.getPath)).getOrElse(throw new FileNotFoundException(s"Signature file for $fileType not found locally."))

}
object SignatureFiles {
  private val containerSignaturePrefix = "container-signature-"
  private val droidSignaturePrefix = "DROID_SignatureFile_V"
  val logger: Logger = Logger[SignatureFiles]

  def apply(): SignatureFiles = {
    val filter: FilenameFilter = (_: File, name: String) =>
      name.startsWith(containerSignaturePrefix) || name.startsWith(droidSignaturePrefix)
    val existingFiles = new File(getClass.getResource("/").toURI).listFiles(filter).toList
    new SignatureFiles(existingFiles)
  }
}