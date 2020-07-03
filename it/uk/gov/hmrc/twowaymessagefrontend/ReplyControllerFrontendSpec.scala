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
import connectors.{ PreferencesConnector, TwoWayMessageConnector }
import controllers.ReplyController
import models.{ ReplyDetails, SubmissionDetails }
import net.codingwell.scalaguice.ScalaModule
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{ reset, when }
import org.scalatestplus.play.{ HtmlUnitFactory, OneBrowserPerSuite }
import play.api.http.Status
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{ Json, Reads }
import play.api.test.Helpers._
import play.api.{ Application, Configuration }
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.retrieve.OptionalRetrieval
import uk.gov.hmrc.http.{ HeaderCarrier, HttpResponse }
import uk.gov.hmrc.twowaymessagefrontend.util.{ ControllerSpecBase, MockAuthConnector }
import play.twirl.api.Html
import uk.gov.hmrc.auth.core.AuthProvider.{ GovernmentGateway, Verify }

import scala.concurrent.Future

class ReplyControllerFrontendSpec
    extends ControllerSpecBase with MockAuthConnector with HtmlUnitFactory with OneBrowserPerSuite {

  val preferencesConnector: PreferencesConnector = mock[PreferencesConnector]
  val twoWayMessageConnector: TwoWayMessageConnector =
    mock[TwoWayMessageConnector]

  val twmGetEnquiryTypeDetailsResponse = Json.parse(s"""{
                                                       |"displayName":"P800 overpayment enquiry",
                                                       |"responseTime":"5 days",
                                                       |"taxIdName":"nino",
                                                       |"taxId":"AB123456C"
                                                       |}""".stripMargin)

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(twoWayMessageConnector)
    when(twoWayMessageConnector.getMessages(any())(any()))
      .thenReturn(Future.successful(List()))
  }

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

  val replyController = app.injector.instanceOf[ReplyController]

  "Frontend test" should {
    "find the home page ok" in {
      mockAuthorise(
        AuthProviders(GovernmentGateway, Verify),
        OptionalRetrieval("nino", Reads.StringReads)
      )(Future.successful(Some("AB123456C")))
      mockAuthorise(AuthProviders(GovernmentGateway, Verify))(
        Future.successful(Some("AB123456C"))
      )
      when(
        twoWayMessageConnector.getSubmissionDetails(
          ArgumentMatchers.eq("sa-general")
        )(any[HeaderCarrier])
      ) thenReturn {
        Future.successful(
          HttpResponse(Status.OK, Some(twmGetEnquiryTypeDetailsResponse))
        )
      }
      when(
        preferencesConnector.getPreferredEmail(
          ArgumentMatchers.eq("5c18eb166f0000110204b160")
        )(any[HeaderCarrier])
      ) thenReturn {
        Future.successful("email@dummy.com")
      }
      when(
        twoWayMessageConnector.getLatestMessage(
          ArgumentMatchers.eq("5c18eb166f0000110204b160")
        )(any[HeaderCarrier])
      ) thenReturn {
        Future.successful(Some(Html("latest")))
      }

      when(
        twoWayMessageConnector.getPreviousMessages(
          ArgumentMatchers.eq("5c18eb166f0000110204b160")
        )(any[HeaderCarrier])
      ) thenReturn {
        Future.successful(Some(Html("previous")))
      }
      val result = await(
        call(
          replyController.onPageLoad("sa-general", "5c18eb166f0000110204b160"),
          fakeRequest
        )
      )
      result.header.status mustBe (200)
    }

    "Send a valid reply message" in {
      import org.mockito.Mockito._

      val enquiryType = "sa-general"

      mockAuthorise(AuthProviders(GovernmentGateway, Verify))(
        Future.successful(Some("AB123456C"))
      )

      val replyDetails = ReplyDetails("A question from the customer")

      when(
        twoWayMessageConnector.getLatestMessage(
          ArgumentMatchers.eq("5c18eb166f0000110204b161")
        )(any[HeaderCarrier])
      ) thenReturn {
        Future.successful(Some(Html("latest")))
      }

      when(
        twoWayMessageConnector.getPreviousMessages(
          ArgumentMatchers.eq("5c18eb166f0000110204b161")
        )(any[HeaderCarrier])
      ) thenReturn {
        Future.successful(Some(Html("previous")))
      }

      when(
        twoWayMessageConnector.getSubmissionDetails(
          ArgumentMatchers.eq(enquiryType)
        )(any[HeaderCarrier])
      ) thenReturn {
        Future.successful(
          HttpResponse(Status.OK, Some(twmGetEnquiryTypeDetailsResponse))
        )
      }

      when(
        twoWayMessageConnector.postReplyMessage(
          ArgumentMatchers.eq(replyDetails),
          ArgumentMatchers.eq(enquiryType),
          ArgumentMatchers.eq("5c18eb166f0000110204b161")
        )(any[HeaderCarrier])
      ) thenReturn {
        val x =
          Json.parse("""{ "id":"5c18eb166f0000110204b161" }""".stripMargin)
        Future.successful(HttpResponse(Status.CREATED, Some(x)))
      }

      go to s"http://localhost:$port/two-way-message-frontend/message/customer/sa-general/5c18eb166f0000110204b161/reply#reply-input"
      textArea("reply-input").value = "A question from the customer"
      click on find(id("submit")).value
      eventually {
        pageSource must include(
          "HMRC received your message and will aim to reply within"
        )
      }
    }

    "Not send a reply message and show an error page when a call to two-way-message getEnquiryTypeDetails returns FORBIDDEN" in {
      import org.mockito.Mockito._

      val enquiryType = "sa-general"

      mockAuthorise(AuthProviders(GovernmentGateway, Verify))(
        Future.successful(Some("AB123456C"))
      )

      val replyDetails = ReplyDetails("A question from the customer")

      when(
        twoWayMessageConnector.getLatestMessage(
          ArgumentMatchers.eq("5c18eb166f0000110204b162")
        )(any[HeaderCarrier])
      ) thenReturn {
        Future.successful(Some(Html("latest")))
      }

      when(
        twoWayMessageConnector.getPreviousMessages(
          ArgumentMatchers.eq("5c18eb166f0000110204b162")
        )(any[HeaderCarrier])
      ) thenReturn {
        Future.successful(Some(Html("previous")))
      }

      when(
        twoWayMessageConnector.getSubmissionDetails(
          ArgumentMatchers.eq(enquiryType)
        )(any[HeaderCarrier])
      ) thenReturn {
        Future.successful(HttpResponse(Status.FORBIDDEN, None))
      }

      when(
        twoWayMessageConnector.postReplyMessage(
          ArgumentMatchers.eq(replyDetails),
          ArgumentMatchers.eq(enquiryType),
          ArgumentMatchers.eq("5c18eb166f0000110204b162")
        )(any[HeaderCarrier])
      ) thenReturn {
        val x =
          Json.parse("""{ "id":"5c18eb166f0000110204b162" }""".stripMargin)
        Future.successful(HttpResponse(Status.CREATED, Some(x)))
      }

      go to s"http://localhost:$port/two-way-message-frontend/message/customer/sa-general/5c18eb166f0000110204b162/reply#reply-input"
      textArea("reply-input").value = "A question from the customer"
      click on find(id("submit")).value
      eventually { pageSource must include("Not authenticated") }
      verify(twoWayMessageConnector, never()).postReplyMessage(
        any[ReplyDetails],
        any[String],
        any[String]
      )(any[HeaderCarrier])
    }

    "Not send a reply message and show an error page when a call to two-way-message getEnquiryTypeDetails returns NOT_FOUND" in {
      import org.mockito.Mockito._

      val enquiryType = "sa-general"

      mockAuthorise(AuthProviders(GovernmentGateway, Verify))(
        Future.successful(Some("AB123456C"))
      )

      val replyDetails = ReplyDetails("A question from the customer")

      when(
        twoWayMessageConnector.getLatestMessage(
          ArgumentMatchers.eq("5c18eb166f0000110204b163")
        )(any[HeaderCarrier])
      ) thenReturn {
        Future.successful(Some(Html("latest")))
      }

      when(
        twoWayMessageConnector.getPreviousMessages(
          ArgumentMatchers.eq("5c18eb166f0000110204b163")
        )(any[HeaderCarrier])
      ) thenReturn {
        Future.successful(Some(Html("previous")))
      }

      when(
        twoWayMessageConnector.getSubmissionDetails(
          ArgumentMatchers.eq(enquiryType)
        )(any[HeaderCarrier])
      ) thenReturn {
        Future.successful(HttpResponse(Status.NOT_FOUND, None))
      }

      go to s"http://localhost:$port/two-way-message-frontend/message/customer/sa-general/5c18eb166f0000110204b163/reply#reply-input"
      textArea("reply-input").value = "A question from the customer"
      click on find(id("submit")).value
      eventually { pageSource must include("Unknown enquiry type") }
      verify(twoWayMessageConnector, never()).postReplyMessage(
        any[ReplyDetails],
        any[String],
        any[String]
      )(any[HeaderCarrier])
    }

    "Not send a reply and show an error page when a call to two-way-message getEnquiryTypeDetails returns an invalid response" in {
      import org.mockito.Mockito._

      val enquiryType = "sa-general"

      mockAuthorise(AuthProviders(GovernmentGateway, Verify))(
        Future.successful(Some("AB123456C"))
      )

      val replyDetails = ReplyDetails("A question from the customer")

      when(
        twoWayMessageConnector.getLatestMessage(
          ArgumentMatchers.eq("5c18eb166f0000110204b167")
        )(any[HeaderCarrier])
      ) thenReturn {
        Future.successful(Some(Html("latest")))
      }

      when(
        twoWayMessageConnector.getPreviousMessages(
          ArgumentMatchers.eq("5c18eb166f0000110204b167")
        )(any[HeaderCarrier])
      ) thenReturn {
        Future.successful(Some(Html("previous")))
      }

      when(
        twoWayMessageConnector.getSubmissionDetails(
          ArgumentMatchers.eq(enquiryType)
        )(any[HeaderCarrier])
      ) thenReturn {
        Future.successful(
          HttpResponse(
            Status.OK,
            Some(Json.parse("""{"invalid": "json body"}"""))
          )
        )
      }

      go to s"http://localhost:$port/two-way-message-frontend/message/customer/sa-general/5c18eb166f0000110204b167/reply#reply-input"
      textArea("reply-input").value = "A question from the customer"
      click on find(id("submit")).value
      eventually { pageSource must include("Unknown enquiry type") }
      verify(twoWayMessageConnector, never()).postReplyMessage(
        any[ReplyDetails],
        any[String],
        any[String]
      )(any[HeaderCarrier])
    }

    "Show an error page when a call to two-way-message createCustomerResponse returns an invalid response" in {
      import org.mockito.Mockito._

      val enquiryType = "sa-general"

      mockAuthorise(AuthProviders(GovernmentGateway, Verify))(
        Future.successful(Some("AB123456C"))
      )

      val replyDetails = ReplyDetails("A question from the customer")

      when(
        twoWayMessageConnector.getLatestMessage(
          ArgumentMatchers.eq("5c18eb166f0000110204b164")
        )(any[HeaderCarrier])
      ) thenReturn {
        Future.successful(Some(Html("latest")))
      }

      when(
        twoWayMessageConnector.getPreviousMessages(
          ArgumentMatchers.eq("5c18eb166f0000110204b164")
        )(any[HeaderCarrier])
      ) thenReturn {
        Future.successful(Some(Html("previous")))
      }

      when(
        twoWayMessageConnector.getSubmissionDetails(
          ArgumentMatchers.eq(enquiryType)
        )(any[HeaderCarrier])
      ) thenReturn {
        Future.successful(
          HttpResponse(Status.OK, Some(twmGetEnquiryTypeDetailsResponse))
        )
      }

      when(
        twoWayMessageConnector.postReplyMessage(
          ArgumentMatchers.eq(replyDetails),
          ArgumentMatchers.eq(enquiryType),
          ArgumentMatchers.eq("5c18eb166f0000110204b164")
        )(any[HeaderCarrier])
      ) thenReturn {
        val x = Json.parse("""{ "invalid":"response" }""".stripMargin)
        Future.successful(HttpResponse(Status.CREATED, Some(x)))
      }

      go to s"http://localhost:$port/two-way-message-frontend/message/customer/sa-general/5c18eb166f0000110204b164/reply#reply-input"
      textArea("reply-input").value = "A question from the customer"
      click on find(id("submit")).value
      eventually { pageSource must include("Missing reference") }
    }
  }

}
