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

package uk.gov.hmrc.twowaymessagefrontend.util

import config.{ AppConfig, FrontendAppConfig }
import forms.EnquiryFormProvider
import play.api.i18n.{ Messages, MessagesApi }

trait ControllerSpecBase extends SpecBase {

  lazy val appConfig: AppConfig = injector.instanceOf[FrontendAppConfig]
  lazy val messagesApi: MessagesApi = injector.instanceOf[MessagesApi]
  lazy val formProvider: EnquiryFormProvider =
    injector.instanceOf[EnquiryFormProvider]
  lazy val messages: Messages = messagesApi.preferred(fakeRequest)
}
