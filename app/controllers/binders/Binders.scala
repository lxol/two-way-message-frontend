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

package controllers.binders

import models.{Identifier, MessageError}
import play.api.mvc.QueryStringBindable

object Binders {

  implicit def queryStringIdentifierBinder(implicit stringBinder: QueryStringBindable[String]) = new QueryStringBindable[Identifier] {

    override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, Identifier]] = {
      stringBinder.bind(key,params) match {
        case Some(idParam) => idParam match {
          case Right(id) => Some(Right(Identifier(id)))
          case Left(_) => None
        }
        case _ => None
      }
    }

    override def unbind(key: String, identifier: Identifier): String = {
      stringBinder.unbind(key,identifier.id)
    }

  }

  implicit def queryStringMessageErrorBinder(implicit stringBinder: QueryStringBindable[String]) = new QueryStringBindable[MessageError] {

    override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, MessageError]] = {
      stringBinder.bind(key,params) match {
        case Some(errorParam) => errorParam match {
          case Right(error) => Some(Right(MessageError(error)))
          case Left(_) => None
        }
        case _ => None
      }
    }

    override def unbind(key: String, error: MessageError): String = {
      stringBinder.unbind(key,error.text)
    }
  }

}
