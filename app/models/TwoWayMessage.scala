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

import org.apache.commons.codec.binary.Base64
import play.api.libs.functional.syntax._
import play.api.libs.json.{Json, Writes, _}
import play.twirl.api.HtmlFormat

import scala.xml.{Node, NodeBuffer, Text}

case class ContactDetails(email: String, telephone: Option[String])

object ContactDetails {

  implicit val format: OFormat[ContactDetails] = Json.format[ContactDetails]
}

case class TwoWayMessage(
    contactDetails: ContactDetails,
    subject: String,
    content: String,
    replyTo: Option[String] = None
)

object TwoWayMessage {

  implicit val twoWayMessageWrites: Writes[TwoWayMessage] = (
    (__ \ "contactDetails").write[ContactDetails] and
      (__ \ "subject").write[String] and
      (__ \ "content").write[String] and
      (__ \ "replyTo").writeNullable[String]
  )((m: TwoWayMessage) =>
    (
      m.contactDetails,
      HtmlFormat.escape(m.subject).body,
      HTMLEncoder.encode(m.content),
      m.replyTo
    )
  )
}

case class TwoWayMessageReply(content: String)

object TwoWayMessageReply {
  implicit val twoWayMessageReplyWrites: Writes[TwoWayMessageReply] =
    (JsPath \ "content")
      .write[String]
      .contramap((m: TwoWayMessageReply) => HTMLEncoder.encode(m.content))
}

case class Identifier(id: String)

object Identifier {

  implicit val id: Reads[Identifier] = Json.reads[Identifier]
}

case class MessageError(text: String)

object HTMLEncoder {

  def encode(text: String): String = {

    val xhtml = splitParas(text).map { para =>
      <p>{text2XML(para)}</p>
    }
    val xhtmlText = xhtml.mkString
    base64Encode(xhtmlText)
  }

  private def base64Encode(text: String): String =
    new String(Base64.encodeBase64String(text.getBytes("UTF-8")))

  private def splitParas(text: String): Seq[String] = {
    text.replaceAll("\r", "").split("[\\n]{2,}")
  }

  private def text2XML(text: String): Seq[Node] = {

    def build(c: Char): Node =
      c match {
        case '<'  => Text("<")
        case '>'  => Text(">")
        case '&'  => Text("&")
        case '\n' => <br/>
        case _    => Text(c.toString)
      }

    text.foldLeft[NodeBuffer](new NodeBuffer()) { (s, c) => s += build(c) }
  }

}
