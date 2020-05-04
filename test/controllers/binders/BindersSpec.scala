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

import base.SpecBase
import models.ReturnLink

class BindersSpec extends SpecBase {

  "Binding a query return link" should {

    val validReturnLinkUrl = "returnLinkUrl"   -> Seq("9tNUeRTIYBD0RO+T5WRO7A]==")
    val validReturnLinkText = "returnLinkText" -> Seq("Q2xpY2sgaGVyZSB0byBiZSByZWRpcmVjdGVk")
    val emptyReturnLinkUrl = "returnLinkUrl"   -> Seq("")
    val emptyReturnLinkText = "returnLinkText" -> Seq("")

    "read the returnLinkUrl and returnLinkText if both present" in {
      controllers.binders.Binders.queryStringReturnLinkBinder
        .bind("anyValName", Map(validReturnLinkUrl, validReturnLinkText)) should contain(
        Right(ReturnLink(url = "9tNUeRTIYBD0RO+T5WRO7A]==", text = "Q2xpY2sgaGVyZSB0byBiZSByZWRpcmVjdGVk"))
      )
    }

    "fail if presented with an empty returnLinkText" in {
      controllers.binders.Binders.queryStringReturnLinkBinder
        .bind("anyValName", Map(validReturnLinkUrl, emptyReturnLinkText)) should
        be(Some(Left("Empty return link text provided")))
    }

    "fail if presented with an empty returnLinkUrl" in {
      controllers.binders.Binders.queryStringReturnLinkBinder
        .bind("anyValName", Map(emptyReturnLinkUrl, validReturnLinkText)) should
        be(Some(Left("Empty return link url provided")))
    }

    "fail if presented with both an empty returnLinkUrl and empty returnLinkText" in {
      controllers.binders.Binders.queryStringReturnLinkBinder
        .bind("anyValName", Map(emptyReturnLinkUrl, emptyReturnLinkText)) should
        be(Some(Left("Empty return link provided")))
    }

    "fail if the returnLinkUrl is not present" in {
      controllers.binders.Binders.queryStringReturnLinkBinder.bind("anyValName", Map(validReturnLinkText)) should
        be(Some(Left("Missing return link url")))
    }

    "fail if the returnLinkText is not present" in {
      controllers.binders.Binders.queryStringReturnLinkBinder.bind("anyValName", Map(validReturnLinkUrl)) should
        be(Some(Left("Missing return link text")))
    }
  }
}
