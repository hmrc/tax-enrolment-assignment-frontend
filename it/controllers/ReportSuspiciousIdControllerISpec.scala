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
import helpers.WiremockHelper._
import play.api.http.Status

class ReportSuspiciousIdControllerISpec extends IntegrationSpecBase with Status {

  val teaHost = s"localhost:$port"
  val urlPath =
    s"/enrol-pt/report-suspicious-id"

  s"GET $urlPath" when {
    "the user is authorised" should {
      s"return 200 and render the report suspicious ID page" in {
        val authResponse = authoriseResponseJson()
        stubAuthorizePost(OK, authResponse.toString())
        stubPost(s"/write/.*", OK, """{"x":2}""")
        val res = buildRequest(urlPath, followRedirects = true)
          .withHttpHeaders(xSessionId, csrfContent)
          .get()

        whenReady(res) { resp =>
          resp.status shouldBe OK
          resp.body should include(
            "Report a suspicious user ID"
          )
        }
      }
    }

  }
}
