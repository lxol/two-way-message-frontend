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
import connectors.mocks.MockAuthConnector
import connectors.TwoWayMessageConnector
import models.{ Identifier, SubmissionDetails }
import net.codingwell.scalaguice.ScalaModule
import org.jsoup.Jsoup
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import play.api.http.Status
import play.api.i18n.I18nSupport
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.test.Helpers._
import play.api.Application
import play.api.mvc.Results.PreconditionFailed

import uk.gov.hmrc.http.{ HeaderCarrier, HttpResponse }

import scala.concurrent.Future

class BaseControllerSpec extends ControllerSpecBase with MockAuthConnector with I18nSupport {

  lazy val mockTwoWayMessageConnector = mock[TwoWayMessageConnector]
  val twmGetEnquiryTypeDetailsResponse = Json.parse(s"""{
                                                       |"displayName":"P800 overpayment enquiry",
                                                       |"responseTime":"5 days",
                                                       |"taxIdName":"nino",
                                                       |"taxId":"AB123456C"
                                                       |}""".stripMargin)

  override def fakeApplication(): Application =
    new GuiceApplicationBuilder()
      .overrides(new AbstractModule with ScalaModule {
        override def configure(): Unit =
          bind[TwoWayMessageConnector].toInstance(mockTwoWayMessageConnector)
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
  }

  val controller = injector.instanceOf[EnquiryController]

  "extractId" should {

    "retrieve an identifier from the Http Response successfully" in {
      val twmPostMessageResponse =
        Json.parse("""
                     |    {
                     |     "id":"5c18eb166f0000110204b160"
                     |    }""".stripMargin)

      val identifier = Identifier("5c18eb166f0000110204b160")
      val result = controller.extractId(
        HttpResponse(Status.CREATED, Some(twmPostMessageResponse))
      )
      result.right.get shouldBe identifier
    }

    "retrieve an error message if an id isn't provided or malformed json" in {
      val bad2wmPostMessageResponse = Json.parse("{}")
      val result = controller.extractId(
        HttpResponse(Status.CREATED, Some(bad2wmPostMessageResponse))
      )
      result.left.get shouldBe PreconditionFailed("Missing reference")
    }
  }

  "getEnquiryTypeDetails" should {

    "return submission details from the Http Response if the call to getEnquiryTypeDetails in two-way-message returns OK" in {
      when(
        mockTwoWayMessageConnector
          .getSubmissionDetails(any[String])(any[HeaderCarrier])
      ).thenReturn(
        Future.successful(
          HttpResponse(Status.OK, Some(twmGetEnquiryTypeDetailsResponse))
        )
      )
      val result =
        await(controller.getEnquiryTypeDetails("p800-overpayment")).right.get
      result shouldBe SubmissionDetails(
        "P800 overpayment enquiry",
        "5 days",
        "nino",
        "AB123456C"
      )
    }

    "return an error page from the Http Response if the call to getEnquiryTypeDetails in 2wsm returns Ok with an invalid body" in {
      when(
        mockTwoWayMessageConnector
          .getSubmissionDetails(any[String])(any[HeaderCarrier])
      ).thenReturn(
        Future.successful(
          HttpResponse(
            Status.OK,
            Some(Json.parse("""{"invalid": "json body"}"""))
          )
        )
      )
      val result =
        await(controller.getEnquiryTypeDetails("p800-overpayment")).left.get
      status(result) shouldBe Status.OK
      val document = Jsoup.parse(contentAsString(result))
      document.html() should include("Unknown enquiry type: p800-overpayment")
    }

    "return an error page from the Http Response if the call to getEnquiryTypeDetails in two-way-message returns FORBIDDEN" in {
      when(
        mockTwoWayMessageConnector
          .getSubmissionDetails(any[String])(any[HeaderCarrier])
      ).thenReturn(Future.successful(HttpResponse(Status.FORBIDDEN)))
      val result =
        await(controller.getEnquiryTypeDetails("p800-overpayment")).left.get
      status(result) shouldBe Status.OK
      val document = Jsoup.parse(contentAsString(result))
      document.html() should include("Not authenticated")
    }

    "return an error page from the Http Response if the call to getEnquiryTypeDetails in two-way-message returns NOT_FOUND" in {
      when(
        mockTwoWayMessageConnector
          .getSubmissionDetails(any[String])(any[HeaderCarrier])
      ).thenReturn(Future.successful(HttpResponse(Status.NOT_FOUND)))
      val result =
        await(controller.getEnquiryTypeDetails("p800-overpayment")).left.get
      status(result) shouldBe Status.OK
      val document = Jsoup.parse(contentAsString(result))
      document.html() should include("Unknown enquiry type: p800-overpayment")
    }
  }
}
