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

package connectors

import com.google.inject.AbstractModule
import net.codingwell.scalaguice.ScalaModule
import org.scalatest.concurrent.Eventually
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Configuration
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import org.scalatest.mockito.MockitoSugar
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.when
import play.api.libs.json.Json
import uk.gov.hmrc.domain.Nino

import scala.concurrent.{Await, Future}

class PreferencesConnectorSpec
    extends PlaySpec
    with GuiceOneServerPerSuite
    with MockitoSugar
    with Eventually {

  lazy implicit val hc = new HeaderCarrier()

  val preferencesConnector: PreferencesConnector = mock[PreferencesConnector]
  val twoWayMessageConnector: TwoWayMessageConnector =
    mock[TwoWayMessageConnector]
  val httpClient: HttpClient = mock[HttpClient]
  val entityResolverConnector: EntityResolverConnector =
    mock[EntityResolverConnector]

  lazy val injector = new GuiceApplicationBuilder()
    .configure(
      Configuration("metrics.enabled" -> false, "testserver.port" -> 8990)
    )
    .overrides(new AbstractModule with ScalaModule {
      override def configure(): Unit = {
        bind[EntityResolverConnector].toInstance(entityResolverConnector)
        bind[HttpClient].toInstance(httpClient)
        bind[TwoWayMessageConnector].toInstance(twoWayMessageConnector)
      }
    })
    .injector()

  "PreferencesConnector" should {
    "return an email address when matching NINO is found" in {
      val prefReply = """{
                        |	"termsAndConditions":{
                        |	  "generic":{
                        |	       "accepted":true,
                        |	       "updatedAt":1521110510782
                        |	    }
                        |	},
                        |	"email":{
                        |	   "email":"test@dummy.com",
                        |	    "isVerified":true,
                        |	    "hasBounces":false,
                        |	    "mailboxFull":false,
                        |	     "status":"verified"
                        |	 },
                        |	"digital":true,
                        |	"entityId":"e6e5ac52-71f1-46d7-b662-39b5c1deb1d8"
                        |  }""".stripMargin

      when(entityResolverConnector.resolveEntityIdFromNino(Nino("AB123456C")))
        .thenReturn(Future.successful(""))

      when(
        httpClient.GET[HttpResponse](ArgumentMatchers.anyString())(
          ArgumentMatchers.any(),
          ArgumentMatchers.any(),
          ArgumentMatchers.any()
        )
      ).thenReturn {
        Future.successful(HttpResponse(200, Some(Json.parse(prefReply))))
      }

      val component = injector.instanceOf[PreferencesConnector]

      val result = Await.result(
        component.getPreferredEmail("AB123456C"),
        scala.concurrent.duration.Duration.Inf
      )

      assert(result == "test@dummy.com")
    }

    "return an empty string when matching NINO is found but it is not verified" in {
      val prefReply = """{
                        |	"termsAndConditions":{
                        |	  "generic":{
                        |	       "accepted":true,
                        |	       "updatedAt":1521110510782
                        |	    }
                        |	},
                        |	"email":{
                        |	   "email":"test@dummy.com",
                        |	    "isVerified":false,
                        |	    "hasBounces":false,
                        |	    "mailboxFull":false,
                        |	     "status":"verified"
                        |	 },
                        |	"digital":true,
                        |	"entityId":"e6e5ac52-71f1-46d7-b662-39b5c1deb1d8"
                        |  }""".stripMargin

      when(entityResolverConnector.resolveEntityIdFromNino(Nino("AB123456C")))
        .thenReturn(Future.successful(""))

      when(
        httpClient.GET[HttpResponse](ArgumentMatchers.anyString())(
          ArgumentMatchers.any(),
          ArgumentMatchers.any(),
          ArgumentMatchers.any()
        )
      ).thenReturn {
        Future.successful(HttpResponse(200, Some(Json.parse(prefReply))))
      }

      val component = injector.instanceOf[PreferencesConnector]

      val result = Await.result(
        component.getPreferredEmail("AB123456C"),
        scala.concurrent.duration.Duration.Inf
      )

      assert(result == "")
    }

    "return an empty string when matching NINO is found but it is has bounced" in {
      val prefReply = """{
                        |	"termsAndConditions":{
                        |	  "generic":{
                        |	       "accepted":true,
                        |	       "updatedAt":1521110510782
                        |	    }
                        |	},
                        |	"email":{
                        |	   "email":"test@dummy.com",
                        |	    "isVerified":true,
                        |	    "hasBounces":true,
                        |	    "mailboxFull":false,
                        |	     "status":"verified"
                        |	 },
                        |	"digital":true,
                        |	"entityId":"e6e5ac52-71f1-46d7-b662-39b5c1deb1d8"
                        |  }""".stripMargin

      when(entityResolverConnector.resolveEntityIdFromNino(Nino("AB123456C")))
        .thenReturn(Future.successful(""))

      when(
        httpClient.GET[HttpResponse](ArgumentMatchers.anyString())(
          ArgumentMatchers.any(),
          ArgumentMatchers.any(),
          ArgumentMatchers.any()
        )
      ).thenReturn {
        Future.successful(HttpResponse(200, Some(Json.parse(prefReply))))
      }

      val component = injector.instanceOf[PreferencesConnector]

      val result = Await.result(
        component.getPreferredEmail("AB123456C"),
        scala.concurrent.duration.Duration.Inf
      )

      assert(result == "")
    }

    "return an empty string when unable to connect to the preferance service" in {
      when(entityResolverConnector.resolveEntityIdFromNino(Nino("AB123456C")))
        .thenReturn(Future.successful(""))

      when(
        httpClient.GET[HttpResponse](ArgumentMatchers.anyString())(
          ArgumentMatchers.any(),
          ArgumentMatchers.any(),
          ArgumentMatchers.any()
        )
      ).thenReturn {
        Future.failed(new Exception("NO PREFERANCE SERVER"))
      }

      val component = injector.instanceOf[PreferencesConnector]

      val result = Await.result(
        component.getPreferredEmail("AB123456C"),
        scala.concurrent.duration.Duration.Inf
      )

      assert(result == "")
    }

  }
}
