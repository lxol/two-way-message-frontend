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

package uk.gov.hmrc.twowaymessagefrontend

import com.google.inject.AbstractModule
import connectors.{PreferencesConnector, TwoWayMessageConnector}
import controllers.EnquiryController
import models.{EnquiryDetails, SubmissionDetails}
import net.codingwell.scalaguice.ScalaModule
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, when}
import org.scalatestplus.play.{HtmlUnitFactory, OneBrowserPerSuite}
import play.api.http.Status
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{Json, Reads}
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.api.{Application, Configuration}
import play.twirl.api.Html
import uk.gov.hmrc.auth.core.AuthProvider.GovernmentGateway
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.retrieve.OptionalRetrieval
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.twowaymessagefrontend.util.{ControllerSpecBase, MockAuthConnector}

import scala.concurrent.Future

class EnquiryControllerFrontendSpec extends ControllerSpecBase  with MockAuthConnector with HtmlUnitFactory with OneBrowserPerSuite {


  val preferencesConnector: PreferencesConnector = mock[PreferencesConnector]
  val twoWayMessageConnector: TwoWayMessageConnector = mock[TwoWayMessageConnector]

  val twmGetEnquiryTypeDetailsResponse = Json.parse(s"""{
                                                       |"displayName":"P800 overpayment enquiry",
                                                       |"responseTime":"7 days",
                                                       |"taxIdName":"nino",
                                                       |"taxId":"AB123456C"
                                                       |}""".stripMargin)

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(twoWayMessageConnector)
    when(twoWayMessageConnector.getMessages(any())(any())).thenReturn(Future.successful(List()))
    when(twoWayMessageConnector.getSubmissionDetails(any[String])(any[HeaderCarrier])).thenReturn(
      Future.successful(HttpResponse(Status.OK, Some(twmGetEnquiryTypeDetailsResponse))))
  }

  override def fakeApplication(): Application = {
    new GuiceApplicationBuilder()
      .configure(Configuration("metrics.enabled" -> false))
      .overrides(new AbstractModule with ScalaModule {
        override def configure(): Unit = {
          bind[AuthConnector].toInstance(mockAuthConnector)
          bind[PreferencesConnector].toInstance(preferencesConnector)
          bind[TwoWayMessageConnector].toInstance(twoWayMessageConnector)
        }
      }).build()
  }

  private val enquiryController = app.injector.instanceOf[EnquiryController]

  "Frontend test" should {

    "find the home page ok" in {
      mockAuthorise(AuthProviders(GovernmentGateway), OptionalRetrieval("nino", Reads.StringReads))(Future.successful(Some("AB123456C")))
      when(twoWayMessageConnector.getSubmissionDetails(ArgumentMatchers.eq("sa-general"))(any[HeaderCarrier])) thenReturn {
        Future.successful(HttpResponse(Status.OK, Some(twmGetEnquiryTypeDetailsResponse)))
      }
      when(preferencesConnector.getPreferredEmail(ArgumentMatchers.eq("AB123456C"))(any[HeaderCarrier])) thenReturn {
        Future.successful("email@dummy.com")
      }
      val result = await(call(enquiryController.onPageLoad("sa-general"), fakeRequest))
      result.header.status mustBe 200
    }

    "Send a valid P800 related message" in {
      import org.mockito.Mockito._
      mockAuthorise(AuthProviders(GovernmentGateway), OptionalRetrieval("nino", Reads.StringReads))(Future.successful(Some("AB123456C")))
      mockAuthorise(AuthProviders(GovernmentGateway))(Future.successful(Some("AB123456C")))
      when(preferencesConnector.getPreferredEmail(ArgumentMatchers.eq("AB123456C"))(any[HeaderCarrier])) thenReturn {
        Future.successful("email@dummy.com")
      }
      val enquiryDetails = EnquiryDetails("sa-general", "A question", "A question from the customer", "test@dummy.com")
      when(twoWayMessageConnector.postMessage(ArgumentMatchers.eq(enquiryDetails))(any[HeaderCarrier])) thenReturn {
        val x = Json.parse("""{ "id":"5c18eb166f0000110204b160" }""".stripMargin )
        Future.successful(HttpResponse(play.api.http.Status.CREATED, Some(x)))
      }
      go to s"http://localhost:$port/two-way-message-frontend/message/sa-general/make_enquiry"
      textField("subject").value = "A question"
      emailField("email").value = "test@dummy.com"
      textArea("question").value = "A question from the customer"
      click on find(id("submit")).value
      eventually { pageSource must include ("HMRC received your message and will reply within") }
    }

    "Display an error page when a call to two-way-message getEnquiryTypeDetails returns FORBIDDEN" in {
      import org.mockito.Mockito._
      mockAuthorise(AuthProviders(GovernmentGateway), OptionalRetrieval("nino", Reads.StringReads))(Future.successful(Some("AB123456C")))
      mockAuthorise(AuthProviders(GovernmentGateway))(Future.successful(Some("AB123456C")))
      when(preferencesConnector.getPreferredEmail(ArgumentMatchers.eq("AB123456C"))(any[HeaderCarrier])) thenReturn {
        Future.successful("email@dummy.com")
      }
      when(twoWayMessageConnector.getSubmissionDetails(any[String])(any[HeaderCarrier])).thenReturn(
        Future.successful(HttpResponse(Status.FORBIDDEN)))
      val enquiryDetails = EnquiryDetails("sa-general", "A question", "A question from the customer", "test@dummy.com")
      go to s"http://localhost:$port/two-way-message-frontend/message/sa-general/make_enquiry"
      eventually { pageSource must include ("Not authenticated") }
    }

    "Display an error page when a call to two-way-message getEnquiryTypeDetails returns NOT_FOUND" in {
      import org.mockito.Mockito._
      mockAuthorise(AuthProviders(GovernmentGateway), OptionalRetrieval("nino", Reads.StringReads))(Future.successful(Some("AB123456C")))
      mockAuthorise(AuthProviders(GovernmentGateway))(Future.successful(Some("AB123456C")))
      when(preferencesConnector.getPreferredEmail(ArgumentMatchers.eq("AB123456C"))(any[HeaderCarrier])) thenReturn {
        Future.successful("email@dummy.com")
      }
      when(twoWayMessageConnector.getSubmissionDetails(any[String])(any[HeaderCarrier])).thenReturn(
        Future.successful(HttpResponse(Status.NOT_FOUND)))
      go to s"http://localhost:$port/two-way-message-frontend/message/sa-general/make_enquiry"
      eventually { pageSource must include ("Unknown enquiry type: sa-general") }
    }

    "Display an error page when a call to two-way-message getEnquiryTypeDetails returns an invalid response" in {
      import org.mockito.Mockito._
      mockAuthorise(AuthProviders(GovernmentGateway), OptionalRetrieval("nino", Reads.StringReads))(Future.successful(Some("AB123456C")))
      mockAuthorise(AuthProviders(GovernmentGateway))(Future.successful(Some("AB123456C")))
      when(preferencesConnector.getPreferredEmail(ArgumentMatchers.eq("AB123456C"))(any[HeaderCarrier])) thenReturn {
        Future.successful("email@dummy.com")
      }
      when(twoWayMessageConnector.getSubmissionDetails(any[String])(any[HeaderCarrier])).thenReturn(
        Future.successful(HttpResponse(Status.OK, Some(Json.parse("""{"invalid": "json body"}""")))))
      go to s"http://localhost:$port/two-way-message-frontend/message/sa-general/make_enquiry"
      eventually { pageSource must include ("Unknown enquiry type: sa-general") }
      verify(twoWayMessageConnector, never()).postMessage(any[EnquiryDetails])(any[HeaderCarrier])
    }

    "Display an error page when a call to two-way-message createMessage returns an invalid response" in {
      import org.mockito.Mockito._
      mockAuthorise(AuthProviders(GovernmentGateway), OptionalRetrieval("nino", Reads.StringReads))(Future.successful(Some("AB123456C")))
      mockAuthorise(AuthProviders(GovernmentGateway))(Future.successful(Some("AB123456C")))
      when(preferencesConnector.getPreferredEmail(ArgumentMatchers.eq("AB123456C"))(any[HeaderCarrier])) thenReturn {
        Future.successful("email@dummy.com")
      }
      val enquiryDetails = EnquiryDetails("sa-general", "A question", "A question from the customer", "test@dummy.com")
      when(twoWayMessageConnector.postMessage(ArgumentMatchers.eq(enquiryDetails))(any[HeaderCarrier])) thenReturn {
        val x = Json.parse("""{ "invalid":"response" }""".stripMargin )
        Future.successful(HttpResponse(play.api.http.Status.CREATED, Some(x)))
      }
      go to s"http://localhost:$port/two-way-message-frontend/message/sa-general/make_enquiry"
      textField("subject").value = "A question"
      emailField("email").value = "test@dummy.com"
      textArea("question").value = "A question from the customer"
      click on find(id("submit")).value
      eventually { pageSource must include ("Missing reference") }
    }
  }

  "Subject field" should {

    "display error message if nothing entered" in {
      stubLogin("AB123456C")
      go to s"http://localhost:$port/two-way-message-frontend/message/sa-general/make_enquiry"
      click on find(id("submit")).value
      eventually { pageSource must include ("Enter a subject") }
    }

    "should include preferences email address as we have it" in {
      mockAuthorise(AuthProviders(GovernmentGateway), OptionalRetrieval("nino", Reads.StringReads))(Future.successful(Some("AB123456C")))
      mockAuthorise(AuthProviders(GovernmentGateway))(Future.successful(Some("AB123456C")))
      when(preferencesConnector.getPreferredEmail(ArgumentMatchers.eq("AB123456C"))(any[HeaderCarrier])) thenReturn {
        Future.successful("email@dummy.com")
      }
      go to s"http://localhost:$port/two-way-message-frontend/message/sa-general/make_enquiry"
      eventually { emailField("email").value must include ("email@dummy.com") }
    }

    "content field"  should {
      "display error if nothing entered" in {
        stubLogin("AB123456C")
        go to s"http://localhost:$port/two-way-message-frontend/message/sa-general/make_enquiry"
        textArea("question").value = ""
        click on find(id("submit")).value
        eventually { pageSource must include ("Enter a question") }
      }
    }
  }

  def stubLogin(nino:String): Unit = {
    mockAuthorise(AuthProviders(GovernmentGateway), OptionalRetrieval("nino", Reads.StringReads))(Future.successful(Some(nino)))
    mockAuthorise(AuthProviders(GovernmentGateway))(Future.successful(Some("AB123456C")))
    when(preferencesConnector.getPreferredEmail(ArgumentMatchers.eq("AB123456C"))(any[HeaderCarrier])) thenReturn {
      Future.successful("")
    }
  }

  "other endpoints" should {
    "call messagesRedirect for redirection" in {
      val result = await(call(enquiryController.messagesRedirect, fakeRequest))
      result.header.status mustBe 303
    }

    "call personalAccountRedirect for redirection" in {
      val result = await(call(enquiryController.messagesRedirect, fakeRequest))
      result.header.status mustBe 303
    }
  }

  import play.api.test.Helpers.{GET, contentAsString}

  "back link" should {
    "go back to previous page when no parameter is passed" in {
      mockAuthorise(AuthProviders(GovernmentGateway), OptionalRetrieval("nino", Reads.StringReads))(Future.successful(Some("AB123456C")))
      when(preferencesConnector.getPreferredEmail(ArgumentMatchers.eq("AB123456C"))(any[HeaderCarrier])) thenReturn {
        Future.successful("email@dummy.com")
      }
      val result = call(enquiryController.onPageLoad("sa-general"), fakeRequest)
      contentAsString(result) must not include "javascript:window.history.go(-1)"
    }

    "go back to a given location when something is passed" in {
      mockAuthorise(AuthProviders(GovernmentGateway), OptionalRetrieval("nino", Reads.StringReads))(Future.successful(Some("AB123456C")))
      when(preferencesConnector.getPreferredEmail(ArgumentMatchers.eq("AB123456C"))(any[HeaderCarrier])) thenReturn {
        Future.successful("email@dummy.com")
      }
      val aFakeRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest(GET, "/message/sa-general/make_enquiry?backCode=SGVsbG8gV29ybGQ%3D")
      val result = call(enquiryController.onPageLoad("sa-general"), aFakeRequest)
      contentAsString(result) must not include "javascript:window.history.go(-1)"
    }

    "if something passed but is invalid then use defaults" in {
      mockAuthorise(AuthProviders(GovernmentGateway), OptionalRetrieval("nino", Reads.StringReads))(Future.successful(Some("AB123456C")))
      when(preferencesConnector.getPreferredEmail(ArgumentMatchers.eq("AB123456C"))(any[HeaderCarrier])) thenReturn {
        Future.successful("email@dummy.com")
      }
      val aFakeRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest(GET, "/message/sa-general/make_enquiry?backCode=hello")
      val result = call(enquiryController.onPageLoad("sa-general"), aFakeRequest)
      contentAsString(result) must not include "javascript:window.history.go(-1)"
    }
  }

}
