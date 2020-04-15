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

package handlers

import config.AppConfig
import javax.inject.{Inject, Singleton}
import play.api.i18n.MessagesApi
import play.api.mvc.Results._
import play.api.mvc.{Request, RequestHeader, Result}
import play.api.{Configuration, Environment, Logger}
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.http.{NotFoundException, Upstream5xxResponse}
import uk.gov.hmrc.play.bootstrap.config.AuthRedirects
import uk.gov.hmrc.play.bootstrap.http.FrontendErrorHandler
import views.html.error_template

@Singleton
class ErrorHandler @Inject()(val appConfig: AppConfig,
                              val messagesApi: MessagesApi,
                             val config: Configuration,
                             val env: Environment)
  extends FrontendErrorHandler with AuthRedirects {

  override def standardErrorTemplate (pageTitle: String, heading: String, message: String) (implicit request: Request[_] ) =
    error_template(pageTitle, heading, message, appConfig)

  override def resolveError(rh: RequestHeader, ex: Throwable): Result = {
    ex match {
      case _: MissingBearerToken =>
        Logger.debug("[AuthenticationPredicate][async] Missing Bearer Token. Redirecting to GG Login.")
        toGGLogin(rh.uri)
      case _: BearerTokenExpired =>
        Logger.debug("[AuthenticationPredicate][async] Bearer Token Timed Out. Redirecting to GG Login.")
        toGGLogin(rh.uri)
      case _: NoActiveSession =>
        Logger.debug("[AuthenticationPredicate][async] No Active Auth Session. Redirecting to GG Login.")
        toGGLogin(rh.uri)
      case _: AuthorisationException =>
        Logger.debug("[AuthenticationPredicate][async] Unauthorised request. Redirecting to GG Login.")
        toGGLogin(rh.uri)
      case _: NotFoundException =>
        NotFound(notFoundTemplate(Request(rh, "")))
      case _: Upstream5xxResponse =>
        // currently the two-way-message m/s converts any errors from the message m/s to 502 errors so any errors originating there will end up here
        InternalServerError(standardErrorTemplate("Error","There was an error: ",
          "We are unable to process your enquiry at this time. Please try again later.")(Request(rh, "")))
      case _ => super.resolveError(rh, ex)
    }
  }

}
