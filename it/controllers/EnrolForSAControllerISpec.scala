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

import com.github.tomakehurst.wiremock.stubbing.StubMapping
import helpers.TestITData._
import helpers.WiremockHelper.{stubAuthorizePost, stubPost}
import helpers.messages.ErrorTemplateMessages
import helpers.{TestHelper, ThrottleHelperISpec}
import play.api.http.Status
import play.api.libs.ws.DefaultWSCookie

class EnrolForSAControllerISpec extends TestHelper with Status with ThrottleHelperISpec {

  val urlPath: String = UrlPaths.enrolForSAPath

  s"POST to $urlPath" should {

    s"return $SEE_OTHER and redirect user to the url returned from ADD TAXES FRONTEND" when {
      s"User hasSA == true" in {
        stubAuthoriseSuccess(hasSAEnrolment = true)
        stubPost("/internal/self-assessment/enrol-for-sa", OK, """{"redirectUrl" : "foo"}""")
        stubPost(s"/write/.*", OK, """{"x":2}""")
        val res = buildRequest(urlPath)
          .addCookies(DefaultWSCookie("mdtp", authAndSessionCookie))
          .addHttpHeaders(xSessionId, xRequestId, sessionCookie)
          .get()

        whenReady(res) { resp =>
          resp.status shouldBe SEE_OTHER
          resp.header("Location").get should include("foo")
        }
      }
    }
    s"return $INTERNAL_SERVER_ERROR" when {
      s"User hasSA == false" in {
        stubAuthoriseSuccess(hasSAEnrolment = false)
        stubPost(s"/write/.*", OK, """{"x":2}""")
        val res = buildRequest(urlPath)
          .addCookies(DefaultWSCookie("mdtp", authAndSessionCookie))
          .addHttpHeaders(xSessionId, xRequestId, sessionCookie)
          .get()

        whenReady(res) { resp =>
          resp.status shouldBe INTERNAL_SERVER_ERROR
          resp.body should include(ErrorTemplateMessages.title)
        }
      }
      "add taxes returns error" in {
        stubAuthoriseSuccess(hasSAEnrolment = true)
        stubPost("/internal/self-assessment/enrol-for-sa", INTERNAL_SERVER_ERROR, "")
        stubPost(s"/write/.*", OK, """{"x":2}""")
        val res = buildRequest(urlPath)
          .addCookies(DefaultWSCookie("mdtp", authAndSessionCookie))
          .addHttpHeaders(xSessionId, xRequestId, sessionCookie)
          .get()

        whenReady(res) { resp =>
          resp.status shouldBe INTERNAL_SERVER_ERROR
          resp.body should include(ErrorTemplateMessages.title)
        }
      }
    }
  }

  def stubAuthoriseSuccess(hasSAEnrolment: Boolean = false): StubMapping = {
    val authResponse = authoriseResponseJson(
      enrolments = if (hasSAEnrolment) { saEnrolmentOnly } else noEnrolments
    )
    stubAuthorizePost(OK, authResponse.toString())
  }
}
