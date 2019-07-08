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

package connectors

import javax.inject.{Inject, Singleton}

import models.MessageFormat._
import models._
import play.api.Mode.Mode
import play.api.http.Status
import play.api.libs.json.{Format, JsError, Json}
import play.api.{Configuration, Environment}
import play.twirl.api.Html
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.play.config.ServicesConfig

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TwoWayMessageConnector @Inject()(httpClient: HttpClient,
                                       override val runModeConfiguration: Configuration,
                                       val environment: Environment)(implicit ec: ExecutionContext)
  extends Status with ServicesConfig {

  override protected def mode: Mode = environment.mode
  lazy val twoWayMessageBaseUrl: String = baseUrl("two-way-message")

  def postMessage(details: EnquiryDetails)(implicit hc: HeaderCarrier): Future[HttpResponse] = {
    val message = TwoWayMessage(
      ContactDetails(details.email),
      details.subject,
      details.question
    )
    httpClient.POST(s"$twoWayMessageBaseUrl/two-way-message/message/customer/${details.queue}/submit", message)
  }

  def postReplyMessage(details: ReplyDetails, queueId: String, replyTo: String)(implicit hc: HeaderCarrier): Future[HttpResponse] = {
    val message = TwoWayMessageReply(
      details.content
    )
    httpClient.POST(s"$twoWayMessageBaseUrl/two-way-message/message/customer/$queueId/$replyTo/reply", message)
  }

  def getMessages(messageId: String)(implicit hc: HeaderCarrier): Future[List[ConversationItem]] =
    httpClient.GET(s"${twoWayMessageBaseUrl}/two-way-message/message/messages-list/$messageId")
      .flatMap {
        response => response.json.validate[List[ConversationItem]].fold(
          errors => Future.failed(new Exception(Json stringify JsError.toJson(errors))),
          msgList => Future.successful(msgList))
      }

  implicit val waitTimeResponseJson: Format[WaitTimeResponse] = Json.format[WaitTimeResponse]

  case class WaitTimeResponse(responseTime: String)

  def getWaitTime(formId: String)(implicit hc: HeaderCarrier): Future[String] =
    httpClient.GET[WaitTimeResponse](s"${twoWayMessageBaseUrl}/two-way-message/message/admin/$formId/response-time")
      .map(e => e.responseTime)

  def getLatestMessage(messageId: String)(implicit hc: HeaderCarrier): Future[Option[Html]] = {
    httpClient.GET(s"${twoWayMessageBaseUrl}/messages/$messageId/latest-message")
      .map { response => Some(Html(response.body)) }

  }

  def getPreviousMessages(messageId: String)(implicit hc: HeaderCarrier): Future[Option[Html]] = {
    httpClient.GET(s"${twoWayMessageBaseUrl}/messages/$messageId/previous-messages")
      .map { response => Some(Html(response.body)) }
  }

}
