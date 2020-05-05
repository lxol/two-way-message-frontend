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

import java.net.URLEncoder

import com.google.inject.AbstractModule
import connectors.{ PreferencesConnector, TwoWayMessageConnector }
import controllers.EnquiryController
import models.EnquiryDetails
import net.codingwell.scalaguice.ScalaModule
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.scalatestplus.play.{ HtmlUnitFactory, OneBrowserPerSuite }
import play.api.http.Status
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{ Json, Reads }
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.api.{ Application, Configuration }
import uk.gov.hmrc.auth.core.AuthProvider.GovernmentGateway
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.retrieve.OptionalRetrieval
import uk.gov.hmrc.crypto.{ ApplicationCrypto, PlainText }
import uk.gov.hmrc.http.{ HeaderCarrier, HttpResponse }
import uk.gov.hmrc.twowaymessagefrontend.util.{ ControllerSpecBase, MockAuthConnector }

import scala.concurrent.Future

class EnquiryControllerFrontendSpec
    extends ControllerSpecBase with MockAuthConnector with HtmlUnitFactory with OneBrowserPerSuite {

  val preferencesConnector: PreferencesConnector = mock[PreferencesConnector]
  val twoWayMessageConnector: TwoWayMessageConnector = mock[TwoWayMessageConnector]

  val twmGetEnquiryTypeDetailsResponse = Json.parse(s"""{
                                                       |"displayName":"P800 overpayment enquiry",
                                                       |"responseTime":"7 days",
                                                       |"taxIdName":"nino",
                                                       |"taxId":"AB123456C"
                                                       |}""".stripMargin)
  val enquiryDetails = EnquiryDetails(
    "sa-general",
    "A subject",
    "07700 900077",
    "test@dummy.com",
    "AB123456C",
    "A question from the customer"
  )

  override def fakeApplication(): Application =
    new GuiceApplicationBuilder()
      .configure(Configuration("metrics.enabled" -> false))
      .overrides(new AbstractModule with ScalaModule {
        override def configure(): Unit = {
          bind[AuthConnector].toInstance(mockAuthConnector)
          bind[PreferencesConnector].toInstance(preferencesConnector)
          bind[TwoWayMessageConnector].toInstance(twoWayMessageConnector)
        }
      })
      .build()

  private val enquiryController = app.injector.instanceOf[EnquiryController]
  private val crypto = app.injector.instanceOf[ApplicationCrypto]

  def encrypt(value: String): String =
    URLEncoder.encode(crypto.QueryParameterCrypto.encrypt(PlainText(value)).value, "UTF-8")

  def fillEnquiryForm(): Unit = {
    textField("subject").value = "A subject"
    textField("telephone").value = "07700 900077"
    emailField("email").value = "test@dummy.com"
    textArea("question").value = "A question from the customer"
    click on find(id("submit")).value
  }

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(twoWayMessageConnector)

    when(twoWayMessageConnector.getMessages(any())(any()))
      .thenReturn(Future.successful(List()))

    when(
      twoWayMessageConnector
        .getSubmissionDetails(any[String])(any[HeaderCarrier])
    ).thenReturn(
      Future.successful(
        HttpResponse(Status.OK, Some(twmGetEnquiryTypeDetailsResponse))
      )
    )

    mockAuthorise(
      AuthProviders(GovernmentGateway),
      OptionalRetrieval("nino", Reads.StringReads)
    )(Future.successful(Some("AB123456C")))

    mockAuthorise(AuthProviders(GovernmentGateway))(
      Future.successful(Some("AB123456C"))
    )

    when(
      preferencesConnector.getPreferredEmail(
        ArgumentMatchers.eq("AB123456C")
      )(any[HeaderCarrier])
    ) thenReturn {
      Future.successful("email@dummy.com")
    }

    when(
      twoWayMessageConnector
        .postMessage(ArgumentMatchers.eq(enquiryDetails))(any[HeaderCarrier])
    ) thenReturn {
      val x =
        Json.parse("""{ "id":"5c18eb166f0000110204b160" }""".stripMargin)
      Future.successful(HttpResponse(play.api.http.Status.CREATED, Some(x)))
    }
  }

  "Frontend test" should {

    "find the home page ok" in {

      val result =
        await(call(enquiryController.onPageLoad("sa-general", None), fakeRequest))
      result.header.status mustBe 200
    }

    "Send a valid SA-GENERAL related message and expect redirection to BTA when no return link is provided" in {

      go to s"http://localhost:$port/two-way-message-frontend/message/sa-general/make_enquiry"
      fillEnquiryForm()
      eventually {
        pageSource must include(
          "HMRC received your message and will aim to reply within"
        )
        pageSource must include(
          "<a id=\"redirect-return-link\" href=\"/business-account/messages\" target=\"_self\" data-sso=\"false\">"
        )
        pageSource must include(
          "Return to your message inbox"
        )
      }
    }

    "Send a valid SA-GENERAL related message and providing a valid encrypted return link url and a valid return link text query parameters" in {

      // 42aO70DPMFVgpFdKEsKkRfwcq7kMw0YSiwTnpGw3btGr7io%2FVG6FYu6Zek5UBMLE3fLBxbepuESbcCt29JeyBw%3D%3D
      val returnLinkUrl = encrypt("https://www.gov.uk/government/organisations/hm-revenue-customs")
      // Kyf77o%2FC%2FJ03FwkDxOfx7RhYEgOprgdATfwi8%2BJRdrQFXCPldT1Y4JbwfI%2BvbrzomwfaXOg6us5Q0wO76QDnmQ%3D%3D
      val returnLinkText = encrypt("please click here to return to where you came from")

      go to s"http://localhost:$port/two-way-message-frontend/message/sa-general/make_enquiry?returnLinkUrl=$returnLinkUrl&returnLinkText=$returnLinkText"

      fillEnquiryForm()

      eventually {
        pageSource must include(
          "<a id=\"redirect-return-link\" href=\"https://www.gov.uk/government/organisations/hm-revenue-customs\" target=\"_self\" data-sso=\"false\">")
        pageSource must include("please click here to return to where you came from")
      }
    }

    "Return a BAD REQUEST error page when providing a poorly encrypted return link url and a valid return link text query parameters" in {

      val returnLinkText = encrypt("please click here to return to where you came from")
      go to s"http://localhost:$port/two-way-message-frontend/message/sa-general/make_enquiry?returnLinkUrl=9tNUeRTIYBD0RO+T5WRO7A]==&returnLinkText=$returnLinkText"

      eventually {
        pageSource must include("Poorly encrypted return link url")
      }
    }

    "Return a BAD REQUEST error page when providing a poorly encrypted return link text and a valid return link url query parameters" in {

      val returnLinkUrl = encrypt("https://www.gov.uk/government/organisations/hm-revenue-customs")
      go to s"http://localhost:$port/two-way-message-frontend/message/sa-general/make_enquiry?returnLinkUrl=$returnLinkUrl&returnLinkText=w/PwaxV+KgqutfsU0cyrJQ=="

      eventually {
        pageSource must include("Poorly encrypted return link text")
      }
    }

    "Return a BAD REQUEST error page when providing an invalid return url (Malformed URI) query parameters" in {

      val returnLinkUrl = encrypt("htttt pss:~~~~googlel.com")
      val returnLinkText = encrypt("some clever text")

      go to s"http://localhost:$port/two-way-message-frontend/message/sa-general/make_enquiry?returnLinkUrl=$returnLinkUrl&returnLinkText=$returnLinkText"

      eventually {
        pageSource must include("Invalid return link url")
      }
    }

    "Attempting to send a valid SA-GENERAL related message with a return url query parameter without its link text counterpart should return a BAD REQUEST" in {

      go to s"http://localhost:$port/two-way-message-frontend/message/sa-general/make_enquiry?returnLinkUrl=/some-return/url"
      eventually {
        pageSource must include("Bad request - 400")
        pageSource must include("Please check that you have entered the correct web address.")
      }
    }

    "Attempting to send a valid message with a return link text query parameter without its return url counterpart should return a BAD REQUEST" in {

      go to s"http://localhost:$port/two-way-message-frontend/message/sa-general/make_enquiry?returnLinkText=some_text"
      eventually {
        pageSource must include("Bad request - 400")
        pageSource must include("Please check that you have entered the correct web address.")
      }
    }

    "Display an error page when a call to two-way-message getEnquiryTypeDetails returns FORBIDDEN" in {

      when(
        twoWayMessageConnector
          .getSubmissionDetails(any[String])(any[HeaderCarrier])
      ).thenReturn(Future.successful(HttpResponse(Status.FORBIDDEN)))

      go to s"http://localhost:$port/two-way-message-frontend/message/sa-general/make_enquiry"
      eventually { pageSource must include("Not authenticated") }
    }

    "Display an error page when a call to two-way-message getEnquiryTypeDetails returns NOT_FOUND" in {

      when(
        twoWayMessageConnector
          .getSubmissionDetails(any[String])(any[HeaderCarrier])
      ).thenReturn(Future.successful(HttpResponse(Status.NOT_FOUND)))
      go to s"http://localhost:$port/two-way-message-frontend/message/sa-general/make_enquiry"
      eventually { pageSource must include("Unknown enquiry type: sa-general") }
    }

    "Display an error page when a call to two-way-message getEnquiryTypeDetails returns an invalid response" in {

      when(
        twoWayMessageConnector
          .getSubmissionDetails(any[String])(any[HeaderCarrier])
      ).thenReturn(
        Future.successful(
          HttpResponse(
            Status.OK,
            Some(Json.parse("""{"invalid": "json body"}"""))
          )
        )
      )
      go to s"http://localhost:$port/two-way-message-frontend/message/sa-general/make_enquiry"
      eventually { pageSource must include("Unknown enquiry type: sa-general") }
      verify(twoWayMessageConnector, never())
        .postMessage(any[EnquiryDetails])(any[HeaderCarrier])
    }

    "Display an error page when a call to two-way-message createMessage returns an invalid response" in {

      when(
        twoWayMessageConnector
          .postMessage(ArgumentMatchers.eq(enquiryDetails))(any[HeaderCarrier])
      ) thenReturn {
        val x = Json.parse("""{ "invalid":"response" }""".stripMargin)
        Future.successful(HttpResponse(play.api.http.Status.CREATED, Some(x)))
      }
      go to s"http://localhost:$port/two-way-message-frontend/message/sa-general/make_enquiry"
      fillEnquiryForm()
      eventually { pageSource must include("Missing reference") }
    }
  }

  "Subject field" should {

    "display error message if nothing entered" in {
      go to s"http://localhost:$port/two-way-message-frontend/message/sa-general/make_enquiry"
      click on find(id("submit")).value
      eventually { pageSource must include("Enter a subject") }
    }

    "should include preferences email address as we have it" in {

      go to s"http://localhost:$port/two-way-message-frontend/message/sa-general/make_enquiry"
      eventually { emailField("email").value must include("email@dummy.com") }
    }

    "content field" should {
      "display error if nothing entered" in {
        go to s"http://localhost:$port/two-way-message-frontend/message/sa-general/make_enquiry"
        textArea("question").value = ""
        click on find(id("submit")).value
        eventually { pageSource must include("Enter a question") }
      }
    }
  }

  "redirection" should {

    "no longer be accessible when pointing to the p800 journey" in {

      go to s"http://localhost:$port/two-way-message-frontend/message/p800-overpaid/make_enquiry"
      eventually {
        pageSource must include(
          "Page not found - 404"
        )
      }
    }
  }

  "back link" should {
    "go back to previous page when no parameter is passed" in {

      val result = call(enquiryController.onPageLoad("sa-general", None), fakeRequest)
      contentAsString(
        result
      ) must not include "javascript:window.history.go(-1)"
    }

    "go back to a given location when something is passed" in {

      val aFakeRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest(
        GET,
        "/message/sa-general/make_enquiry?backCode=SGVsbG8gV29ybGQ%3D"
      )
      val result =
        call(enquiryController.onPageLoad("sa-general", None), aFakeRequest)
      contentAsString(
        result
      ) must not include "javascript:window.history.go(-1)"
    }

    "if something passed but is invalid then use defaults" in {

      val aFakeRequest: FakeRequest[AnyContentAsEmpty.type] =
        FakeRequest(GET, "/message/sa-general/make_enquiry?backCode=hello")
      val result =
        call(enquiryController.onPageLoad("sa-general", None), aFakeRequest)
      contentAsString(
        result
      ) must not include "javascript:window.history.go(-1)"
    }
  }
}
