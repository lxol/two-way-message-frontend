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

package forms

import javax.inject.Inject
import models.EnquiryDetails
import play.api.data.{Form, Forms, Mapping}
import play.api.data.Forms._
import play.api.data.validation.{Constraint, Invalid, Valid, ValidationError}
import play.api.i18n.{Lang, Messages, MessagesApi}

class EnquiryFormProvider @Inject()( messagesApi: MessagesApi)   {
  private val SUBJECT_MAX_LENGTH = 65


  val messages = messagesApi.preferred( Seq(Lang("en")))
  def apply(): Form[EnquiryDetails] =
    Form(
      mapping(
        "queue" -> nonEmptyText,
        "subject" -> nonEmptyTextWithError("Enter a subject").verifying( subjectConstraint),
        "question" -> nonEmptyTextWithError("Enter a question").verifying( contentConstraint),
        "email" -> email
      )(EnquiryDetails.apply)(EnquiryDetails.unapply)
    )


  def nonEmptyTextWithError(error: String): Mapping[String] = {
    Forms.text verifying Constraint[String]("constraint.required") { o =>
      if (o == null) Invalid(ValidationError(error)) else if (o.trim.isEmpty) Invalid(ValidationError(error)) else Valid
    }
  }


  val subjectConstraint: Constraint[String] = Constraint("constraints.subject")({
    plainText =>
      if (plainText.length <= 65) {
        Valid
      } else {
        Invalid(Seq(ValidationError("Subject has a maximum length of 65 characters")))
      }
  })

  val contentConstraint: Constraint[String] = Constraint("constraints.question")({
    plainText =>
      if (plainText.length <= 75000) {
        Valid
      } else {
        Invalid(Seq(ValidationError("Content has a maximum length of 75,000 characters")))
      }
  })
}
