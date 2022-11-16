package uk.gov.nationalarchives.fileformat

import com.github.tototoshi.csv.CSVReader
import graphql.codegen.types.{FFIDMetadataInput, FFIDMetadataInputMatches}
import org.apache.commons.cli.{CommandLine, DefaultParser}
import uk.gov.nationalarchives.droid.command.action.{CommandFactoryImpl, CommandLineParam}
import uk.gov.nationalarchives.droid.command.context.SpringUiContext
import uk.gov.nationalarchives.droid.core.interfaces.config.RuntimeConfig
import uk.gov.nationalarchives.fileformat.DroidCommandRunner.SignatureVersions

import java.io.{File, PrintWriter, StringWriter}
class DroidCommandRunner {
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

  def cliFromArgs(args: Array[String]): CommandLine = {
    new DefaultParser().parse(CommandLineParam.options(), args)
  }

  def getCSVMatches(filePath: String, outputPrefix: String): List[FFIDMetadataInputMatches] = {
    val profileArgs = cliFromArgs(Array("-a", filePath, "-p", s"$outputPrefix.droid"))
    val exportArgs = cliFromArgs(Array("-p", s"$outputPrefix.droid", "-E", s"$outputPrefix.csv"))
    val commandFactory = factory(new StringWriter())
    commandFactory.getProfileCommand(profileArgs).execute()
    commandFactory.getExportFormatCommand(exportArgs).execute()
    val reader = CSVReader.open(new File(s"$outputPrefix.csv"))
    def toOpt(value: String): Option[String] = Option(value).filter(_.trim.nonEmpty)
    reader.all.tail.filter(o => o.length > 1 && o(1).isEmpty)
      .map(o => {
        val extension = toOpt(o(9))
        val identificationBasis = o(5)
        val puid = toOpt(o(14))
        FFIDMetadataInputMatches(extension, identificationBasis, puid)
      })
  }
}
object DroidCommandRunner {
  def apply() = new DroidCommandRunner()
  case class SignatureVersions(droidSignatureVersion: String, containerSignatureVersion: String)
}
