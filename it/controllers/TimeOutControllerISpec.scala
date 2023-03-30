/*
 * Copyright 2023 HM Revenue & Customs
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

import helpers.{IntegrationSpecBase, ItUrlPaths}
import helpers.TestITData.{authoriseResponseJson, sessionId, xAuthToken, xSessionId}
import play.api.test.Helpers.{GET, await, contentAsString, defaultAwaitTimeout, route, status, writeableOf_AnyContentAsEmpty}
import helpers.messages.TimedOutMessages
import org.jsoup.Jsoup
import play.api.http.Status.{NO_CONTENT, OK}
import play.api.test.FakeRequest

import java.time.Instant
import scala.concurrent.Future

class TimeOutControllerISpec extends IntegrationSpecBase {

  val urlPathKeepAlive: String = ItUrlPaths.keepAlive
  val urlPathTimeout: String = ItUrlPaths.timeout

  def saveToSessionAndGetLastLoginDate: Future[Instant] = {
    save[String](sessionId, "redirectURL", returnUrl)
      .map(_ => getLastLoginDateTime(sessionId))
  }

  s"GET $urlPathKeepAlive" should {
    "extend the session cache and return NoContent" in {
      val initialLastLoginDate = await(saveToSessionAndGetLastLoginDate)
      stubPost(s"/write/.*", OK, """{"x":2}""")
      val authResponse = authoriseResponseJson()
      stubAuthorizePost(OK, authResponse.toString())
      Thread.sleep(2000)

      val request = FakeRequest(GET, "/protect-tax-info" + urlPathKeepAlive)
        .withSession(xAuthToken, xSessionId)
      val result = route(app, request).get

        status(result) shouldBe NO_CONTENT
        assert(getLastLoginDateTime(sessionId).isAfter(initialLastLoginDate))
    }
  }

  s"GET $urlPathTimeout" should {
  "return OK and render the timeout page" in {
    val request = FakeRequest(GET, "/protect-tax-info" + urlPathTimeout)
      .withSession(xAuthToken, xSessionId)
    val result = route(app, request).get

        status(result) shouldBe OK
        val page = Jsoup.parse(contentAsString(result))
        page.title should include(TimedOutMessages.title)

    }
  }
}
