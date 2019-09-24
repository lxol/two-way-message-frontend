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
import connectors.mocks.MockAuthConnector
import net.codingwell.scalaguice.ScalaModule
import play.api.Application
import play.api.http.Status
import play.api.i18n.I18nSupport
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.{AuthConnector, Enrolment}
import uk.gov.hmrc.domain.Nino

import scala.concurrent.Future

class AccessibilityStatementControllerSpec extends ControllerSpecBase with MockAuthConnector with I18nSupport {


  override def fakeApplication(): Application = {

    new GuiceApplicationBuilder()
      .overrides(new AbstractModule with ScalaModule {
        override def configure(): Unit = {
          bind[AuthConnector].toInstance(mockAuthConnector)
        }
      })
      .build()
  }

  val controller = injector.instanceOf[AccessibilityStatementController]

  "calling show" should {

    "return 200 (OK) when presented with a valid (HMRC-NI) provider from auth-client" in {
      val nino = Nino("AB123456C")
      mockAuthorise(Enrolment("HMRC-NI"))(Future.successful(Some(nino.value)))

      val result = call(controller.show, fakeRequest)

      status(result) shouldBe Status.OK
    }

    "return 403 (OK) when presented with invalid auth provider from auth-client" in {
      val nino = Nino("AB123456C")
      mockAuthorise(Enrolment("HMRC-NI"))(Future.failed(new Exception("")))
      val result = call(controller.show, fakeRequest)
      status(result) shouldBe Status.FORBIDDEN
    }
  }
}

