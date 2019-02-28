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

package models

import org.apache.commons.codec.binary.Base64
import play.api.libs.functional.syntax._
import play.api.libs.json.{Json, Writes, _}


case class ContactDetails(email: String)

object ContactDetails {

  implicit val format = Json.format[ContactDetails]
}

case class TwoWayMessage(contactDetails: ContactDetails, subject: String, content: String, replyTo: Option[String] = None)

object TwoWayMessage {

  implicit val twoWayMessageWrites: Writes[TwoWayMessage] = (
    (__ \ "contactDetails").write[ContactDetails] and
      (__ \ "subject").write[String] and
      (__ \ "content").write[String] and
      (__ \ "replyTo").writeNullable[String]
    ) ((m: TwoWayMessage) =>
      (m.contactDetails, m.subject, new String(Base64.encodeBase64String(m.content.getBytes("UTF-8"))), m.replyTo))
}

case class TwoWayMessageReply(content: String)

object TwoWayMessageReply {

    implicit val twoWayMessageReplyFormat = Json.format[TwoWayMessageReply]
/*  implicit val twoWayMessageReplyWrites: Writes[TwoWayMessageReply] = (
      (__ \ "content").write[String]
    ) ((m: TwoWayMessageReply) =>
      (new String(Base64.encodeBase64String(m.content.getBytes("UTF-8")))))*/
}

case class Identifier(id: String)

object Identifier {

  implicit val id = Json.reads[Identifier]
}

case class MessageError(text: String)



