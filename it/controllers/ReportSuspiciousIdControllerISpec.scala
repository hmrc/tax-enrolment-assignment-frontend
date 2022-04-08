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
import play.api.libs.json.Json
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.testOnly
import play.api.test.Helpers._

class ReportSuspiciousIdControllerISpec
    extends IntegrationSpecBase
    with Status {

  val teaHost = s"localhost:$port"
  val urlPath =
    s"/enrol-pt/report-suspicious-id"
  val returnUrl: String = testOnly.routes.TestOnlyController.successfulCall
    .absoluteURL(false, teaHost)

  val sessionCookie
    : (String, String) = ("COOKIE" -> createSessionCookieAsString(sessionData))

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
          resp.body should include("Report a suspicious user ID")
        }
      }
    }
  }

  s"POST $urlPath" when {
    "the user has account type of SA_ASSIGNED_TO_OTHER_USER" should {
      s"enrol the user for PT and redirect to the EnroledAfterReportingFraud" in {
        await(save[String](sessionId, "redirectURL", returnUrl))
        await(
          save[AccountTypes.Value](
            sessionId,
            "ACCOUNT_TYPE",
            SA_ASSIGNED_TO_OTHER_USER
          )
        )
        val authResponse = authoriseResponseJson()
        stubAuthorizePost(OK, authResponse.toString())
        stubPost(s"/write/.*", OK, """{"x":2}""")
        stubPut(
          s"/tax-enrolments/service/HMRC-PT/enrolment",
          Status.NO_CONTENT,
          ""
        )
        val res = buildRequest(urlPath, followRedirects = false)
          .withHttpHeaders(xSessionId, xRequestId, sessionCookie, csrfContent)
          .post(Json.obj())

        whenReady(res) { resp =>
          resp.status shouldBe SEE_OTHER
          resp.header("Location").get should include(
            "/enrol-pt/enrolment-success-sa-access-not-wanted"
          )
        }
      }
    }

    "the user has account type of SA_ASSIGNED_TO_OTHER_USER but silent enrolment fails" should {
      s"render the error page" in {
        await(save[String](sessionId, "redirectURL", returnUrl))
        await(
          save[AccountTypes.Value](
            sessionId,
            "ACCOUNT_TYPE",
            SA_ASSIGNED_TO_OTHER_USER
          )
        )
        val authResponse = authoriseResponseJson()
        stubAuthorizePost(OK, authResponse.toString())
        stubPost(s"/write/.*", OK, """{"x":2}""")
        stubPut(
          s"/tax-enrolments/service/HMRC-PT/enrolment",
          Status.INTERNAL_SERVER_ERROR,
          ""
        )
        val res = buildRequest(urlPath, followRedirects = false)
          .withHttpHeaders(xSessionId, xRequestId, sessionCookie, csrfContent)
          .post(Json.obj())

        whenReady(res) { resp =>
          resp.status shouldBe OK
          resp.body should include("There was a problem")
        }
      }
    }

    List(
      SINGLE_ACCOUNT,
      PT_ASSIGNED_TO_OTHER_USER,
      PT_ASSIGNED_TO_CURRENT_USER,
      MULTIPLE_ACCOUNTS,
      SA_ASSIGNED_TO_CURRENT_USER
    ).foreach { accountType =>
      s"the session cache has Account type of $accountType" should {
        s"redirect to accountCheck" in {
          await(save[String](sessionId, "redirectURL", returnUrl))
          await(
            save[AccountTypes.Value](sessionId, "ACCOUNT_TYPE", accountType)
          )
          val authResponse = authoriseResponseJson()
          stubAuthorizePost(OK, authResponse.toString())
          stubPost(s"/write/.*", OK, """{"x":2}""")
          val res = buildRequest(urlPath, followRedirects = false)
            .withHttpHeaders(xSessionId, xRequestId, sessionCookie, csrfContent)
            .post(Json.obj())

          whenReady(res) { resp =>
            val page = Jsoup.parse(resp.body)

            resp.status shouldBe SEE_OTHER
            resp.header("Location").get should include(
              s"/tax-enrolment-assignment-frontend/no-pt-enrolment"
            )
          }
        }
      }
    }

    "the session cache is empty" should {
      "return Internal Server Error" in {
        val authResponse = authoriseResponseJson()
        stubAuthorizePost(OK, authResponse.toString())
        stubPost(s"/write/.*", OK, """{"x":2}""")
        val res = buildRequest(urlPath, followRedirects = true)
          .withHttpHeaders(xSessionId, xRequestId, sessionCookie, csrfContent)
          .post(Json.obj())

        whenReady(res) { resp =>
          val page = Jsoup.parse(resp.body)

          resp.status shouldBe INTERNAL_SERVER_ERROR
        }
      }
    }
  }
}
