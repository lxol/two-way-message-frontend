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

import org.scalatest.FunSuite

import play.api.libs.json.{Json, _}

class TwoWayMessageSpec extends FunSuite {
  import models.TwoWayMessageReply._

  test("TwoWayMessageReply should create json correctly") {
    val twoWayMessageReply = TwoWayMessageReply("Hello World")
    val json = Json.toJson(twoWayMessageReply)

    assert( json.toString === """{"content":"SGVsbG8gV29ybGQ="}""")
  }

  test("HTMLEncode - check CR are replaced with <br>") {
    val s =
      """Hello
        |World""".stripMargin

    val result = HTMLEncode.text2HTML(s)
    assert( "Hello <br />World" === result)
  }

  test("HTMLEncode - check ampersand") {
    val s = """Hello & World"""

    val result = HTMLEncode.text2HTML(s)
    assert( "Hello &amp; World" === result)
  }

  test("HTMLEncode - check angled brackets") {
    val s = """Hello <World>"""

    val result = HTMLEncode.text2HTML(s)
    assert( "Hello &lt;World&gt;" === result)
  }

  test("HTMLEncode - encoding") {
    val s = """Hello & <World>"""

    val result = HTMLEncode.encode(s)
    assert( "SGVsbG8gJmFtcDsgJmx0O1dvcmxkJmd0Ow==" === result)
  }


}
