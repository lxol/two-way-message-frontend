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

import scala.concurrent.{ExecutionContext, Future}
import ExecutionContext.Implicits.global
import config.AppConfig
import handlers.ErrorHandler
import javax.inject.{Inject, Singleton}

import models.{Identifier, MessageError}
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent}
import uk.gov.hmrc.auth.core.{AuthConnector, AuthorisedFunctions, Enrolment}
import uk.gov.hmrc.play.bootstrap.controller.FrontendController
import views.html.{enquirySubmitted, error_template}

@Singleton
class ReplySubmittedController @Inject()(appConfig: AppConfig,
                                           val authConnector: AuthConnector,
                                           override val messagesApi: MessagesApi,
                                           errorHandler: ErrorHandler)
  extends FrontendController with AuthorisedFunctions with I18nSupport {

  def onPageLoad(queueId: String, replyTo: String, maybeId: Option[Identifier], maybeError: Option[MessageError]): Action[AnyContent] = Action.async {
    implicit request =>
      authorised(Enrolment("HMRC-NI")) {
        maybeId match {
          case Some(identifier) => Future.successful(Ok(enquirySubmitted(appConfig, identifier.id)))
          case _ => maybeError match {
            case Some(error) => Future.successful(Ok(error_template("Error", "There was an error:", error.text, appConfig)))
            case _ => Future.successful(Ok(error_template("Error", "Missing reference number!","", appConfig)))
          }
        }
      }
  }
}
