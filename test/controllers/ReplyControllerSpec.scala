/*
 * Copyright 2019 HM Revenue & Customs
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

package controllers

import com.google.inject.AbstractModule
import connectors.TwoWayMessageConnector
import connectors.mocks.MockAuthConnector
import models.{ReplyDetails, Identifier, MessageError}
import net.codingwell.scalaguice.ScalaModule
import org.jsoup.Jsoup
import play.api.Application
import play.api.http.Status
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.AnyContentAsFormUrlEncoded
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.auth.core.{AuthConnector, Enrolment}
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import play.api.libs.json.Json
import play.mvc.Http
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class ReplyControllerSpec extends ControllerSpecBase with MockAuthConnector {

  lazy val mockTwoWayMessageConnector = mock[TwoWayMessageConnector]

  override def fakeApplication(): Application = {

    new GuiceApplicationBuilder()
      .overrides(new AbstractModule with ScalaModule {
        override def configure(): Unit = {
          bind[TwoWayMessageConnector].toInstance(mockTwoWayMessageConnector)
          bind[AuthConnector].toInstance(mockAuthConnector)
        }
      })
      .build()
  }

  val controller = injector.instanceOf[ReplyController]

  "extractId" should {

    "retrieve an identifier from the Http Response successfully" in {
      val twmPostMessageResponse = Json.parse(
        """
          |    {
          |     "id":"5c18eb166f0000110204b160"
          |    }""".stripMargin)

      val identifier = Identifier("5c18eb166f0000110204b160")
      val result = controller.extractId(HttpResponse(Status.CREATED, Some(twmPostMessageResponse)))
      result.right.get shouldBe identifier
    }

    "retrieve an error message if an id isn't provided or malformed json" in {
      val bad2wmPostMessageResponse = Json.parse("{}")
      val result = controller.extractId(HttpResponse(Status.CREATED, Some(bad2wmPostMessageResponse)))
      result.left.get shouldBe MessageError("Missing reference")
    }
  }

  // Please see integration tests for auth failure scenarios as these are handled by the ErrorHandler class
  "calling onPageLoad()" should {

    "return 200 (OK) when presented with a valid Nino (HMRC-NI) enrolment from auth-client" in {
      val nino = Nino("AB123456C")
      mockAuthorise(Enrolment("HMRC-NI"), Retrievals.email)(Future.successful(Some(nino.value)))
      val result = call(controller.onPageLoad("P800", "messageid"), fakeRequest)
      status(result) shouldBe Status.OK
      val document = Jsoup.parse(contentAsString(result))
      // document.getElementsByClass("heading-large").text().contains("Our team will reply") shouldBe true
    }
  }

  //Please see integration tests for auth failure scenarios as these are handled by the ErrorHandler class
  "calling onSubmit()" should {
    val queueId = "p800"
    val messageId = "543e92e101000001006300c9"
    val fakeRequestWithForm = FakeRequest(routes.ReplyController.onSubmit(queueId, messageId))
    val requestWithFormData: FakeRequest[AnyContentAsFormUrlEncoded] = fakeRequestWithForm.withFormUrlEncodedBody(
      "content" -> "test content"
    )
    val replyDetails = ReplyDetails(
      "test content"
    )

    val badRequestWithFormData: FakeRequest[AnyContentAsFormUrlEncoded] = fakeRequestWithForm.withFormUrlEncodedBody(
      "bad" -> "value",
      "queue" -> "This will always be present"
    )

    "return 303 (SEE_OTHER) when presented with a valid Nino (HMRC-NI) credentials and valid payload" in {
      val twmPostMessageResponse = Json.parse(
        """
          |    {
          |     "id":"5c18eb166f0000110204b160"
          |    }""".stripMargin)

      val nino = Nino("AB123456C")
      mockAuthorise(Enrolment("HMRC-NI"))(Future.successful(Some(nino.value)))
      when(mockTwoWayMessageConnector.postReplyMessage(ArgumentMatchers.eq(replyDetails), ArgumentMatchers.eq(queueId), ArgumentMatchers.eq(messageId))(any[HeaderCarrier])).thenReturn(
        Future.successful(
          HttpResponse(Http.Status.CREATED, Some(twmPostMessageResponse))
        )
      )
      val result = await(call(controller.onSubmit(queueId, messageId), requestWithFormData))
      result.header.status shouldBe Status.SEE_OTHER
      result.header.headers("Location") shouldBe "/two-way-message-frontend/message/customer/p800/543e92e101000001006300c9/submitted?maybeId=5c18eb166f0000110204b160"
    }

    "return 303 (SEE_OTHER) when presented with a valid Nino (HMRC-NI) credentials but with an invalid payload" in {
      val bad2wmPostMessageResponse = Json.parse("{}")
      val nino = Nino("AB123456C")
      mockAuthorise(Enrolment("HMRC-NI"))(Future.successful(Some(nino.value)))
      when(mockTwoWayMessageConnector.postReplyMessage(ArgumentMatchers.eq(replyDetails), ArgumentMatchers.eq(queueId), ArgumentMatchers.eq(messageId))(any[HeaderCarrier])).thenReturn(
        Future.successful(
          HttpResponse(Http.Status.CREATED, Some(bad2wmPostMessageResponse))
        )
      )
      val result = await(call(controller.onSubmit(queueId, messageId), requestWithFormData))
      result.header.status shouldBe Status.SEE_OTHER
      result.header.headers("Location") shouldBe "/two-way-message-frontend/message/customer/p800/543e92e101000001006300c9/submitted?maybeError=Missing+reference"
    }

    "return 400 (BAD_REQUEST) when presented with invalid form data" in {
      val nino = Nino("AB123456C")
      mockAuthorise(Enrolment("HMRC-NI"))(Future.successful(Some(nino.value)))
      val result = call(controller.onSubmit(queueId, messageId), badRequestWithFormData)
      status(result) shouldBe Status.BAD_REQUEST
    }

    "return 303 (SEE_OTHER) when two-way-message service returns a different status than 201 (CREATED)" in {
      val nino = Nino("AB123456C")
      mockAuthorise(Enrolment("HMRC-NI"))(Future.successful(Some(nino.value)))

      when(mockTwoWayMessageConnector.postReplyMessage(ArgumentMatchers.eq(replyDetails), ArgumentMatchers.eq(queueId), ArgumentMatchers.eq(messageId))(any[HeaderCarrier])).thenReturn(
        Future.successful(
          HttpResponse(Http.Status.CONFLICT)
        )
      )

      val result = await(call(controller.onSubmit(queueId, messageId), requestWithFormData))
      result.header.status shouldBe Status.SEE_OTHER
      result.header.headers("Location") shouldBe "/two-way-message-frontend/message/customer/p800/543e92e101000001006300c9/submitted?maybeError=Error+sending+reply+details"
    }
  }

}
