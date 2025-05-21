package uk.gov.nationalarchives.fileformat

import org.apache.commons.io.output.ByteArrayOutputStream

import java.io.ByteArrayInputStream

object LambdaRunner extends App {
    private val body = """{"consignmentId": "630964fc-c34e-4cbe-9c26-dd98e08f5e5b", "fileId": "b634337e-5a63-42f4-b97e-4fb9d1c1d366", "originalPath": "testfilesnoeicar/deliberately_unidentifiable_file.dat", "userId": "030cf12c-8d5d-46b9-b86a-38e0920d0e1a", "s3SourceBucket": "tdr-upload-files-cloudfront-dirty-intg", "s3SourceBucketKey": "00019017-84e2-420c-aa24-78c5f7c0438d/e04b74bf-456f-4321-bcd9-d06c79641ab3/9c20d000-dfe1-488e-b4a2-8142c4ed8147"}"""
    val inputStream = new ByteArrayInputStream(body.getBytes())
    private val output = new ByteArrayOutputStream()
    new Lambda().process(inputStream, output)
    println(output.toByteArray.map(_.toChar).mkString)
}
