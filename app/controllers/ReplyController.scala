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

import config.AppConfig
import connectors.TwoWayMessageConnector
import forms.ReplyFormProvider
import javax.inject.{Inject, Singleton}

import play.api.data.Forms._
import play.api.data._
import play.api.data.validation.Constraints._
import play.api.i18n.{I18nSupport, MessagesApi}
import uk.gov.hmrc.auth.core.{AuthConnector, AuthorisedFunctions, Enrolment}
import play.api.mvc.{Action, AnyContent}
import uk.gov.hmrc.play.bootstrap.controller.FrontendController
import views.html.reply

import scala.concurrent.{ExecutionContext, Future}
import models.{ReplyDetails, Identifier, MessageError}
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals
import uk.gov.hmrc.http.HttpResponse

import ExecutionContext.Implicits.global

@Singleton
class ReplyController @Inject()(appConfig: AppConfig,
                                  override val messagesApi: MessagesApi,
                                  formProvider: ReplyFormProvider,
                                  val authConnector: AuthConnector,
                                  twoWayMessageConnector: TwoWayMessageConnector)
  extends FrontendController with AuthorisedFunctions with I18nSupport {

  val form: Form[ReplyDetails] = formProvider()

  def onPageLoad(queue: String, replyTo: String): Action[AnyContent] = Action.async {
    implicit request =>
      authorised(Enrolment("HMRC-NI")).retrieve(Retrievals.email) {
        case email => {
          Future.successful(Ok(reply(queue, replyTo, appConfig, form, ReplyDetails( ""))))
        }
      }
  }

  def onSubmit(queueId: String, replyTo: String): Action[AnyContent] = Action.async {
    implicit request =>
      authorised(Enrolment("HMRC-NI")) {
        form.bindFromRequest().fold(
          (formWithErrors: Form[ReplyDetails]) => {
            var returnedErrorForm = formWithErrors
            if(emailConfirmationError(formWithErrors)) {
              returnedErrorForm = appendEmailConfirmationError(formWithErrors)
            }
            Future.successful(BadRequest(reply(queueId, replyTo, appConfig, returnedErrorForm, rebuildFailedForm(formWithErrors))))
          },
            replyDetails => 
              twoWayMessageConnector.postReplyMessage(replyDetails, queueId, replyTo).map(response => response.status match {
                case CREATED => extractId(response) match {
                  case Right(id) => Redirect(routes.ReplySubmittedController.onPageLoad(queueId, replyTo, Some(id), None))
                  case Left(error) => Redirect(routes.ReplySubmittedController.onPageLoad(queueId, replyTo, None, Some(error)))
                }
                case _ => Redirect(routes.ReplySubmittedController.onPageLoad(queueId, replyTo, None,Some(MessageError("Error sending reply details"))))
              })
        )
      }
  }

  def messagesRedirect = Action {
    Redirect(appConfig.messagesFrontend)
  }

  def extractId(response: HttpResponse): Either[MessageError,Identifier] = {
    response.json.validate[Identifier].asOpt match {
      case Some(identifier) => Right(identifier)
      case None => Left(MessageError("Missing reference"))
    }
  }

  def emailConfirmationError(form: Form[ReplyDetails]) = {
    val email = form.data.get("email")
    val confirmEmail = form.data.get("confirmEmail")
    (email.isEmpty || confirmEmail.isEmpty) || email.get != confirmEmail.get
  }

  def appendEmailConfirmationError(form: Form[ReplyDetails]) = {
    val appendedErrors = form.errors ++ Seq(FormError("email", "Email addresses must match. Check them and try again."))
    form.copy(errors = appendedErrors)
  }

  private def rebuildFailedForm(formWithErrors: Form[ReplyDetails]) = {
      ReplyDetails(
        formWithErrors.data.get("content").getOrElse("")
      )
    }
}
