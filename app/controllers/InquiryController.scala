/*
 * Copyright 2018 HM Revenue & Customs
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

import javax.inject.Inject

import play.api.data.Form
import play.api.i18n.{I18nSupport, MessagesApi}
import uk.gov.hmrc.play.bootstrap.controller.FrontendController
import connectors.DataCacheConnector
import controllers.actions._
import config.FrontendAppConfig
import forms.InquiryFormProvider
//import utils.{Navigator, UserAnswers}
import views.html.inquiry

import play.api.mvc.{Action, AnyContent}

import scala.concurrent.Future
import utils.InputOption

class InquiryController @Inject()(appConfig: FrontendAppConfig,
                                         override val messagesApi: MessagesApi,
                                         formProvider: InquiryFormProvider) extends FrontendController with I18nSupport {

  val form: Form[Boolean] = formProvider()

  def onPageLoad() = //(identify andThen getData andThen requireData)
    Action {
    implicit request =>
      // val preparedForm = request.userAnswers.takingOverBusiness match {
      //   case None => form
      //   case Some(value) => form.fill(value)
      // }



  def options: Seq[InputOption] = Seq(
    InputOption("true", "inquiry.dropdown.p1", Some("vat_vat-form")),
    InputOption("false", "inquiry.dropdown.p2", None),
    InputOption("false", "inquiry.dropdown.p3", None)
  )

      val preparedForm = form.fill(true)
      Ok(inquiry(appConfig, preparedForm, options))
  }

  def onSubmit() = //(identify andThen getData andThen requireData).async
  Action {
    implicit request =>
      // form.bindFromRequest().fold(
      //   (formWithErrors: Form[_]) =>
      //     Future.successful(BadRequest(takingOverBusiness(appConfig, formWithErrors, NormalMode))),
      //   (value) =>
      //     dataCacheConnector.save[Boolean](request.internalId, TakingO

    //verBusinessId.toString, value).map(cacheMap =>
      //       Redirect(navigator.nextPage(TakingOverBusinessId, NormalMode)(new UserAnswers(cacheMap))))
      // )
    ???
  }
}
