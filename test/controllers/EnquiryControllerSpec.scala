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
import config.FrontendAppConfig
import connectors.mocks.MockAuthConnector
import connectors.{ PreferencesConnector, TwoWayMessageConnector }
import models.EnquiryDetails
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
import play.api.{ Application, Configuration, Environment }
import play.mvc.Http
import uk.gov.hmrc.auth.core.AuthProvider.{ GovernmentGateway, Verify }
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals
import uk.gov.hmrc.auth.core.{ AuthConnector, AuthProviders, UnsupportedAffinityGroup }
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.{ HeaderCarrier, HttpResponse }

import scala.concurrent.Future

class EnquiryControllerSpec extends ControllerSpecBase with MockAuthConnector with I18nSupport {

  lazy val mockTwoWayMessageConnector = mock[TwoWayMessageConnector]
  lazy val mockPreferencesConnector = mock[PreferencesConnector]
  val twmGetEnquiryTypeDetailsResponse = Json.parse(s"""{
                                                       |"displayName":"P800 overpayment enquiry",
                                                       |"responseTime":"5 days",
                                                       |"taxIdName":"nino",
                                                       |"taxId":"AB123456C"
                                                       |}""".stripMargin)

  override def fakeApplication(): Application =
    new GuiceApplicationBuilder()
      .overrides(new AbstractModule with ScalaModule {
        override def configure(): Unit = {
          bind[TwoWayMessageConnector].toInstance(mockTwoWayMessageConnector)
          bind[PreferencesConnector].toInstance(mockPreferencesConnector)
          bind[AuthConnector].toInstance(mockAuthConnector)
        }
      })
      .build()

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockTwoWayMessageConnector)
    when(
      mockTwoWayMessageConnector
        .getSubmissionDetails(any[String])(any[HeaderCarrier])
    ).thenReturn(
      Future.successful(
        HttpResponse(Status.OK, Some(twmGetEnquiryTypeDetailsResponse))
      )
    )
    when(
      mockPreferencesConnector
        .getPreferredEmail(any[String])(any[HeaderCarrier])
    ).thenReturn(Future.successful("preferredEmail@test.com"))
  }

  val controller = injector.instanceOf[EnquiryController]

  // Please see integration tests for auth failure scenarios as these are handled by the ErrorHandler class
  "calling onPageLoad() for P800" should {

    "return 200 (OK) when presented with a valid Nino (HMRC-NI) enrolment from auth-client" in {
      val nino = Nino("AB123456C")
      when(
        mockPreferencesConnector
          .getPreferredEmail(any[String])(any[HeaderCarrier])
      ).thenReturn(Future.successful("preferredEmail@test.com"))
      mockAuthorise(AuthProviders(GovernmentGateway, Verify), Retrievals.nino)(
        Future.successful(Some(nino.value))
      )
      val result = call(controller.onPageLoad("p800-overpayment", None), fakeRequest)

      status(result) shouldBe Status.OK
      val document = Jsoup.parse(contentAsString(result))
      document
        .getElementsByClass("heading-large")
        .text()
        .contains("Send your message") shouldBe true
    }

    "return 401 (UNAUTHORIZED) when auth-client returns 401" in {
      when(
        mockPreferencesConnector
          .getPreferredEmail(any[String])(any[HeaderCarrier])
      ).thenReturn(Future.successful("preferredEmail@test.com"))
      mockAuthorise(AuthProviders(GovernmentGateway, Verify), Retrievals.nino)(
        Future.failed(new UnsupportedAffinityGroup(""))
      )
      val result = call(controller.onPageLoad("p800-overpayment", None), fakeRequest)

      status(result) shouldBe Status.UNAUTHORIZED
    }
  }

  "calling onPageLoad() for ct-general" should {

    "return 200 (OK) when presented with a missing Nino identifier and an enrolment other than HMRC-NI from auth-client" in {
      reset(mockPreferencesConnector)
      mockAuthorise(AuthProviders(GovernmentGateway, Verify), Retrievals.nino)(
        Future.successful(None)
      )
      val result = call(controller.onPageLoad("epaye-general", None), fakeRequest)
      verify(mockPreferencesConnector, never())
        .getPreferredEmail(any[String])(any[HeaderCarrier])
      status(result) shouldBe Status.OK
      val document = Jsoup.parse(contentAsString(result))
      document
        .getElementsByClass("heading-large")
        .text()
        .contains("Send your message") shouldBe true
    }
  }

  // Please see integration tests for auth failure scenarios as these are handled by the ErrorHandler class
  "calling onSubmit() for P800" should {
    val fakeRequestWithForm = FakeRequest(routes.EnquiryController.onSubmit(None))
    val requestWithFormData: FakeRequest[AnyContentAsFormUrlEncoded] =
      fakeRequestWithForm.withFormUrlEncodedBody(
        "enquiryType" -> "p800",
        "subject"     -> "subject",
        "telephone"   -> "07700 900077",
        "email"       -> "test@test.com",
        "taxId"       -> "AB123456C",
        "question"    -> "question"
      )

    val enquiryDetails = EnquiryDetails(
      "p800",
      "subject",
      "07700 900077",
      "test@test.com",
      "AB123456C",
      "question"
    )

    val badRequestWithFormData: FakeRequest[AnyContentAsFormUrlEncoded] =
      fakeRequestWithForm.withFormUrlEncodedBody(
        "bad"         -> "value",
        "enquiryType" -> "p800"
      )

    "return 200 (OK) when presented with a valid Nino (HMRC-NI) credentials and valid payload" in {
      val twmPostMessageResponse =
        Json.parse("""
                     |    {
                     |     "id":"5c18eb166f0000110204b160"
                     |    }""".stripMargin)

      val nino = Nino("AB123456C")
      mockAuthorise(AuthProviders(GovernmentGateway, Verify))(
        Future.successful(Some(nino.value))
      )
      when(
        mockTwoWayMessageConnector
          .postMessage(ArgumentMatchers.eq(enquiryDetails))(any[HeaderCarrier])
      ).thenReturn(
        Future.successful(
          HttpResponse(Http.Status.CREATED, Some(twmPostMessageResponse))
        )
      )
      val result = await(call(controller.onSubmit(None), requestWithFormData))
      result.header.status shouldBe Status.OK
    }

    "return 412 (PRECONDITION_FAILED) when presented with a valid Nino (HMRC-NI) credentials but with an invalid payload" in {
      val bad2wmPostMessageResponse = Json.parse("{}")
      val nino = Nino("AB123456C")
      mockAuthorise(AuthProviders(GovernmentGateway, Verify))(
        Future.successful(Some(nino.value))
      )
      when(
        mockTwoWayMessageConnector
          .postMessage(ArgumentMatchers.eq(enquiryDetails))(any[HeaderCarrier])
      ).thenReturn(
        Future.successful(
          HttpResponse(Http.Status.CREATED, Some(bad2wmPostMessageResponse))
        )
      )
      val result = await(call(controller.onSubmit(None), requestWithFormData))
      result.header.status shouldBe Status.PRECONDITION_FAILED
    }

    "return 400 (BAD_REQUEST) when presented with invalid form data" in {
      val nino = Nino("AB123456C")
      mockAuthorise(AuthProviders(GovernmentGateway, Verify))(
        Future.successful(Some(nino.value))
      )
      val result = call(controller.onSubmit(None), badRequestWithFormData)
      status(result) shouldBe Status.BAD_REQUEST
    }

    "return 412 (PRECONDITION_FAILED) when two-way-message service returns a different status than 201 (CREATED)" in {
      val nino = Nino("AB123456C")
      mockAuthorise(AuthProviders(GovernmentGateway, Verify))(
        Future.successful(Some(nino.value))
      )
      when(
        mockTwoWayMessageConnector
          .postMessage(ArgumentMatchers.eq(enquiryDetails))(any[HeaderCarrier])
      ).thenReturn(
        Future.successful(
          HttpResponse(Http.Status.CONFLICT)
        )
      )

      val result = await(call(controller.onSubmit(None), requestWithFormData))
      result.header.status shouldBe Status.PRECONDITION_FAILED
    }
  }

  "validation should" should {
    val fakeRequestWithForm = FakeRequest(routes.EnquiryController.onSubmit(None))

    "Successful" in {
      val twmPostMessageResponse = Json.parse("""
                                                |    {
                                                |     "id":"5c18eb166f0000110204b160"
                                                |    }""".stripMargin)
      val nino = Nino("AB123456C")
      mockAuthorise(AuthProviders(GovernmentGateway, Verify))(
        Future.successful(Some(nino.value))
      )

      val requestWithFormData: FakeRequest[AnyContentAsFormUrlEncoded] =
        fakeRequestWithForm.withFormUrlEncodedBody(
          "enquiryType" -> "p800",
          "subject"     -> "subject",
          "telephone"   -> "07700 900077",
          "email"       -> "test@test.com",
          "taxId"       -> "AB123456C",
          "question"    -> "question"
        )

      val enquiryDetails = EnquiryDetails(
        "p800",
        "subject",
        "07700 900077",
        "test@test.com",
        "AB123456C",
        "question"
      )

      when(
        mockTwoWayMessageConnector
          .postMessage(ArgumentMatchers.eq(enquiryDetails))(any[HeaderCarrier])
      ).thenReturn(
        Future.successful(
          HttpResponse(Http.Status.OK, Some(twmPostMessageResponse))
        )
      )

      val result = await(call(controller.onSubmit(None), requestWithFormData))
      result.header.status shouldBe Status.OK
    }

    "Unsuccessful when subject and telephone are too long" in {
      val nino = Nino("AB123456C")
      mockAuthorise(AuthProviders(GovernmentGateway, Verify))(
        Future.successful(Some(nino.value))
      )

      val requestWithFormData: FakeRequest[AnyContentAsFormUrlEncoded] =
        fakeRequestWithForm.withFormUrlEncodedBody(
          "enquiryType" -> "p800",
          "subject"     -> "a" * 66,
          "telephone"   -> "a" * 26,
          "email"       -> "test@test.com",
          "taxId"       -> "AB123456C",
          "question"    -> "test"
        )

      val result = await(call(controller.onSubmit(None), requestWithFormData))
      result.header.status shouldBe Status.BAD_REQUEST
      val document = Jsoup.parse(contentAsString(result))
      document.getElementsByClass("error-summary-list").html() contains
        """<li><a href="#subject">Subject has a maximum length of 65 characters</a></li>"""
      document.getElementsByClass("error-summary-list").html() contains
        """<li><a href="#telephone">Telephone number has a maximum length of 25 characters</a></li>"""
    }

    "Unsuccessful when email is invalid" in {
      val nino = Nino("AB123456C")
      mockAuthorise(AuthProviders(GovernmentGateway, Verify))(
        Future.successful(Some(nino.value))
      )

      val requestWithFormData: FakeRequest[AnyContentAsFormUrlEncoded] =
        fakeRequestWithForm.withFormUrlEncodedBody(
          "enquiryType" -> "p800",
          "subject"     -> "subject",
          "telephone"   -> "07700 900077",
          "email"       -> "test.test.com",
          "taxId"       -> "AB123456C",
          "question"    -> "question"
        )

      val result = await(call(controller.onSubmit(None), requestWithFormData))
      result.header.status shouldBe Status.BAD_REQUEST
      val document = Jsoup.parse(contentAsString(result))
      document.getElementsByClass("error-summary-list").html() shouldBe
        """<li><a href="#email">Enter a valid email</a></li>"""
    }
  }

  "enquiry_submitted view " should {
    import views.html.enquiry_submitted
    val env = Environment.simple()
    val testMessageId = "5c9a36c30d00008f0093aae8"
    "includes messageId in a comment if perf-test-flag is true" in {
      val configuration = Configuration.load(env) ++ Configuration.from(
        Map("perf-test-flag" -> "true")
      )
      val config = new FrontendAppConfig(configuration, env)
      enquiry_submitted(config, testMessageId, "7 days", "sa-general", None).body should include(
        s"messageId=$testMessageId"
      )
    }

    "not include messageId in a comment if perf-test-flag is false" in {
      val configuration = Configuration.load(env) ++ Configuration.from(
        Map("perf-test-flag" -> "false")
      )
      val config = new FrontendAppConfig(configuration, env)
      enquiry_submitted(
        config,
        testMessageId,
        "7 days",
        "sa-general",
        None
      ).body should not include (s"messageId=$testMessageId")
    }

    "not include messageId in a comment if perf-test-flag is missing" in {
      val config = new FrontendAppConfig(Configuration.load(env), env)
      enquiry_submitted(
        config,
        testMessageId,
        "7 days",
        "sa-general",
        None
      ).body should not include (s"messageId=$testMessageId")
    }
  }
}
