package uk.gov.nationalarchives.fileformat

import com.typesafe.config.{Config, ConfigFactory}
import software.amazon.awssdk.core.SdkBytes
import software.amazon.awssdk.http.apache.ApacheHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.kms.KmsClient
import software.amazon.awssdk.services.kms.model.DecryptRequest
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.{ChangeMessageVisibilityRequest, ChangeMessageVisibilityResponse, DeleteMessageRequest, DeleteMessageResponse, SendMessageRequest, SendMessageResponse}

import java.net.URI
import java.nio.ByteBuffer
import java.util.Base64
import scala.jdk.CollectionConverters.MapHasAsJava
import scala.util.{Failure, Success, Try}

class AWSUtils {
  val configFactory: Config = ConfigFactory.load()

  val sqs: SqsClient = {
    val httpClient = ApacheHttpClient.builder.build
    SqsClient.builder()
      .region(Region.EU_WEST_2)
      .endpointOverride(URI.create(configFactory.getString("sqs.endpoint")))
      .httpClient(httpClient)
      .build()
  }

  val kms: KmsClient = {
    val endpoint = configFactory.getString("kms.endpoint")
    val httpClient = ApacheHttpClient.builder.build
    KmsClient.builder()
      .region(Region.EU_WEST_2)
      .endpointOverride(URI.create(endpoint))
      .httpClient(httpClient)
      .build()
  }

  def decryptValuesFromConfig(configPaths: List[String], encryptionContext: Map[String, String]): Map[String, String] = {
    val configFactory = ConfigFactory.load
    configPaths.map(path => {
      val value = configFactory.getString(path)
      val decryptedString: String = decryptValue(value, encryptionContext)
      (path, decryptedString)
    }).toMap
  }

  def decryptValue(value: String, encryptionContext: Map[String, String]): String = {
    Try {
      val decodedValue = Base64.getDecoder.decode(value)
      val decryptRequest = DecryptRequest.builder()
        .ciphertextBlob(SdkBytes.fromByteBuffer(ByteBuffer.wrap(decodedValue)))
        .encryptionContext(encryptionContext.asJava)
        .build()
      kms.decrypt(decryptRequest).plaintext().asUtf8String()
    } match {
      // Return the original value on error. This will allow us to deploy without breaking the lambdas. It can be removed once all variables are encrypted
      case Failure(_) => value
      case Success(value) => value
    }
  }

  def send(queueUrl: String, messageBody: String): SendMessageResponse = {
    sqs.sendMessage(SendMessageRequest.builder()
      .queueUrl(queueUrl)
      .messageBody(messageBody)
      .delaySeconds(0)
      .build())
  }

  def delete(queueUrl: String, receiptHandle: String): DeleteMessageResponse = {
    sqs.deleteMessage(
      DeleteMessageRequest.builder()
        .queueUrl(queueUrl)
        .receiptHandle(receiptHandle)
        .build())
  }

  def makeMessageVisible(queueUrl: String, receiptHandle: String): ChangeMessageVisibilityResponse = {
    sqs.changeMessageVisibility(
      ChangeMessageVisibilityRequest.builder
        .queueUrl(queueUrl)
        .receiptHandle(receiptHandle)
        .visibilityTimeout(0)
        .build
    )
  }
}
