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

import config.AppConfig
import connectors.TwoWayMessageConnector
import javax.inject.Inject
import models.{Identifier, MessageError, SubmissionDetails}
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Request, Result}
import uk.gov.hmrc.auth.core.{AuthConnector, AuthorisedFunctions}
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.play.bootstrap.controller.FrontendController
import views.html.error_template

import scala.concurrent.{ExecutionContext, Future}

class BaseController @Inject() (
    val appConfig: AppConfig,
    override val authConnector: AuthConnector,
    override val messagesApi: MessagesApi,
    val twoWayMessageConnector: TwoWayMessageConnector
)(implicit val ec: ExecutionContext)
    extends FrontendController
    with AuthorisedFunctions
    with I18nSupport {

  def getEnquiryTypeDetails(
      enquiryType: String
  )(implicit request: Request[_]): Future[Either[Result, SubmissionDetails]] = {
    twoWayMessageConnector
      .getSubmissionDetails(enquiryType)
      .flatMap(response =>
        response.status match {
          case OK =>
            response.json.validate[SubmissionDetails].asOpt match {
              case Some(submissionDetails) =>
                Future.successful(Right(submissionDetails))
              case None =>
                Future.successful(
                  Left(
                    Ok(
                      error_template(
                        "Error",
                        "There was an error:",
                        s"Unknown enquiry type: $enquiryType",
                        appConfig
                      )
                    )
                  )
                )
            }
          case FORBIDDEN =>
            Future.successful(
              Left(
                Ok(
                  error_template(
                    "Error",
                    "There was an error:",
                    "Not authenticated",
                    appConfig
                  )
                )
              )
            )
          case NOT_FOUND =>
            Future.successful(
              Left(
                Ok(
                  error_template(
                    "Error",
                    "There was an error:",
                    s"Unknown enquiry type: $enquiryType",
                    appConfig
                  )
                )
              )
            )
          case _ =>
            Future.successful(
              Left(
                Ok(
                  error_template(
                    "Error",
                    "There was an error:",
                    "Error getting enquiry type details",
                    appConfig
                  )
                )
              )
            )
        }
      )
  }

  def extractId(response: HttpResponse): Either[MessageError, Identifier] = {
    response.json.validate[Identifier].asOpt match {
      case Some(identifier) => Right(identifier)
      case None             => Left(MessageError("Missing reference"))
    }
  }
}
