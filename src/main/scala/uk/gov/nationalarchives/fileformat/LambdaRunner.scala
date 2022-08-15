package uk.gov.nationalarchives.fileformat

import com.amazonaws.services.lambda.runtime.events.SQSEvent
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage

import scala.jdk.CollectionConverters._

object LambdaRunner extends App {
  val body = """{"consignmentId":  "f0a73877-6057-4bbb-a1eb-7c7b73cab586", "fileId":  "acea5919-25a3-4c6b-8908-fa47cc77878f", "originalPath" :  "originalPath.txt"}"""

  val event = new SQSEvent()
  val record = new SQSMessage()
  record.setBody(body)
  record.setMessageId("test")
  event.setRecords(List(record).asJava)
  new Lambda().process(event, null)
}
