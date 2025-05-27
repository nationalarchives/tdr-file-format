package uk.gov.nationalarchives.fileformat

import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.Logger
import uk.gov.nationalarchives.fileformat.SignatureFiles._

import java.io._
import java.net.URI
import java.net.http.HttpResponse.BodyHandlers
import java.net.http.{HttpClient, HttpRequest}
import java.nio.file.StandardOpenOption._
import java.nio.file.{Path, Paths}
import scala.util.Try

class SignatureFiles(client: HttpClient, existingFiles: List[File]) {

  def downloadSignatureFile(fileType: String): Try[Path] = Try {
    val cdnUrl = config.getString("signatures.cdn")
    existingFiles
      .find(_.getName.toLowerCase.startsWith(fileType))
      .map(f => Paths.get(f.getPath)).getOrElse {
      logger.debug("Downloading signature files")
      val fileName = if (fileType == "container") {
        s"$containerSignaturePrefix${config.getString("containers.version")}.xml"
      } else {
        s"$droidSignaturePrefix${config.getString("droid.version")}.xml"
      }

      val path = Paths.get(s"$rootDirectory/$fileName")
      val uri = new URI(s"$cdnUrl/$fileName")
      val request = HttpRequest.newBuilder.uri(uri).GET().build()
      client.send(request, BodyHandlers.ofFile(path, TRUNCATE_EXISTING, WRITE, CREATE_NEW)).body()
    }
  }
}
object SignatureFiles {
  val config: Config = ConfigFactory.load()
  private val containerSignaturePrefix = "container-signature-"
  private val droidSignaturePrefix = "DROID_SignatureFile_V"
  private val rootDirectory: String = config.getString("root.directory")
  val logger: Logger = Logger[SignatureFiles]

  def apply(): SignatureFiles = {
    val filter: FilenameFilter = (_: File, name: String) =>
      name.startsWith(containerSignaturePrefix) || name.startsWith(droidSignaturePrefix)
    val existingFiles = new File(rootDirectory).listFiles(filter).toList
    new SignatureFiles(HttpClient.newHttpClient(), existingFiles)
  }
}
