package uk.gov.nationalarchives.fileformat

import com.typesafe.scalalogging.Logger

object ExceptionUtils {
  implicit class ExceptionFunctions(throwable: java.lang.Throwable) {
    val logger: Logger = Logger[Lambda]
    def stackTrace: String = {
      logger.error(throwable.getMessage, throwable)
      Some(throwable).map(_.getMessage) .getOrElse("")
    }

  }
}



