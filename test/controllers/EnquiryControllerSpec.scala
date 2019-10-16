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
import config.FrontendAppConfig
import connectors.mocks.MockAuthConnector
import connectors.{PreferencesConnector, TwoWayMessageConnector}
import models.{EnquiryDetails, Identifier, MessageError, SubmissionDetails}
import net.codingwell.scalaguice.ScalaModule
import org.jsoup.Jsoup
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import play.api.http.Status
import play.api.i18n.I18nSupport
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.mvc.AnyContentAsFormUrlEncoded
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.api.{Application, Configuration, Environment}
import play.mvc.Http
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals
import uk.gov.hmrc.auth.core.{AuthConnector, Enrolment, UnsupportedAffinityGroup}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.Future

class EnquiryControllerSpec extends ControllerSpecBase with MockAuthConnector with I18nSupport {

  lazy val mockTwoWayMessageConnector = mock[TwoWayMessageConnector]
  lazy val mockPreferencesConnector = mock[PreferencesConnector]

  override def fakeApplication(): Application = {

    new GuiceApplicationBuilder()
      .overrides(new AbstractModule with ScalaModule {
        override def configure(): Unit = {
          bind[TwoWayMessageConnector].toInstance(mockTwoWayMessageConnector)
          bind[PreferencesConnector].toInstance(mockPreferencesConnector)
          bind[AuthConnector].toInstance(mockAuthConnector)
        }
      })
      .build()
  }
  when(mockTwoWayMessageConnector.getSubmissionDetails(any[String])(any[HeaderCarrier])).thenReturn(
    Future.successful(Some(SubmissionDetails("P800 overpayment enquiry","7 days"))))
  when(mockPreferencesConnector.getPreferredEmail(any[String])(any[HeaderCarrier])).thenReturn(Future.successful("preferredEmail@test.com"))

  val controller = injector.instanceOf[EnquiryController]

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
      when(mockPreferencesConnector.getPreferredEmail(any[String])(any[HeaderCarrier])).thenReturn(Future.successful("preferredEmail@test.com"))
      mockAuthorise(Enrolment("HMRC-NI"), Retrievals.nino)(Future.successful(Some(nino.value)))
      val result = call(controller.onPageLoad("p800-overpayment"), fakeRequest)

      status(result) shouldBe Status.OK
      val document = Jsoup.parse(contentAsString(result))
      document.getElementsByClass("heading-large").text().contains("Send your message") shouldBe true
    }

    "return 401 (unauthorised) when presented with a Nino that returns 401 from auth-client" in {
      val nino = Nino("AB123456C")
      when(mockPreferencesConnector.getPreferredEmail(any[String])(any[HeaderCarrier])).thenReturn(Future.successful("preferredEmail@test.com"))
      mockAuthorise(Enrolment("HMRC-NI"), Retrievals.nino)(Future.failed(new UnsupportedAffinityGroup("")))
      val result = call(controller.onPageLoad("p800-overpayment"), fakeRequest)

      status(result) shouldBe Status.UNAUTHORIZED
    }
  }

  // Please see integration tests for auth failure scenarios as these are handled by the ErrorHandler class
  "calling onSubmit()" should {
    val fakeRequestWithForm = FakeRequest(routes.EnquiryController.onSubmit())
    val requestWithFormData: FakeRequest[AnyContentAsFormUrlEncoded] = fakeRequestWithForm.withFormUrlEncodedBody(
      "enquiryType" -> "some-enquiry-type",
      "subject" -> "subject",
      "question" -> "question",
      "email" -> "test@test.com"
    )

    val enquiryDetails = EnquiryDetails(
      "some-enquiry-type",
      "subject",
      "question",
      "test@test.com"
    )

    val badRequestWithFormData: FakeRequest[AnyContentAsFormUrlEncoded] = fakeRequestWithForm.withFormUrlEncodedBody(
      "bad" -> "value",
      "enquiryType" -> "This will always be present"
    )

    "return 200 (OK) when presented with a valid Nino (HMRC-NI) credentials and valid payload" in {
      val twmPostMessageResponse = Json.parse(
        """
          |    {
          |     "id":"5c18eb166f0000110204b160"
          |    }""".stripMargin)

      val nino = Nino("AB123456C")
      mockAuthorise(Enrolment("HMRC-NI"))(Future.successful(Some(nino.value)))
      when(mockTwoWayMessageConnector.postMessage(ArgumentMatchers.eq(enquiryDetails))(any[HeaderCarrier])).thenReturn(
        Future.successful(
          HttpResponse(Http.Status.CREATED, Some(twmPostMessageResponse))
        )
      )
      val result = await(call(controller.onSubmit(), requestWithFormData))
      result.header.status shouldBe Status.OK
//      result.header.headers("Location") shouldBe "/two-way-message-frontend/message/submitted?maybeId=5c18eb166f0000110204b160"
    }

    "return 200 (OK) when presented with a valid Nino (HMRC-NI) credentials but with an invalid payload" in {
      val bad2wmPostMessageResponse = Json.parse("{}")
      val nino = Nino("AB123456C")
      mockAuthorise(Enrolment("HMRC-NI"))(Future.successful(Some(nino.value)))
      when(mockTwoWayMessageConnector.postMessage(ArgumentMatchers.eq(enquiryDetails))(any[HeaderCarrier])).thenReturn(
        Future.successful(
          HttpResponse(Http.Status.CREATED, Some(bad2wmPostMessageResponse))
        )
      )
      val result = await(call(controller.onSubmit(), requestWithFormData))
      result.header.status shouldBe Status.OK
//      result.header.headers("Location") shouldBe "/two-way-message-frontend/message/submitted?maybeError=Missing+reference"
    }

    "return 400 (BAD_REQUEST) when presented with invalid form data" in {
      val nino = Nino("AB123456C")
      mockAuthorise(Enrolment("HMRC-NI"))(Future.successful(Some(nino.value)))
      val result = call(controller.onSubmit(), badRequestWithFormData)
      status(result) shouldBe Status.BAD_REQUEST
    }

    "return 200 (OK) when two-way-message service returns a different status than 201 (CREATED)" in {
      val nino = Nino("AB123456C")
      mockAuthorise(Enrolment("HMRC-NI"))(Future.successful(Some(nino.value)))
      when(mockTwoWayMessageConnector.postMessage(ArgumentMatchers.eq(enquiryDetails))(any[HeaderCarrier])).thenReturn(
        Future.successful(
          HttpResponse(Http.Status.CONFLICT)
        )
      )

      val result = await(call(controller.onSubmit(), requestWithFormData))
      result.header.status shouldBe Status.OK
//      result.header.headers("Location") shouldBe "/two-way-message-frontend/message/submitted?maybeError=Error+sending+enquiry+details"
    }
  }

  "validation should" should {
    val fakeRequestWithForm = FakeRequest(routes.EnquiryController.onSubmit())

    "Successful" in {
      val nino = Nino("AB123456C")
      mockAuthorise(Enrolment("HMRC-NI"))(Future.successful(Some(nino.value)))

      val matchingEmails: FakeRequest[AnyContentAsFormUrlEncoded] = fakeRequestWithForm.withFormUrlEncodedBody(
        "enquiryType" -> "some-enquiry-type",
        "subject" -> "subject",
        "question" -> "question",
        "email" -> "test@test.com"
      )
      val enquiryDetails = EnquiryDetails(
        "some-enquiry-type",
        "subject",
        "question",
        "test@test.com"
      )

      when(mockTwoWayMessageConnector.postMessage(ArgumentMatchers.eq(enquiryDetails))(any[HeaderCarrier])).thenReturn(
        Future.successful(
          HttpResponse(Http.Status.CONFLICT)
        )
      )

      val result = await(call(controller.onSubmit(), matchingEmails))
      result.header.status shouldBe Status.OK
    }

    "Unsuccessful when subject is too long" in {
      val nino = Nino("AB123456C")
      mockAuthorise(Enrolment("HMRC-NI"))(Future.successful(Some(nino.value)))

      val nonMatchingEmails: FakeRequest[AnyContentAsFormUrlEncoded] = fakeRequestWithForm.withFormUrlEncodedBody(
        "enquiryType" -> "some-enquiry-type",
        "subject" -> "a" * 66,
        "question" -> "test",
        "email" -> "test@test.com"
      )

      val result = await(call(controller.onSubmit(), nonMatchingEmails))
      result.header.status shouldBe Status.BAD_REQUEST
      val document = Jsoup.parse(contentAsString(result))
      document.getElementsByClass("error-summary-list").html() shouldBe "<li><a href=\"#subject\">Subject has a maximum length of 65 characters</a></li>"
    }
  }

  "enquiry_submitted view " should {
    import views.html.enquiry_submitted
    val env = Environment.simple()
    val testMessageId = "5c9a36c30d00008f0093aae8"
    "includes messageId in a comment if perf-test-flag is true" in {
      val configuration = Configuration.load(env) ++ Configuration.from(Map("perf-test-flag" -> "true"))
      val config = new FrontendAppConfig(configuration, env)
      enquiry_submitted(config, testMessageId, "7 days").body should include(s"messageId=${testMessageId}")
    }

    "not include messageId in a comment if perf-test-flag is false" in {
      val configuration = Configuration.load(env) ++ Configuration.from(Map("perf-test-flag" -> "false"))
      val config = new FrontendAppConfig(configuration, env)
      enquiry_submitted(config, testMessageId, "7 days").body should not include(s"messageId=${testMessageId}")
    }

    "not include messageId in a comment if perf-test-flag is missing" in {
      val config = new FrontendAppConfig(Configuration.load(env), env)
      enquiry_submitted(config, testMessageId, "7 days").body  should not include(s"messageId=${testMessageId}")
    }

  }
}
