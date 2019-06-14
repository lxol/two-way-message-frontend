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
    (m.contactDetails, m.subject, HTMLEncode.encode(m.content), m.replyTo) )
}

case class TwoWayMessageReply(content: String)

object TwoWayMessageReply {
     implicit val twoWayMessageReplyWrites: Writes[TwoWayMessageReply] =
     (( JsPath \ "content").write[String]).contramap((m: TwoWayMessageReply) => HTMLEncode.encode(m.content))
}

case class Identifier(id: String)

object Identifier {

  implicit val id = Json.reads[Identifier]
}

case class MessageError(text: String)


object HTMLEncode {

  def encode( s:String ): String =
    new String(Base64.encodeBase64String(HTMLEncode.text2HTML(s).getBytes("UTF-8")))

  def text2HTML( txt:String): String = {
    def build( c:Char): String = c match {
      case '<' => "&lt;"
      case '>' => "&gt;"
      case '&' => "&amp;"
      case '\n' => " <br />"
      case c =>  c.toString
    }

    txt.foldLeft(""){ (s,c) => s + build(c)}
  }

}


