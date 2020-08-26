package uk.gov.nationalarchives.fileformat

object ExceptionUtils {
  implicit class ExceptionFunctions(throwable: java.lang.Throwable) {
    private def throwableToString(t: Throwable) = s"${t.getMessage}\n${t.getStackTrace.map(_.toString).mkString("\n")}"
    def stackTrace: String = Some(throwable).map(throwableToString) .getOrElse("")

  }
}



