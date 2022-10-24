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

import helpers.TestHelper
import helpers.TestITData.{authoriseResponseJson, sessionId, xRequestId, xSessionId}
import helpers.WiremockHelper.{stubAuthorizePost, stubPost}
import play.api.http.Status
import play.api.libs.json.JsString
import play.api.libs.ws.DefaultWSCookie

import java.net.URLEncoder
import scala.concurrent.Future

class SignOutControllerISpec extends TestHelper with Status {

  val url: String = UrlPaths.signout

  def saveRedirectUrlToSession(optRedirectUrl: Option[String]): Future[Boolean] = {
    optRedirectUrl match {
      case Some(redirectUrl) => save(sessionId, Map("redirectURL" -> JsString(redirectUrl)))
      case None => save(sessionId, Map())
    }
  }

  s"GET $url" when {
    "the session cache contains a redirectUrl" should {
      "remove the session and redirect to gg signout with continue url" in {
        await(saveRedirectUrlToSession(Some(UrlPaths.returnUrl)))
        stubPost(s"/write/.*", OK, """{"x":2}""")
        val authResponse = authoriseResponseJson()
        stubAuthorizePost(OK, authResponse.toString())
        val res = buildRequest(url)
          .addCookies(DefaultWSCookie("mdtp", authAndSessionCookie))
          .addHttpHeaders(xSessionId, xRequestId)
          .get()

        whenReady(res) { resp =>
          resp.status shouldBe SEE_OTHER
          resp.header("Location").get should include(
            s"/bas-gateway/sign-out-without-state?continueUrl=${URLEncoder.encode(UrlPaths.returnUrl, "UTF-8")}"
          )
          await(sessionRepository().get(sessionId)) shouldBe None
        }
      }
    }

    "the session cache does not contain a redirectUrl" should {
      "remove the session and redirect to gg signout without continue url" in {
        await(saveRedirectUrlToSession(None))
        stubPost(s"/write/.*", OK, """{"x":2}""")
        val authResponse = authoriseResponseJson()
        stubAuthorizePost(OK, authResponse.toString())
        val res = buildRequest(url)
          .addCookies(DefaultWSCookie("mdtp", authAndSessionCookie))
          .addHttpHeaders(xSessionId, xRequestId)
          .get()

        whenReady(res) { resp =>
          resp.status shouldBe SEE_OTHER
          resp.header("Location").get should include(
            s"/bas-gateway/sign-out-without-state"
          )
          sessionRepository().get(sessionId).map(session => assert(session.isEmpty))

        }
      }
    }

    "the session cache does not contain a redirectUrl" should {
      "redirect to gg signout without continue url" in {
        await(saveRedirectUrlToSession(None))
        stubPost(s"/write/.*", OK, """{"x":2}""")
        val authResponse = authoriseResponseJson()
        stubAuthorizePost(OK, authResponse.toString())
        val res = buildRequest(url)
          .addCookies(DefaultWSCookie("mdtp", authAndSessionCookie))
          .addHttpHeaders(xSessionId, xRequestId)
          .get()

        whenReady(res) { resp =>
          resp.status shouldBe SEE_OTHER
          resp.header("Location").get should include(
            s"/bas-gateway/sign-out-without-state"
          )
        }
      }
    }
  }
}
