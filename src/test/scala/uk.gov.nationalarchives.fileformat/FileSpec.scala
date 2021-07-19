package uk.gov.nationalarchives.fileformat

import org.scalatest.{BeforeAndAfterEach, Suite}

import scala.sys.process._

trait FileSpec extends BeforeAndAfterEach { this: Suite =>

  // Create the files and then try to ls them in the test script. This mimics reading from the existing file on EFS
  // and should catch problems where there are spaces in the filename.
  override def beforeEach(): Unit = {
    "mkdir -p ./src/test/resources/testfiles/f0a73877-6057-4bbb-a1eb-7c7b73cab586/rootDirectory/subDirectory".!
    "touch ./src/test/resources/testfiles/f0a73877-6057-4bbb-a1eb-7c7b73cab586/originalPath".!
    "touch ./src/test/resources/testfiles/f0a73877-6057-4bbb-a1eb-7c7b73cab586/pathwith`".!!
    Seq("bash", "-c", """touch "./src/test/resources/testfiles/f0a73877-6057-4bbb-a1eb-7c7b73cab586/rootDirectory/originalPath\"withQu'ote"""").!!
    """touch "./src/test/resources/testfiles/f0a73877-6057-4bbb-a1eb-7c7b73cab586/path with space" """.!
    "touch ./src/test/resources/testfiles/f0a73877-6057-4bbb-a1eb-7c7b73cab586/rootDirectory/subDirectory/originalPath".!

    super.beforeEach()
  }

  override def afterEach(): Unit = {
    super.afterEach()

    "rm -rf ./src/test/resources/testfiles/f0a73877-6057-4bbb-a1eb-7c7b73cab586".!
  }
}
