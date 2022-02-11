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
import org.jsoup.Jsoup
import play.api.http.Status
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.testOnly

class FraudReportingControllerISpec extends IntegrationSpecBase with Status {

  val teaHost = s"localhost:$port"
  val urlPath =
    s"/report-IDs/select-IDs"

  s"GET $urlPath" when {
    "the user is authorised" should {
      s"render the underconstruction view" in {
        val authResponse = authoriseResponseJson(enrolments = ptEnrolmentOnly)
        stubAuthorizePost(OK, authResponse.toString())
        stubPost(s"/write/.*", OK, """{"x":2}""")
        val res = buildRequest(urlPath, followRedirects = true)
          .withHttpHeaders(xSessionId, csrfContent)
          .get()

        whenReady(res) { resp =>
          resp.status shouldBe OK
          val page = Jsoup.parse(resp.body)
          page.title() should include("Tax Enrolment Assignment Frontend")
          page.select("h1").text() shouldBe "Fraud Reporting - Select IDs"
          page
            .select("p")
            .text() shouldBe "Select IDs for accounts not recognised"
        }
      }
    }

    "the user has no session" should {
      s"return $UNAUTHORIZED" in {
        stubAuthorizePostUnauthorised("SessionRecordNotFound")
        stubPost(s"/write/.*", OK, """{"x":2}""")

        val res = buildRequest(urlPath)
          .withHttpHeaders(xSessionId, csrfContent)
          .get()

        whenReady(res) { resp =>
          resp.status shouldBe UNAUTHORIZED
        }
      }
    }
  }
}
