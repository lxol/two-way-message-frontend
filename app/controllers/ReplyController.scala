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
import models.{Identifier, MessageError, ReplyDetails}
import play.api.data._
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent, Request, Result}
import play.twirl.api.Html
import uk.gov.hmrc.auth.core.{AuthConnector, AuthorisedFunctions, Enrolment}
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.play.bootstrap.controller.FrontendController
import utils.MessageRenderer
import views.html.{enquiry_submitted, error_template, reply}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton
class ReplyController @Inject()(appConfig: AppConfig,
                                override val messagesApi: MessagesApi,
                                formProvider: ReplyFormProvider,
                                val authConnector: AuthConnector,
                                messageRenderer: MessageRenderer,
                                twoWayMessageConnector: TwoWayMessageConnector)
  extends FrontendController with AuthorisedFunctions with I18nSupport {

  val form: Form[ReplyDetails] = formProvider()

  def onPageLoad(enquiryType: String, replyTo: String): Action[AnyContent] = Action.async {
    implicit request =>
    authorised(Enrolment("HMRC-NI")) {
      for {
        before <- twoWayMessageConnector.getLatestMessage(replyTo)
        after <- twoWayMessageConnector.getPreviousMessages(replyTo)
      } yield {Ok(reply(enquiryType, replyTo, appConfig, form, ReplyDetails(""),before.getOrElse(Html("")), after.getOrElse(Html("")))) }
    }
  }

  def onSubmit(enquiryType: String, replyTo: String): Action[AnyContent] = Action.async {
    implicit request =>
    authorised(Enrolment("HMRC-NI")) {
      form.bindFromRequest().fold(
        (formWithErrors: Form[ReplyDetails]) => {
          val returnedErrorForm = formWithErrors
          for {
            before <- twoWayMessageConnector.getLatestMessage(replyTo)
            after <- twoWayMessageConnector.getPreviousMessages(replyTo)
          } yield BadRequest(reply(
              enquiryType, replyTo, appConfig, returnedErrorForm, rebuildFailedForm(formWithErrors),before.getOrElse(Html("")), after.getOrElse(Html(""))))
        },
        replyDetails =>
        submitMessage(enquiryType,replyDetails,replyTo)
      )
    }
  }

  def submitMessage(enquiryType: String, replyDetails: ReplyDetails, replyTo: String)(implicit request: Request[_]): Future[Result] = {
    twoWayMessageConnector.getSubmissionDetails(enquiryType).flatMap {
      case Some(details) =>
        twoWayMessageConnector.postReplyMessage(replyDetails, enquiryType, replyTo).flatMap { response =>
          response.status match {
            case CREATED => extractId(response) match {
              case Right(id) => Future.successful(Ok(enquiry_submitted(appConfig, id.id, details.responseTime)))
              case Left(error) => Future.successful(Ok(error_template("Error", "There was an error:", error.text, appConfig)))
            }
            case _ => Future.successful(Ok(error_template("Error", "There was an error:", "Error sending reply details", appConfig)))
          }
        }
      case None => Future.successful(Ok(error_template("Error", "There was an error:", s"Unknown enquiry type: $enquiryType", appConfig)))
    }
  }

  def extractId(response: HttpResponse): Either[MessageError, Identifier] = {
    response.json.validate[Identifier].asOpt match {
      case Some(identifier) => Right(identifier)
      case None => Left(MessageError("Missing reference"))
    }
  }

  private def rebuildFailedForm(formWithErrors: Form[ReplyDetails]) = {
    ReplyDetails(
      formWithErrors.data.getOrElse("reply-input", "")
    )
  }
}
