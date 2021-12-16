/*
 * Copyright 2021 HM Revenue & Customs
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

import helpers.IntegrationSpecBase
import helpers.WiremockHelper._
import helpers.TestITData._

import play.api.http.Status

class AccountCheckControllerISpec extends IntegrationSpecBase with Status {

  val urlPath = "/account-check?redirectUrl=http%3A%2F%2Fexample.com"

  s"GET $urlPath" when {
    "the user is authorised to use the service" should {
      s"return $OK" in {
        val authResponse = authoriseResponseJson()
        stubAuthorizePost(OK, authResponse.toString())
        stubPost(s"/write/.*", OK, """{"x":2}""")

        val res = buildRequest(urlPath).withHttpHeaders(xSessionId, csrfContent).get()

        whenReady(res) {resp =>
          resp.status shouldBe OK
        }
      }
    }

    "the user has a session missing required element NINO" should {
      s"return $UNAUTHORIZED" in {
        val authResponse = authoriseResponseJson(optNino = None)
        stubAuthorizePost(OK, authResponse.toString())
        stubPost(s"/write/.*", OK, """{"x":2}""")

        val res = buildRequest(urlPath).withHttpHeaders(xSessionId, csrfContent).get()

        whenReady(res) {resp =>
          resp.status shouldBe UNAUTHORIZED
        }
      }
    }

    "the user has a session missing required element Credentials" should {
      s"return $UNAUTHORIZED" in {
        val authResponse = authoriseResponseJson(optCreds = None)
        stubAuthorizePost(OK, authResponse.toString())
        stubPost(s"/write/.*", OK, """{"x":2}""")

        val res = buildRequest(urlPath).withHttpHeaders(xSessionId, csrfContent).get()

        whenReady(res) {resp =>
          resp.status shouldBe UNAUTHORIZED
        }
      }
    }

    List(sessionNotFound, insufficientConfidenceLevel).foreach{ failureReason =>
      s"the auth returns 401 and the user has an $failureReason" should {
        s"return $UNAUTHORIZED" in {
          stubAuthorizePostUnauthorised(failureReason)
          stubPost(s"/write/.*", OK, """{"x":2}""")

          val res = buildRequest(urlPath).withHttpHeaders(xSessionId, csrfContent).get()

          whenReady(res) {resp =>
            resp.status shouldBe UNAUTHORIZED
          }
        }
      }
    }
  }
}
