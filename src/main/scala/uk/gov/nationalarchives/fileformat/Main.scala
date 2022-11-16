package uk.gov.nationalarchives.fileformat

import org.apache.commons.cli.{DefaultParser, GnuParser}
import uk.gov.nationalarchives.droid.command.DroidCommandLine
import uk.gov.nationalarchives.droid.command.action.{CommandFactoryImpl, CommandLineParam, DisplayDefaultSignatureFileVersionCommand, ProfileRunCommand, VersionCommand}
import uk.gov.nationalarchives.droid.command.context.SpringUiContext
import uk.gov.nationalarchives.droid.container.httpservice.ContainerSignatureHttpService
import uk.gov.nationalarchives.droid.core.interfaces.config.{DroidGlobalConfig, RuntimeConfig}
import uk.gov.nationalarchives.droid.core.interfaces.signature.{SignatureFileInfo, SignatureManager, SignatureType, SignatureUpdateService}
import uk.gov.nationalarchives.droid.profile.{ProfileContextLocator, ProfileManagerImpl}
import uk.gov.nationalarchives.droid.signature.{PronomSignatureService, SignatureManagerImpl}

import java.io.{BufferedOutputStream, BufferedWriter, ByteArrayOutputStream, PrintStream, PrintWriter, StringWriter}
import java.nio.file.Path
import java.util
import scala.jdk.CollectionConverters._

object Main extends App {

  val factory: StringWriter => CommandFactoryImpl = { writer =>
    RuntimeConfig.configureRuntimeEnvironment()
    new CommandFactoryImpl(SpringUiContext.getInstance(), new PrintWriter(writer))
  }

  def version: String = {
    val writer = new StringWriter()
    factory(writer).getVersionCommand.execute()
    writer.toString.trim
  }

  def signatureVersions: SignatureVersions = {
    val writer = new StringWriter()
    factory(writer).getDisplayDefaultSignatureVersionCommand.execute()
    val signatureOutput = writer.toString.trim.split("\n")
    val containerSignatureVersion = signatureOutput(0).split(" ").last
    val droidSignatureVersion = signatureOutput(1).split(" ").last
    SignatureVersions(droidSignatureVersion, containerSignatureVersion)
  }

  case class SignatureVersions(droidSignatureVersion: String, containerSignatureVersion: String)
  new Lambda().process(null, null)
}
