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

package models

import org.joda.time.LocalDate
import play.api.libs.json._
import play.api.libs.json.{Json, Reads}
import models.FormId.FormId
import models.MessageType.MessageType

object MessageFormat {

  implicit val formIdFormat: Format[FormId] =
    Format(
      Reads.enumNameReads(FormId),
      Writes.enumNameWrites
    )
  implicit val messageTypeFormat: Format[MessageType] =
    Format(
      Reads.enumNameReads(MessageType),
      Writes.enumNameWrites
    )

  implicit val conversationItemDetailsFormat: Format[ConversationItemDetails] =
    Json.format[ConversationItemDetails]

  implicit val conversationItemFormat: Format[ConversationItem] =
    Json.format[ConversationItem]

}

object FormId extends Enumeration {

  type FormId = Value

  val Question = Value("2WSM-question")
  val Reply = Value("2WSM-reply")
}
object MessageType extends Enumeration {

  type MessageType = Value

  val Adviser = Value("2wsm-advisor")
  val Customer = Value("2wsm-customer")
}

case class Adviser(pidId: String)
object Adviser {
  implicit val adviserFormat: Format[Adviser] = Json.format[Adviser]
}

case class ConversationItemDetails(
    `type`: MessageType,
    form: FormId,
    issueDate: Option[LocalDate],
    replyTo: Option[String] = None,
    enquiryType: Option[String] = None,
    adviser: Option[Adviser] = None
)

case class ConversationItem(
    subject: String,
    body: Option[ConversationItemDetails],
    validFrom: LocalDate,
    content: Option[String]
)
