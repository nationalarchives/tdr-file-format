package uk.gov.nationalarchives.fileformat

import org.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfterEach, EitherValues}
import org.scalatest.flatspec.AnyFlatSpec

import scala.sys.process._

class FileSpec extends AnyFlatSpec with BeforeAndAfterEach  with MockitoSugar with EitherValues {
  override def afterEach(): Unit = {
    "rm -f ./src/test/resources/testfiles/originalPath".!
    "rm -f ./src/test/resources/testfiles/result.csv".!
    "rm -rf ./src/test/resources/testfiles/f0a73877-6057-4bbb-a1eb-7c7b73cab586".!
    "rm -rf ./src/test/resources/testfiles/acea5919-25a3-4c6b-8908-fa47cc77878f.csv".!
  }
}
