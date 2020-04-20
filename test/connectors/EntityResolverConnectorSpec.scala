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
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.domain.Nino

import scala.concurrent.{Await, Future}

class EntityResolverConnectorSpec
    extends PlaySpec
    with GuiceOneServerPerSuite
    with MockitoSugar
    with Eventually {

  lazy implicit val hc = new HeaderCarrier()

  val entityResolverConnector: EntityResolverConnector =
    mock[EntityResolverConnector]

  val twoWayMessageConnector: TwoWayMessageConnector =
    mock[TwoWayMessageConnector]
  val httpClient: HttpClient = mock[HttpClient]

  lazy val injector = new GuiceApplicationBuilder()
    .configure(
      Configuration("metrics.enabled" -> false, "testserver.port" -> 8990)
    )
    .overrides(new AbstractModule with ScalaModule {
      override def configure(): Unit = {
        bind[HttpClient].toInstance(httpClient)
      }
    })
    .injector()

  "PreferencesConnector" should {
    "return an entity id from a matching NINO " in {

      when(
        httpClient.GET[HttpResponse](ArgumentMatchers.anyString())(
          ArgumentMatchers.any(),
          ArgumentMatchers.any(),
          ArgumentMatchers.any()
        )
      ).thenReturn {
        Future.successful(
          HttpResponse(
            200,
            Some(
              Json.parse(
                """ { "_id":"987", "sautr":"123", "nino": "AB123456C"} """
              )
            )
          )
        )
      }

      val component = injector.instanceOf[EntityResolverConnector]

      val result = Await.result(
        component.resolveEntityIdFromNino(Nino("AB123456C")),
        scala.concurrent.duration.Duration.Inf
      )

      assert(result == "987")
    }

    "return empty string if no matching NINO " in {

      when(
        httpClient.GET[HttpResponse](ArgumentMatchers.anyString())(
          ArgumentMatchers.any(),
          ArgumentMatchers.any(),
          ArgumentMatchers.any()
        )
      ).thenReturn {
        Future.successful(HttpResponse(404))
      }

      val component = injector.instanceOf[EntityResolverConnector]

      val result = Await.result(
        component.resolveEntityIdFromNino(Nino("AB123456C")),
        scala.concurrent.duration.Duration.Inf
      )

      assert(result == "")
    }

  }

}
