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

import com.github.tomakehurst.wiremock.stubbing.StubMapping
import helpers.TestITData._
import helpers.WiremockHelper.{stubAuthorizePost}
import helpers.{TestHelper, ThrottleHelperISpec}
import play.api.http.Status
import play.api.libs.ws.DefaultWSCookie

class EnrolForSAControllerISpec extends TestHelper with Status with ThrottleHelperISpec {

  val urlPath: String = UrlPaths.enrolForSAPath


  s"GET to $urlPath" should {

    s"return $SEE_OTHER and redirect user to the business-tax-account when SA enrolment found" when {
      s"User hasSA == true" in {
        stubAuthoriseSuccess(hasSAEnrolment = true)
        val res = buildRequest(urlPath)
          .addCookies(DefaultWSCookie("mdtp", authAndSessionCookie))
          .addHttpHeaders(xSessionId, xRequestId, sessionCookie)
          .get()

        whenReady(res) { resp =>
          resp.status shouldBe SEE_OTHER
          resp.header("Location").get should include(appConfig.btaUrl)
        }
      }
    }

    s"return $INTERNAL_SERVER_ERROR and when No SA enrolment found" when {
      s"User hasSA == false" in {
        stubAuthoriseSuccess()
        val res = buildRequest(urlPath)
          .addCookies(DefaultWSCookie("mdtp", authAndSessionCookie))
          .addHttpHeaders(xSessionId, xRequestId, sessionCookie)
          .get()

        whenReady(res) { resp =>
          resp.status shouldBe INTERNAL_SERVER_ERROR
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
