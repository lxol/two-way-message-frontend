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
import connectors.{PreferencesConnector, TwoWayMessageConnector}
import forms.EnquiryFormProvider
import javax.inject.{Inject, Singleton}
import models.{EnquiryDetails, Identifier, MessageError}
import play.api.data._
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent, Request, Result}
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals
import uk.gov.hmrc.auth.core.{AuthConnector, AuthorisationException, AuthorisedFunctions, Enrolment}
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.play.bootstrap.controller.FrontendController
import views.html.{enquiry, enquiry_submitted, error_template}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class EnquiryController @Inject()(appConfig: AppConfig,
                                  override val messagesApi: MessagesApi,
                                  formProvider: EnquiryFormProvider,
                                  val authConnector: AuthConnector,
                                  twoWayMessageConnector: TwoWayMessageConnector,
                                  preferencesConnector: PreferencesConnector)
  extends FrontendController with AuthorisedFunctions with I18nSupport {

  val form: Form[EnquiryDetails] = formProvider()

  def onPageLoad(enquiryType: String): Action[AnyContent] = Action.async {
    implicit request =>
      authorised(Enrolment("HMRC-NI")).retrieve(Retrievals.nino) {
        case Some(nino) =>
          for {
            submissionDetails <- twoWayMessageConnector.getSubmissionDetails(enquiryType)
            email <- preferencesConnector.getPreferredEmail(nino)
          } yield {
            submissionDetails match {
              case Some(details) => Ok(enquiry(appConfig, form, EnquiryDetails(enquiryType, "", "", email), details.responseTime))
              case None => NotFound
            }
          }
        case _ => Future.successful(Forbidden)
      } recoverWith {
        case _ : AuthorisationException => Future.successful(Unauthorized)
        case _ => Future.successful(InternalServerError)
      }
    }

  def onSubmit(): Action[AnyContent] = Action.async {
    implicit request =>
      authorised(Enrolment("HMRC-NI")) {
        val enquiryType = form.bindFromRequest().data("enquiryType")
        twoWayMessageConnector.getSubmissionDetails(enquiryType).flatMap {
          case Some(details) =>
            form.bindFromRequest().fold(
              (formWithErrors: Form[EnquiryDetails]) => {
                Future.successful(BadRequest(enquiry(appConfig, formWithErrors, rebuildFailedForm(formWithErrors), details.responseTime)))
              },
              enquiryDetails => {
                submitMessage(enquiryDetails, details.responseTime)
              })
          case None => Future.successful(Ok(error_template("Error", "There was an error:", s"Unknown enquiry type: $enquiryType", appConfig)))
        }
      }
  }

  def submitMessage(enquiryDetails: EnquiryDetails, responseTime: String)(implicit request: Request[_]): Future[Result] = {
      twoWayMessageConnector.postMessage(enquiryDetails).map(response => response.status match {
        case CREATED => extractId(response) match {
          case Right(id) => Ok(enquiry_submitted(appConfig, id.id, responseTime))
          case Left(error) => Ok(error_template("Error", "There was an error:", error.text, appConfig))
        }
        case _ => Ok(error_template("Error", "There was an error:", "Error sending enquiry details", appConfig))
      })
  }

  def messagesRedirect: Action[AnyContent] = Action {
    Redirect(appConfig.messagesFrontendUrl)
  }

  def personalAccountRedirect: Action[AnyContent] = Action {
    Redirect(appConfig.personalAccountUrl)
  }

  def extractId(response: HttpResponse): Either[MessageError,Identifier] = {
    response.json.validate[Identifier].asOpt match {
      case Some(identifier) => Right(identifier)
      case None => Left(MessageError("Missing reference"))
    }
  }

  private def rebuildFailedForm(formWithErrors: Form[EnquiryDetails]) = {
      EnquiryDetails(
        formWithErrors.data.getOrElse("enquiryType", ""),
        formWithErrors.data.getOrElse("subject", ""),
        formWithErrors.data.getOrElse("question", ""),
        formWithErrors.data.getOrElse("email", ""))
    }


}
