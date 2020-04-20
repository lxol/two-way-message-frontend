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

import javax.inject.{ Inject, Singleton }
import play.api.Mode.Mode
import play.api.http.Status
import play.api.libs.json.{ Json, Reads }
import play.api.{ Configuration, Environment }
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.{ HeaderCarrier, HttpResponse }
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.play.config.ServicesConfig

import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class EntityResolverConnector @Inject()(
  httpClient: HttpClient,
  val runModeConfiguration: Configuration,
  val environment: Environment
)(implicit ec: ExecutionContext)
    extends Status with ServicesConfig {

  case class Entity(_id: String, sautr: String, nino: String)
  implicit val reader: Reads[Entity] = Json.reads[Entity]

  override protected def mode: Mode = environment.mode
  lazy val entityResolverBaseUrl: String = baseUrl("entity-resolver")

  def resolveEntityIdFromNino(
    nino: Nino
  )(implicit headerCarrier: HeaderCarrier): Future[String] =
    httpClient
      .GET[HttpResponse](s"$entityResolverBaseUrl/entity-resolver/paye/$nino")
      .map(resp => Json.parse(resp.body).as[Entity]._id)
      .recover({ case _ => "" })
}
