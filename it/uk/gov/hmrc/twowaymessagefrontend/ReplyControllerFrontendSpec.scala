
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
import controllers.ReplyController
import models.ReplyDetails
import net.codingwell.scalaguice.ScalaModule
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatestplus.play.{HtmlUnitFactory, OneBrowserPerSuite}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{Json, Reads}
import play.api.test.Helpers._
import play.api.{Application, Configuration}
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.retrieve.OptionalRetrieval
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.twowaymessagefrontend.util.{ControllerSpecBase, MockAuthConnector}

import scala.concurrent.Future

class ReplyControllerFrontendSpec extends ControllerSpecBase  with MockAuthConnector with HtmlUnitFactory with   OneBrowserPerSuite{


  val preferencesConnector: PreferencesConnector = mock[PreferencesConnector]
  val twoWayMessageConnector: TwoWayMessageConnector = mock[TwoWayMessageConnector]

  when(twoWayMessageConnector.getMessages(any())(any())).thenReturn(Future.successful(List()))

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

  val replyController = app.injector.instanceOf[ReplyController]

  "Frontend test" should {
    "find the home page ok" in {
      mockAuthorise(Enrolment("HMRC-NI"), OptionalRetrieval("nino", Reads.StringReads))(Future.successful(Some("AB123456C")))
      mockAuthorise(Enrolment("HMRC-NI"))(Future.successful(Some("AB123456C")))
      when(twoWayMessageConnector.getWaitTime(ArgumentMatchers.eq("p800"))(ArgumentMatchers.any[HeaderCarrier])) thenReturn {
        Future.successful("7 days")
      }
      when( preferencesConnector.getPreferredEmail( ArgumentMatchers.eq("5c18eb166f0000110204b160") )(ArgumentMatchers.any[HeaderCarrier])) thenReturn {
        Future.successful("email@dummy.com")
      }
      val result = await(call(replyController.onPageLoad("p800", "5c18eb166f0000110204b160"), fakeRequest))
      result.header.status mustBe (200)
    }

    "Send a valid message" in {
      import org.mockito.Mockito._

      mockAuthorise(Enrolment("HMRC-NI"))(Future.successful(Some("AB123456C")))

      val replyDetails = ReplyDetails("A question from the customer")
      when(twoWayMessageConnector.postReplyMessage( ArgumentMatchers.eq(replyDetails), ArgumentMatchers.eq("p800"), ArgumentMatchers.eq("A1B2C3D4E5") )(ArgumentMatchers.any[HeaderCarrier])) thenReturn {
        val x = Json.parse( """{ "id":"5c18eb166f0000110204b160" }""".stripMargin )

        Future.successful(HttpResponse(play.api.http.Status.CREATED, Some(x)))
      }

      go to s"http://localhost:$port/two-way-message-frontend/message/customer/p800/A1B2C3D4E5/reply#reply-input"

      textArea("reply-input").value = "A question from the customer"

      click on find(id("submit")).value

      eventually { pageSource must include ("HMRC received your message and will reply within") }

    }
  }

}
