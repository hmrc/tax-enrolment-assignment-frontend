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

import helpers.IntegrationSpecBase
import helpers.TestITData._
import helpers.WiremockHelper.{stubAuthorizePost, stubPost}
import play.api.http.Status

class ContinueWithCurrentIdControllerISpec extends IntegrationSpecBase with Status {

  val teaHost = s"localhost:$port"
  val urlPath =
    s"/enrol-sa/enrol-current-user-id"

  s"GET $urlPath" when {
    "a user chooses to confirm the credentials that are currently being used in the session" should {
      s"return $OK with the confirmCurrentId page" in {
        val authResponse = authoriseResponseJson()
        stubAuthorizePost(OK, authResponse.toString())
        stubPost(s"/write/.*", OK, """{"x":2}""")

        val res = buildRequest(urlPath,followRedirects = true)
          .withHttpHeaders(xSessionId, csrfContent)
          .get()

        whenReady(res) { resp =>
          resp.status shouldBe OK
          resp.body should include(
            "Only your current user ID can access your personal tax account from now on"
          )
        }
      }
    }
  }
}