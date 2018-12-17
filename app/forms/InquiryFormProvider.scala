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

package forms

import javax.inject.Inject

import forms.mappings.Mappings
import play.api.data.{Form, Mapping}
import play.api.data.Forms._
import models.InquiryDetails
import utils.InputOption

class InquiryFormProvider @Inject() extends FormErrorHelper with Mappings {

  def apply(queueOptions: Seq[InputOption]): Form[InquiryDetails] =
    Form(
      mapping(
        "queue" -> text(),
          // queueMapping(queueOptions, "messages__error_country_required", "messages__error_country_invalid"),

        "text" -> text("companyAddressDetails.error.field2.required")
          .verifying(maxLength(100, "companyAddressDetails.error.field2.length"))
      )(InquiryDetails.apply)(InquiryDetails.unapply)
    )


  // def queueMapping(countryOptions: InputOption, keyRequired: String, keyInvalid: String): Mapping[String] = {
  //   text(keyRequired)
  //     .verifying(country(countryOptions, keyInvalid))
  // }

}
