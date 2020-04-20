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

package controllers

import com.google.inject.AbstractModule
import connectors.TwoWayMessageConnector
import connectors.mocks.MockAuthConnector
import models.{
  ConversationItem,
  Identifier,
  MessageError,
  ReplyDetails,
  SubmissionDetails
}
import net.codingwell.scalaguice.ScalaModule
import org.jsoup.Jsoup
import play.api.Application
import play.api.http.Status
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.AnyContentAsFormUrlEncoded
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.auth.core.{AuthConnector, AuthProviders, Enrolment}
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import play.api.libs.json.Json
import play.mvc.Http
import play.twirl.api.Html
import uk.gov.hmrc.auth.core.AuthProvider.GovernmentGateway
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import utils.MessageRenderer

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class ReplyControllerSpec extends ControllerSpecBase with MockAuthConnector {

  lazy val mockTwoWayMessageConnector = mock[TwoWayMessageConnector]
  lazy val mockMessageRenderer = mock[MessageRenderer]
  val twmGetEnquiryTypeDetailsResponse = Json.parse(s"""{
                                                       |"displayName":"P800 overpayment enquiry",
                                                       |"responseTime":"7 days"
                                                       |}""".stripMargin)

  when(mockMessageRenderer.renderMessage(any())).thenReturn(Html(""))
  when(mockMessageRenderer.renderMessages(any())).thenReturn(Html(""))
  when(mockTwoWayMessageConnector.getMessages(any())(any()))
    .thenReturn(List[ConversationItem]())
  when(
    mockTwoWayMessageConnector
      .getSubmissionDetails(any[String])(any[HeaderCarrier])
  ).thenReturn(
    Future.successful(
      HttpResponse(Status.OK, Some(twmGetEnquiryTypeDetailsResponse))
    )
  )
  when(mockTwoWayMessageConnector.getLatestMessage(any())(any()))
    .thenReturn(Some(Html("")))
  when(mockTwoWayMessageConnector.getPreviousMessages(any())(any()))
    .thenReturn(Some(Html("")))

  override def fakeApplication(): Application = {

    new GuiceApplicationBuilder()
      .overrides(new AbstractModule with ScalaModule {
        override def configure(): Unit = {
          bind[TwoWayMessageConnector].toInstance(mockTwoWayMessageConnector)
          bind[AuthConnector].toInstance(mockAuthConnector)
          bind[MessageRenderer].toInstance(mockMessageRenderer)
        }
      })
      .build()
  }

  val controller = injector.instanceOf[ReplyController]

  // Please see integration tests for auth failure scenarios as these are handled by the ErrorHandler class
  "calling onPageLoad()" should {

    "return 200 (OK) when presented with a valid Nino (HMRC-NI) enrolment from auth-client" in {
      val nino = Nino("AB123456C")
      mockAuthorise(AuthProviders(GovernmentGateway), Retrievals.email)(
        Future.successful(Some(nino.value))
      )
      mockAuthorise(AuthProviders(GovernmentGateway))(
        Future.successful(Some(nino.value))
      )
      val result = call(
        controller.onPageLoad("p800-overpayment", "messageid"),
        fakeRequest
      )
      status(result) shouldBe Status.OK
    }
  }

  //Please see integration tests for auth failure scenarios as these are handled by the ErrorHandler class
  "calling onSubmit()" should {
    val enquiryType = "p800-overpayment"
    val messageId = "543e92e101000001006300c9"
    val fakeRequestWithForm =
      FakeRequest(routes.ReplyController.onSubmit(enquiryType, messageId))
    val requestWithFormData: FakeRequest[AnyContentAsFormUrlEncoded] =
      fakeRequestWithForm.withFormUrlEncodedBody(
        "reply-input" -> "test content"
      )
    val replyDetails = ReplyDetails(
      "test content"
    )

    val badRequestWithFormData: FakeRequest[AnyContentAsFormUrlEncoded] =
      fakeRequestWithForm.withFormUrlEncodedBody(
        "bad" -> "value",
        "enquiryType" -> "This will always be present"
      )

    val badRequestWithEmptyFormData: FakeRequest[AnyContentAsFormUrlEncoded] =
      fakeRequestWithForm.withFormUrlEncodedBody(
        "reply-input" -> ""
      )

    "return 200 (OK) when presented with a valid Nino (HMRC-NI) credentials and valid payload" in {
      val twmPostMessageResponse =
        Json.parse("""
          |    {
          |     "id":"5c18eb166f0000110204b160"
          |    }""".stripMargin)

      val nino = Nino("AB123456C")
      mockAuthorise(AuthProviders(GovernmentGateway))(
        Future.successful(Some(nino.value))
      )
      when(
        mockTwoWayMessageConnector.postReplyMessage(
          ArgumentMatchers.eq(replyDetails),
          ArgumentMatchers.eq(enquiryType),
          ArgumentMatchers.eq(messageId)
        )(any[HeaderCarrier])
      ).thenReturn(
        Future.successful(
          HttpResponse(Http.Status.CREATED, Some(twmPostMessageResponse))
        )
      )

      val result = await(
        call(controller.onSubmit(enquiryType, messageId), requestWithFormData)
      )
      result.header.status shouldBe Status.OK
    }

    "return 200 (OK) when presented with a valid Nino (HMRC-NI) credentials but with an invalid payload" in {
      val bad2wmPostMessageResponse = Json.parse("{}")
      val nino = Nino("AB123456C")
      mockAuthorise(AuthProviders(GovernmentGateway))(
        Future.successful(Some(nino.value))
      )
      when(
        mockTwoWayMessageConnector.postReplyMessage(
          ArgumentMatchers.eq(replyDetails),
          ArgumentMatchers.eq(enquiryType),
          ArgumentMatchers.eq(messageId)
        )(any[HeaderCarrier])
      ).thenReturn(
        Future.successful(
          HttpResponse(Http.Status.CREATED, Some(bad2wmPostMessageResponse))
        )
      )
      val result = await(
        call(controller.onSubmit(enquiryType, messageId), requestWithFormData)
      )
      result.header.status shouldBe Status.OK
    }

    "return 400 (BAD_REQUEST) when presented with invalid form data" in {
      val nino = Nino("AB123456C")
      mockAuthorise(AuthProviders(GovernmentGateway))(
        Future.successful(Some(nino.value))
      )
      val result = call(
        controller.onSubmit(enquiryType, messageId),
        badRequestWithFormData
      )
      status(result) shouldBe Status.BAD_REQUEST
    }

    "return 400 (BAD_REQUEST) when presented with empty form data" in {
      val nino = Nino("AB123456C")
      mockAuthorise(AuthProviders(GovernmentGateway))(
        Future.successful(Some(nino.value))
      )
      val result = call(
        controller.onSubmit(enquiryType, messageId),
        badRequestWithEmptyFormData
      )
      status(result) shouldBe Status.BAD_REQUEST

      val document = Jsoup.parse(contentAsString(result))
      document
        .getElementsByClass("error-summary-list")
        .html() shouldBe """<li><a href="#reply-input">Please enter a value</a></li>"""
    }

    "return 200 (OK) when two-way-message service returns a different status than 201 (CREATED)" in {
      val nino = Nino("AB123456C")
      mockAuthorise(AuthProviders(GovernmentGateway))(
        Future.successful(Some(nino.value))
      )

      when(
        mockTwoWayMessageConnector.postReplyMessage(
          ArgumentMatchers.eq(replyDetails),
          ArgumentMatchers.eq(enquiryType),
          ArgumentMatchers.eq(messageId)
        )(any[HeaderCarrier])
      ).thenReturn(
        Future.successful(
          HttpResponse(Http.Status.CONFLICT)
        )
      )

      val result = await(
        call(controller.onSubmit(enquiryType, messageId), requestWithFormData)
      )
      result.header.status shouldBe Status.OK
    }
  }

  "validation should" should {
    val enquiryType = "p800-overpayment"
    val messageId = "543e92e101000001006300c9"
    val fakeRequestWithForm =
      FakeRequest(routes.ReplyController.onSubmit(enquiryType, messageId))

    "Unsuccessful when subject is too long" in {
      val nino = Nino("AB123456C")
      mockAuthorise(AuthProviders(GovernmentGateway))(
        Future.successful(Some(nino.value))
      )

      val requestWithLongContent: FakeRequest[AnyContentAsFormUrlEncoded] =
        fakeRequestWithForm.withFormUrlEncodedBody(
          "reply-input" -> "a" * (100000 + 1)
        )

      val result = await(
        call(
          controller.onSubmit(enquiryType, messageId),
          requestWithLongContent
        )
      )
      result.header.status shouldBe Status.BAD_REQUEST
      val document = Jsoup.parse(contentAsString(result))
      document
        .getElementsByClass("error-summary-list")
        .html() shouldBe "<li><a href=\"#reply-input\">Maximum length is 100,000</a></li>"
    }
  }

}
