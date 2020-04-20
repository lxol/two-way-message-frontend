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

package utils

import com.google.inject.Inject
import connectors.TwoWayMessageConnector
import models._
import org.joda.time.LocalDate
import org.joda.time.format.DateTimeFormat
import play.twirl.api.Html
import views.html.rendered_messages
import views.html.rendered_message

class MessageRenderer {

  def renderMessages(messages: List[ConversationItem]): Html =
    rendered_messages(messages)

  def renderMessage(message: ConversationItem): Html = rendered_message(message)

}

object DateUtils {
  def getDateText(message: ConversationItem): String = {
    val messageDate = extractMessageDate(message)
    message.body
      .map { details =>
        details.`type` match {
          case MessageType.Customer =>
            s"You sent this message on $messageDate"
          case MessageType.Adviser =>
            s"This message was sent to you on $messageDate"
          case _ => defaultDateText(messageDate)
        }
      }
      .getOrElse(defaultDateText(messageDate))
  }

  def extractMessageDate(message: ConversationItem): String =
    message.body.get.issueDate match {
      case Some(issueDate) => formatter(issueDate)
      case None            => formatter(message.validFrom)
    }
  private def defaultDateText(dateStr: String) =
    s"This message was sent on $dateStr"

  private val dateFormatter = DateTimeFormat.forPattern("dd MMMM yyyy")
  private def formatter(date: LocalDate): String = date.toString(dateFormatter)

}
