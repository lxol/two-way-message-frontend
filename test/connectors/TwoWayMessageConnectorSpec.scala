/*
 * Copyright 2020 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package connectors

import assets.Fixtures
import base.SpecBase
import com.google.inject.AbstractModule
import models.MessageFormat._
import models._
import net.codingwell.scalaguice.ScalaModule
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.{ any, _ }
import org.mockito.Mockito.when
import org.scalatest.concurrent.ScalaFutures
import play.api.Mode.Mode
import play.api.http.Status
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{ Json, Writes }
import play.api.{ Application, Mode }
import play.mvc.Http
import play.twirl.api.Html
import uk.gov.hmrc.http.{ HeaderCarrier, HttpReads, HttpResponse }
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.{ ExecutionContext, Future }

class TwoWayMessageConnectorSpec extends SpecBase with Fixtures {

  lazy implicit val hc = new HeaderCarrier()
  lazy val mockHttpClient = mock[HttpClient]

  override def fakeApplication(): Application =
    new GuiceApplicationBuilder()
      .overrides(new AbstractModule with ScalaModule {
        override def configure(): Unit = {
          bind[Mode].toInstance(Mode.Test)
          bind[HttpClient].toInstance(mockHttpClient)
        }
      })
      .build()

  val twoWayMessageConnector = injector.instanceOf[TwoWayMessageConnector]

  "postMessage" should {

    val twmPostMessageResponse =
      Json.parse("""
                   |    {
                   |     "id":"5c18eb166f0000110204b160"
                   |    }""".stripMargin)

    val details = EnquiryDetails(
      "some-enquiry-type",
      "my subject",
      "my question",
      "email@test.com",
      "07700900077",
      "AB123456C"
    )

    val message = TwoWayMessage(
      ContactDetails(details.email, Some(details.telephone)),
      details.subject,
      details.question
    )

    "respond with a mongo id after a successful call to two-way-message service results in a message creation from a valid payload" in {

      when(
        mockHttpClient.POST[TwoWayMessage, HttpResponse](
          any[String],
          ArgumentMatchers.eq(message),
          any[Seq[(String, String)]]
        )(
          any[Writes[TwoWayMessage]],
          any[HttpReads[HttpResponse]],
          any[HeaderCarrier],
          any[ExecutionContext]
        )
      ).thenReturn(
        Future.successful(
          HttpResponse(Http.Status.CREATED, Some(twmPostMessageResponse))
        )
      )

      val result = await(twoWayMessageConnector.postMessage(details))
      result.status shouldBe Status.CREATED
    }

    "respond with a 504 (GATEWAY TIMEOUT) after an unsuccessful call to two-way-message service results in the error message being propagated back up the chain" in {

      when(
        mockHttpClient.POST[TwoWayMessage, HttpResponse](
          any[String],
          ArgumentMatchers.eq(message),
          any[Seq[(String, String)]]
        )(
          any[Writes[TwoWayMessage]],
          any[HttpReads[HttpResponse]],
          any[HeaderCarrier],
          any[ExecutionContext]
        )
      ).thenReturn(
        Future.successful(HttpResponse(Http.Status.GATEWAY_TIMEOUT))
      )

      val result = await(twoWayMessageConnector.postMessage(details))
      result.status shouldBe Status.GATEWAY_TIMEOUT
    }
  }

  "postReplyMessage" should {
    val twmPostMessageResponse =
      Json.parse("""
                   |    {
                   |     "id":"5c18eb166f0000110204b160"
                   |    }""".stripMargin)

    val details = EnquiryDetails(
      "some-enquiry-type",
      "my subject",
      "my question",
      "email@test.com",
      "07700 900077 ext. 2354",
      "AB123456C"
    )

    "respond with a mongo id after a successful call to two-way-message service results in a message creation from a valid payload" in {

      when(
        mockHttpClient
          .POST[TwoWayMessageReply, HttpResponse](any[String](), any(), any())(
            ArgumentMatchers.any(),
            ArgumentMatchers.any(),
            ArgumentMatchers.any(),
            ArgumentMatchers.any()
          )
      ).thenReturn(
        Future.successful(
          HttpResponse(Http.Status.CREATED, Some(twmPostMessageResponse))
        )
      )

      val result = await(
        twoWayMessageConnector.postReplyMessage(
          ReplyDetails("Hello"),
          "p800-overpayment",
          "e6e5ac52-71f1-46d7-b662-39b5c1deb1d8"
        )
      )
      result.status shouldBe Status.CREATED
    }

    "respond with a 504 (GATEWAY TIMEOUT) after an unsuccessful call to two-way-message service results in the error message being propagated back up the chain" in {

      when(
        mockHttpClient
          .POST[TwoWayMessageReply, HttpResponse](any[String](), any(), any())(
            ArgumentMatchers.any(),
            ArgumentMatchers.any(),
            ArgumentMatchers.any(),
            ArgumentMatchers.any()
          )
      ).thenReturn(
        Future.successful(
          HttpResponse(
            Http.Status.GATEWAY_TIMEOUT,
            Some(twmPostMessageResponse)
          )
        )
      )

      val result = await(
        twoWayMessageConnector.postReplyMessage(
          ReplyDetails("Hello World"),
          "p800-overpayment",
          "e6e5ac52-71f1-46d7-b662-39b5c1deb1d8"
        )
      )
      result.status shouldBe Status.GATEWAY_TIMEOUT
    }

  }

  "twoWayMessageConnector.getMessages" should {

    "respond with  a list of messages if valid input from 2wsm" in {
      val messageId = "1234567890"
      val messagesStr = conversationItems("123", "321")
      val messages: List[ConversationItem] =
        Json.parse(messagesStr).validate[List[ConversationItem]].get
      when(
        mockHttpClient.GET(endsWith(s"/message/messages-list/$messageId"))(
          rds = any[HttpReads[HttpResponse]],
          hc = any[HeaderCarrier],
          ec = any[ExecutionContext]
        )
      ).thenReturn(
        Future.successful(
          HttpResponse(200, Some(Json.parse(messagesStr)), Map.empty, None)
        )
      )

      val result: List[ConversationItem] =
        await(twoWayMessageConnector.getMessages(messageId))
      result shouldBe (messages)
    }

    "return a failed future with a json validation exception if can not parse input messages from 2wsm" in {
      val messageId = "1234567890"
      val invalidMessagesStr = "{}"
      when(
        mockHttpClient.GET(endsWith(s"/message/messages-list/$messageId"))(
          rds = any[HttpReads[HttpResponse]],
          hc = any[HeaderCarrier],
          ec = any[ExecutionContext]
        )
      ).thenReturn(
        Future.successful(
          HttpResponse(
            200,
            Some(Json.parse(invalidMessagesStr)),
            Map.empty,
            None
          )
        )
      )

      ScalaFutures.whenReady(
        twoWayMessageConnector.getMessages(messageId).failed
      ) { ex =>
        ex shouldBe an[Exception]
        ex.getMessage should include("""error.expected.jsarray""")
      }
    }

    "forward an exception from 2wsm" in {
      val messageId = "1234567890"
      val testMessage = "test exception"
      when(
        mockHttpClient.GET(endsWith(s"/message/messages-list/$messageId"))(
          rds = any[HttpReads[HttpResponse]],
          hc = any[HeaderCarrier],
          ec = any[ExecutionContext]
        )
      ).thenReturn(Future.failed(new Exception(testMessage)))

      ScalaFutures.whenReady(
        twoWayMessageConnector.getMessages(messageId).failed
      ) { ex =>
        ex shouldBe a[Exception]
        ex.getMessage should be(testMessage)
      }
    }
  }
  "twoWayMessageConnector.getLatestMessage" should {

    "respond with  a latest messages if valid input from 2wsm" in {
      val messageId = "1234567890"
      val messageStr = "top message rendered"
      when(
        mockHttpClient.GET(endsWith(s"/messages/$messageId/latest-message"))(
          rds = any[HttpReads[HttpResponse]],
          hc = any[HeaderCarrier],
          ec = any[ExecutionContext]
        )
      ).thenReturn(
        Future
          .successful(HttpResponse(200, None, Map.empty, Some(messageStr)))
      )

      val result: Option[Html] =
        await(twoWayMessageConnector.getLatestMessage(messageId))
      result shouldBe (Some(Html(messageStr)))
    }

    "forward an exception from 2wsm" in {
      val messageId = "1234567890"
      val testMessage = "test exception"
      when(
        mockHttpClient.GET(endsWith(s"/messages/$messageId/latest-message"))(
          rds = any[HttpReads[HttpResponse]],
          hc = any[HeaderCarrier],
          ec = any[ExecutionContext]
        )
      ).thenReturn(Future.failed(new Exception(testMessage)))

      ScalaFutures.whenReady(
        twoWayMessageConnector.getLatestMessage(messageId).failed
      ) { ex =>
        ex shouldBe a[Exception]
        ex.getMessage should be(testMessage)
      }
    }
  }

  "twoWayMessageConnector.getPreviousMessages" should {

    "respond with  a latest messages if valid input from 2wsm" in {
      val messageId = "1234567890"
      val messageStr = "rest of the messages rendered"
      when(
        mockHttpClient
          .GET(endsWith(s"/messages/$messageId/previous-messages"))(
            rds = any[HttpReads[HttpResponse]],
            hc = any[HeaderCarrier],
            ec = any[ExecutionContext]
          )
      ).thenReturn(
        Future
          .successful(HttpResponse(200, None, Map.empty, Some(messageStr)))
      )

      val result: Option[Html] =
        await(twoWayMessageConnector.getPreviousMessages(messageId))
      result shouldBe (Some(Html(messageStr)))
    }

    "forward an exception from 2wsm" in {
      val messageId = "1234567890"
      val testMessage = "test exception"
      when(
        mockHttpClient
          .GET(endsWith(s"/messages/$messageId/previous-messages"))(
            rds = any[HttpReads[HttpResponse]],
            hc = any[HeaderCarrier],
            ec = any[ExecutionContext]
          )
      ).thenReturn(Future.failed(new Exception(testMessage)))

      ScalaFutures.whenReady(
        twoWayMessageConnector.getPreviousMessages(messageId).failed
      ) { ex =>
        ex shouldBe a[Exception]
        ex.getMessage should be(testMessage)
      }
    }
  }
}
