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

import javax.inject.{Inject, Singleton}
import play.api.Mode.Mode
import play.api.http.Status
import play.api.libs.json.{JsValue, Json}
import play.api.{Configuration, Environment}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.play.config.ServicesConfig

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class PreferencesConnector @Inject()(httpClient: HttpClient,
                                     val runModeConfiguration: Configuration,
                                     val environment: Environment,
                                     val entityResolverConnector: EntityResolverConnector)
                                    (implicit ec: ExecutionContext) extends Status with ServicesConfig {


  override protected def mode: Mode = environment.mode

  lazy val preferencesBaseUrl: String = baseUrl("preferences")

  def getPreferredEmail(nino: String)(implicit headerCarrier: HeaderCarrier): Future[String] = {

    def verified(body:JsValue): Boolean = (body \ "email" \ "isVerified").asOpt[Boolean].getOrElse(false)
    def hasBounces(body:JsValue): Boolean = (body \ "email" \ "hasBounces").asOpt[Boolean].getOrElse(false)

    try {
      for {
        entityId <- entityResolverConnector.resolveEntityIdFromNino(Nino(nino))
        email <- httpClient
          .GET[HttpResponse](s"$preferencesBaseUrl/preferences/$entityId")
          .map(e => {
            val jBody: JsValue = Json.parse(e.body)
            if  (verified(jBody) && !hasBounces(jBody)) { (jBody \ "email" \ "email").as[String] }
            else { "" }
          })
          .recover({ case _ => "" })
      } yield email
    } catch {
      case _: Throwable => Future.successful("")
    }
  }
}



