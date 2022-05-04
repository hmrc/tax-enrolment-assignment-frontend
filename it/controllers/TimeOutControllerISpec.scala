/*
 * Copyright 2022 HM Revenue & Customs
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

package controllers

import java.time.LocalDateTime

import helpers.TestHelper
import helpers.TestITData.{
  authoriseResponseJson,
  sessionId,
  xRequestId,
  xSessionId
}
import helpers.WiremockHelper.{stubAuthorizePost, stubPost}
import helpers.messages.{EnrolledForPTPageMessages, TimedOutMessages}
import org.jsoup.Jsoup
import play.api.http.Status
import play.api.libs.ws.DefaultWSCookie

import scala.concurrent.Future

class TimeOutControllerISpec extends TestHelper with Status {

  val urlPathKeepAlive: String = UrlPaths.keepAlive
  val urlPathTimeout: String = UrlPaths.timeout

  def saveToSessionAndGetLastLoginDate: Future[LocalDateTime] = {
    save[String](sessionId, "redirectURL", UrlPaths.returnUrl)
      .map(_ => getLastLoginDateTime(sessionId))
  }

  s"GET $urlPathKeepAlive" should {
    "extend the session cache and return NoContent" in {
      val initialLastLoginDate = await(saveToSessionAndGetLastLoginDate)
      stubPost(s"/write/.*", OK, """{"x":2}""")
      val authResponse = authoriseResponseJson()
      stubAuthorizePost(OK, authResponse.toString())
      Thread.sleep(2000)
      val res = buildRequest(urlPathKeepAlive)
        .addCookies(DefaultWSCookie("mdtp", authAndSessionCookie))
        .addHttpHeaders(xSessionId, xRequestId)
        .get()

      whenReady(res) { resp =>
        resp.status shouldBe NO_CONTENT
        assert(getLastLoginDateTime(sessionId).isAfter(initialLastLoginDate))
      }
    }
  }

  s"GET $urlPathTimeout" should {
    "remove the cache, return OK and render the timeout page" in {
      await(save[String](sessionId, "redirectURL", UrlPaths.returnUrl))
      val authResponse = authoriseResponseJson()
      stubAuthorizePost(OK, authResponse.toString())
      stubPost(s"/write/.*", OK, """{"x":2}""")

      val res = buildRequest(urlPathTimeout)
        .addCookies(DefaultWSCookie("mdtp", authAndSessionCookie))
        .addHttpHeaders(xSessionId, xRequestId)
        .get()

      whenReady(res) { resp =>
        resp.status shouldBe OK
        val page = Jsoup.parse(resp.body)
        page.title should include(TimedOutMessages.title)
        assert(await(getEntry[String](sessionId, "redirectURL")).isEmpty)
      }
    }
  }
}
