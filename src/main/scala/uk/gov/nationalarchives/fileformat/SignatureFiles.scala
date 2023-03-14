package uk.gov.nationalarchives.fileformat

import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.Logger
import org.w3c.dom.Document
import uk.gov.nationalarchives.fileformat.FFIDExtractor.configFactory
import uk.gov.nationalarchives.fileformat.SignatureFiles._

import java.io._
import java.net.URI
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.HttpResponse.BodyHandlers
import java.net.http.{HttpClient, HttpRequest}
import java.nio.file.StandardOpenOption._
import java.nio.file.{Path, Paths}
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import scala.util.Try

class SignatureFiles(client: HttpClient, existingFiles: List[File]) {

  def downloadSignatureFile(fileType: String): Try[Path] = Try {
    val cdnUrl = config.getString("signatures.cdn")
    existingFiles
      .find(_.getName.toLowerCase.startsWith(fileType))
      .map(f => Paths.get(f.getPath)).getOrElse {
      logger.debug("Downloading signature files")
      val fileName = if (fileType == "container") {
        s"$containerSignaturePrefix${containerSignatureVersion()}.xml"
      } else {
        if(configFactory.getBoolean("droid.pin")) {
          s"$droidSignaturePrefix${configFactory.getString("droid.version")}.xml"
        } else {
          s"$droidSignaturePrefix${droidSignatureVersion()}.xml"
        }
      }

      val path = Paths.get(s"$rootDirectory/$fileName")
      val uri = new URI(s"$cdnUrl/$fileName")
      val request = HttpRequest.newBuilder.uri(uri).GET().build()
      client.send(request, BodyHandlers.ofFile(path, TRUNCATE_EXISTING, WRITE, CREATE_NEW)).body()
    }
  }

  private def containerSignatureVersion(): String = {
    val uri = new URI(s"$nationalArchivesUrl/pronom/container-signature.xml")
    val request = HttpRequest.newBuilder.uri(uri).GET().build()
    val lastModifiedHeaderValue = client.send(request, BodyHandlers.ofString()).headers().firstValue("last-modified").get()
    val lastModified = LocalDateTime.parse(lastModifiedHeaderValue, DateTimeFormatter.RFC_1123_DATE_TIME)
    DateTimeFormatter.ofPattern("yyyyMMdd").format(lastModified)
  }

  private def droidSignatureVersionRequest: Array[Byte] = {
    val doc: Document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument()
    val envelope = doc.createElement("soap:Envelope")
    val body = doc.createElement("soap:Body")
    val sigFile = doc.createElement("getSignatureFileVersionV1")
    sigFile.setAttribute("xmlns", "http://pronom.nationalarchives.gov.uk")
    body.appendChild(sigFile)
    envelope.setAttribute("xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance")
    envelope.setAttribute("xmlns:xsd", "http://www.w3.org/2001/XMLSchema")
    envelope.setAttribute("xmlns:soap", "http://schemas.xmlsoap.org/soap/envelope/")
    envelope.appendChild(body)
    doc.appendChild(envelope)
    val baos = new ByteArrayOutputStream()
    TransformerFactory.newInstance().newTransformer().transform(new DOMSource(doc), new StreamResult(baos))
    baos.toByteArray
  }

  private def droidSignatureVersion(): String = {
    val url = new URI(s"$nationalArchivesUrl/pronom/service.asmx")
    val publisher = BodyPublishers.ofByteArray(droidSignatureVersionRequest)
    val request: HttpRequest = HttpRequest.newBuilder
      .uri(url)
      .POST(publisher)
      .header("Content-Type", "text/xml;charset=UTF-8")
      .header("SOAPAction", "http://pronom.nationalarchives.gov.uk:getSignatureFileVersionV1In")
      .build()

    val body = client.send[String](request, BodyHandlers.ofString()).body()
    val builder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
    val parsed = builder.parse(new ByteArrayInputStream(body.getBytes()))
    parsed.getElementsByTagName("Version").item(1).getTextContent
  }
}
object SignatureFiles {
  val config: Config = ConfigFactory.load()
  private val containerSignaturePrefix = "container-signature-"
  private val droidSignaturePrefix = "DROID_SignatureFile_V"
  val rootDirectory: String = config.getString("root.directory")
  val logger: Logger = Logger[SignatureFiles]
  private val nationalArchivesUrl = config.getString("signatures.nationalArchivesUrl")


  def apply(): SignatureFiles = {
    val filter: FilenameFilter = (_: File, name: String) =>
      name.startsWith(containerSignaturePrefix) || name.startsWith(droidSignaturePrefix)
    val existingFiles = new File(rootDirectory).listFiles(filter).toList
    new SignatureFiles(HttpClient.newHttpClient(), existingFiles)
  }
}
