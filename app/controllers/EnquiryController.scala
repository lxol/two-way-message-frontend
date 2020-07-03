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

import java.net.{ URI, URISyntaxException }

import config.AppConfig
import connectors.{ PreferencesConnector, TwoWayMessageConnector }
import forms.EnquiryFormProvider
import javax.inject.{ Inject, Singleton }
import models.{ EnquiryDetails, ReturnLink }
import play.api.data._
import play.api.i18n.MessagesApi
import play.api.mvc.{ Action, AnyContent, Request, Result }
import uk.gov.hmrc.auth.core.AuthProvider.{ GovernmentGateway, Verify }
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals
import uk.gov.hmrc.auth.core.{ AuthConnector, AuthProviders, AuthorisationException }
import uk.gov.hmrc.crypto.{ ApplicationCrypto, Crypted }
import views.html.{ enquiry, enquiry_submitted, error_template }

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success, Try }

@Singleton
class EnquiryController @Inject()(
  appConfig: AppConfig,
  messagesApi: MessagesApi,
  formProvider: EnquiryFormProvider,
  authConnector: AuthConnector,
  twoWayMessageConnector: TwoWayMessageConnector,
  preferencesConnector: PreferencesConnector,
  crypto: ApplicationCrypto
)(override implicit val ec: ExecutionContext)
    extends BaseController(
      appConfig,
      authConnector,
      messagesApi,
      twoWayMessageConnector
    ) {

  val form: Form[EnquiryDetails] = formProvider()

  def onPageLoad(enquiryType: String, returnLink: Option[ReturnLink]): Action[AnyContent] =
    Action.async { implicit request =>
      authorised(AuthProviders(GovernmentGateway, Verify)).retrieve(Retrievals.nino) { nino =>
        for {
          email             <- nino.fold(Future.successful(""))(preferencesConnector.getPreferredEmail(_))
          submissionDetails <- getEnquiryTypeDetails(enquiryType)
          decryptedReturnLink = validateEncryptedReturnLink(returnLink)
        } yield
          (submissionDetails, decryptedReturnLink) match {
            case (Right(details), Right(_)) =>
              Ok(
                enquiry(
                  appConfig,
                  form,
                  EnquiryDetails(enquiryType, "", "", email, details.taxId, ""),
                  details.responseTime,
                  enquiryType,
                  returnLink
                )
              )
            case (Left(errorPage), _) => errorPage
            case (_, Left(errorPage)) => errorPage
          }
      } recoverWith {
        case _: AuthorisationException => Future.successful(Unauthorized)
        case _                         => Future.successful(InternalServerError)
      }
    }

  def onSubmit(returnLink: Option[ReturnLink]): Action[AnyContent] =
    Action.async { implicit request =>
      authorised(AuthProviders(GovernmentGateway, Verify)) {
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
                        details.responseTime,
                        enquiryType,
                        returnLink
                      )
                    )
                  )
                },
                enquiryDetails => {
                  submitMessage(enquiryDetails, details.responseTime, returnLink)
                }
              )
          case Left(errorPage) => Future.successful(errorPage)
        }
      }
    }

  def submitMessage(enquiryDetails: EnquiryDetails, responseTime: String, returnLink: Option[ReturnLink])(
    implicit request: Request[_]
  ): Future[Result] =
    for {
      response <- twoWayMessageConnector.postMessage(enquiryDetails)
      identifier = extractId(response)
      decryptedReturnLink = validateEncryptedReturnLink(returnLink)
    } yield
      (response.status, identifier, decryptedReturnLink) match {
        case (CREATED, Right(id), Right(returnLink)) =>
          Ok(enquiry_submitted(appConfig, id.id, responseTime, enquiryDetails.enquiryType, returnLink))
        case (_, Left(errorPage), _) => errorPage
        case (_, _, Left(errorPage)) => errorPage
        case _ =>
          Ok(
            error_template(
              "Error",
              "There was an error:",
              "Error sending enquiry details",
              appConfig
            )
          )
      }

  private def validateEncryptedReturnLink(returnLink: Option[ReturnLink]): Either[Result, Option[ReturnLink]] =
    returnLink.map { link =>
      val validatedUrl = Try {
        val encryptedUrl = crypto.QueryParameterCrypto.decrypt(Crypted(link.url)).value
        new URI(encryptedUrl).toString
      }
      val decryptedText = Try(crypto.QueryParameterCrypto.decrypt(Crypted(link.text)).value)
      (validatedUrl, decryptedText)
    } match {
      case Some(x) =>
        x match {
          case (Success(url), _) if url.length < 2   => Left(BadRequest("Invalid return link url"))
          case (_, Success(text)) if text.length < 2 => Left(BadRequest("Invalid return link text"))
          case (Success(url), Success(text))         => Right(Some(ReturnLink(url, text)))
          case (Failure(_: SecurityException), _)    => Left(BadRequest("Poorly encrypted return link url"))
          case (Failure(_: URISyntaxException), _)   => Left(BadRequest("Invalid return link url"))
          case (_, Failure(_: SecurityException))    => Left(BadRequest("Poorly encrypted return link text"))
          case _                                     => Left(BadRequest("An error occurred whilst attempting to validate the return link parameters"))
        }
      case None => Right(None)
    }

  private def rebuildFailedForm(
    formWithErrors: Form[EnquiryDetails],
    taxId: String
  ) =
    EnquiryDetails(
      formWithErrors.data.getOrElse("enquiryType", ""),
      formWithErrors.data.getOrElse("subject", ""),
      formWithErrors.data.getOrElse("telephone", ""),
      formWithErrors.data.getOrElse("email", ""),
      taxId,
      formWithErrors.data.getOrElse("question", "")
    )
}
