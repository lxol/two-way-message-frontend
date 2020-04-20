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
import connectors.{ PreferencesConnector, TwoWayMessageConnector }
import forms.EnquiryFormProvider
import javax.inject.{ Inject, Singleton }
import models.EnquiryDetails
import play.api.data._
import play.api.i18n.MessagesApi
import play.api.mvc.{ Action, AnyContent, Request, Result }
import uk.gov.hmrc.auth.core.AuthProvider.GovernmentGateway
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals
import uk.gov.hmrc.auth.core.{ AuthConnector, AuthProviders, AuthorisationException }
import views.html.{ enquiry, enquiry_submitted, error_template }

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class EnquiryController @Inject()(
  appConfig: AppConfig,
  messagesApi: MessagesApi,
  formProvider: EnquiryFormProvider,
  authConnector: AuthConnector,
  twoWayMessageConnector: TwoWayMessageConnector,
  preferencesConnector: PreferencesConnector
)(override implicit val ec: ExecutionContext)
    extends BaseController(
      appConfig,
      authConnector,
      messagesApi,
      twoWayMessageConnector
    ) {

  val form: Form[EnquiryDetails] = formProvider()

  def onPageLoad(enquiryType: String): Action[AnyContent] =
    Action.async { implicit request =>
      authorised(AuthProviders(GovernmentGateway)).retrieve(Retrievals.nino) { nino =>
        for {
          email <- nino.fold(Future.successful(""))(
                    preferencesConnector.getPreferredEmail(_)
                  )
          submissionDetails <- getEnquiryTypeDetails(enquiryType)
        } yield
          submissionDetails.fold(
            (errorPage: Result) => errorPage,
            details =>
              Ok(
                enquiry(
                  appConfig,
                  form,
                  EnquiryDetails(enquiryType, "", "", email, "", details.taxId),
                  details.responseTime
                )
            )
          )
      } recoverWith {
        case _: AuthorisationException => Future.successful(Unauthorized)
        case _                         => Future.successful(InternalServerError)
      }
    }

  def onSubmit(): Action[AnyContent] =
    Action.async { implicit request =>
      authorised(AuthProviders(GovernmentGateway)) {
        val enquiryType = form.bindFromRequest().data("enquiryType")
        getEnquiryTypeDetails(enquiryType).flatMap {
          case Right(details) =>
            form
              .bindFromRequest()
              .fold(
                (formWithErrors: Form[EnquiryDetails]) => {
                  Future.successful(
                    BadRequest(
                      enquiry(
                        appConfig,
                        formWithErrors,
                        rebuildFailedForm(formWithErrors, details.taxId),
                        details.responseTime
                      )
                    )
                  )
                },
                enquiryDetails => {
                  submitMessage(enquiryDetails, details.responseTime)
                }
              )
          case Left(errorPage) => Future.successful(errorPage)
        }
      }
    }

  def submitMessage(enquiryDetails: EnquiryDetails, responseTime: String)(
    implicit request: Request[_]
  ): Future[Result] =
    twoWayMessageConnector
      .postMessage(enquiryDetails)
      .map(response =>
        response.status match {
          case CREATED =>
            extractId(response) match {
              case Right(id) =>
                Ok(enquiry_submitted(appConfig, id.id, responseTime))
              case Left(error) =>
                Ok(
                  error_template(
                    "Error",
                    "There was an error:",
                    error.text,
                    appConfig
                  )
                )
            }
          case _ =>
            Ok(
              error_template(
                "Error",
                "There was an error:",
                "Error sending enquiry details",
                appConfig
              )
            )
      })

  def messagesRedirect: Action[AnyContent] =
    Action {
      Redirect(appConfig.messagesFrontendUrl)
    }

  def personalAccountRedirect: Action[AnyContent] =
    Action {
      Redirect(appConfig.personalAccountUrl)
    }

  private def rebuildFailedForm(
    formWithErrors: Form[EnquiryDetails],
    taxId: String
  ) =
    EnquiryDetails(
      formWithErrors.data.getOrElse("enquiryType", ""),
      formWithErrors.data.getOrElse("subject", ""),
      formWithErrors.data.getOrElse("question", ""),
      formWithErrors.data.getOrElse("email", ""),
      formWithErrors.data.getOrElse("telephone", ""),
      taxId
    )
}
