package uk.gov.nationalarchives.fileformat

import com.amazonaws.services.lambda.runtime.events.SQSEvent
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage

import scala.jdk.CollectionConverters._

object LambdaRunner extends App {
    val event = new SQSEvent()
    val body = """{"consignmentId": "907d547d-a9ed-45f3-b93c-055fc792a299", "fileId": "b634337e-5a63-42f4-b97e-4fb9d1c1d366", "originalPath": "testfilesnoeicar/deliberately_unidentifiable_file.dat", "userId": "030cf12c-8d5d-46b9-b86a-38e0920d0e1a"}"""
    val record = new SQSMessage()
    record.setBody(body)
    record.setMessageId("test")
    event.setRecords(List(record).asJava)
    new Lambda().process(event, null)
}
