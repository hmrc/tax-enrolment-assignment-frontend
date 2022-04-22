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
import helpers.TestITData._
import helpers.WiremockHelper._
import helpers.messages._
import org.jsoup.Jsoup
import play.api.http.Status
import play.api.libs.json.Json
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes.{
  MULTIPLE_ACCOUNTS,
  PT_ASSIGNED_TO_CURRENT_USER,
  PT_ASSIGNED_TO_OTHER_USER,
  SINGLE_ACCOUNT
}

class EnrolledForPTISpec extends TestHelper with Status {

  val urlPath: String = UrlPaths.enrolledPTNoSAOnAnyAccountPath

  s"GET $urlPath" when {
    s"the session cache has Account type of $MULTIPLE_ACCOUNTS" should {
      s"render the EnrolledForPT page" in {
        await(save[String](sessionId, "redirectURL", UrlPaths.returnUrl))
        await(
          save[AccountTypes.Value](sessionId, "ACCOUNT_TYPE", MULTIPLE_ACCOUNTS)
        )
        val authResponse = authoriseResponseJson()
        stubAuthorizePost(OK, authResponse.toString())
        stubPost(s"/write/.*", OK, """{"x":2}""")
        stubGet(
          s"/users-group-search/users/$CREDENTIAL_ID",
          OK,
          usergroupsResponseJson().toString()
        )
        val res = buildRequest(urlPath, followRedirects = true)
          .withHttpHeaders(xSessionId, xRequestId, sessionCookie)
          .get()

        whenReady(res) { resp =>
          val page = Jsoup.parse(resp.body)

          resp.status shouldBe OK
          page.title should include(EnrolledForPTPageMessages.title)
        }
      }
    }

    List(PT_ASSIGNED_TO_OTHER_USER, PT_ASSIGNED_TO_CURRENT_USER, SINGLE_ACCOUNT)
      .foreach { accountType =>
        s"the session cache has Account type of $accountType" should {
          s"redirect to ${UrlPaths.accountCheckPath}" in {
            await(save[String](sessionId, "redirectURL", UrlPaths.returnUrl))
            await(
              save[AccountTypes.Value](sessionId, "ACCOUNT_TYPE", accountType)
            )
            val authResponse = authoriseResponseJson()
            stubAuthorizePost(OK, authResponse.toString())
            stubPost(s"/write/.*", OK, """{"x":2}""")
            val res = buildRequest(urlPath)
              .withHttpHeaders(xSessionId, xRequestId, sessionCookie)
              .get()

            whenReady(res) { resp =>
              resp.status shouldBe SEE_OTHER
              resp.header("Location").get should include(
                UrlPaths.accountCheckPath
              )
            }
          }
        }
      }

    "the session cache is empty" should {
      "render the error page" in {
        val authResponse = authoriseResponseJson()
        stubAuthorizePost(OK, authResponse.toString())
        stubPost(s"/write/.*", OK, """{"x":2}""")
        val res = buildRequest(urlPath, followRedirects = true)
          .withHttpHeaders(xSessionId, xRequestId, sessionCookie)
          .get()

        whenReady(res) { resp =>
          resp.status shouldBe INTERNAL_SERVER_ERROR
          resp.body should include(ErrorTemplateMessages.title)
        }
      }
    }

    "an authorised user with no credential uses the service" should {
      s"render the error page" in {
        val authResponse = authoriseResponseJson()
        stubAuthorizePost(OK, authResponse.toString())
        stubPost(s"/write/.*", OK, """{"x":2}""")
        stubGetWithQueryParam(
          "/identity-verification/nino",
          "nino",
          NINO,
          Status.NOT_FOUND,
          ""
        )
        val res = buildRequest(urlPath, followRedirects = true)
          .withHttpHeaders(xSessionId, xRequestId, sessionCookie)
          .get()

        whenReady(res) { resp =>
          resp.status shouldBe INTERNAL_SERVER_ERROR
          resp.body should include(ErrorTemplateMessages.title)
        }
      }
    }

    "an authorised user but IV returns internal error" should {
      s"render the error page" in {
        val authResponse = authoriseResponseJson()
        stubAuthorizePost(OK, authResponse.toString())
        stubPost(s"/write/.*", OK, """{"x":2}""")
        stubGetWithQueryParam(
          "/identity-verification/nino",
          "nino",
          NINO,
          Status.INTERNAL_SERVER_ERROR,
          ""
        )
        val res = buildRequest(urlPath, followRedirects = true)
          .withHttpHeaders(xSessionId, xRequestId, sessionCookie)
          .get()

        whenReady(res) { resp =>
          resp.status shouldBe INTERNAL_SERVER_ERROR
          resp.body should include(ErrorTemplateMessages.title)
        }
      }
    }

    "the user has a session missing required element NINO" should {
      s"redirect to ${UrlPaths.unauthorizedPath}" in {
        val authResponse = authoriseResponseJson(optNino = None)
        stubAuthorizePost(OK, authResponse.toString())
        stubPost(s"/write/.*", OK, """{"x":2}""")

        val res =
          buildRequest(urlPath)
            .withHttpHeaders(xSessionId, xRequestId, csrfContent, sessionCookie)
            .get()

        whenReady(res) { resp =>
          resp.status shouldBe SEE_OTHER
          resp.header("Location").get should include(UrlPaths.unauthorizedPath)
        }
      }
    }

    "the user has a session missing required element Credentials" should {
      s"redirect to ${UrlPaths.unauthorizedPath}" in {
        val authResponse = authoriseResponseJson(optCreds = None)
        stubAuthorizePost(OK, authResponse.toString())
        stubPost(s"/write/.*", OK, """{"x":2}""")

        val res =
          buildRequest(urlPath)
            .withHttpHeaders(xSessionId, xRequestId, csrfContent, sessionCookie)
            .get()

        whenReady(res) { resp =>
          resp.status shouldBe SEE_OTHER
          resp.header("Location").get should include(UrlPaths.unauthorizedPath)
        }
      }
    }

    "the user has a insufficient confidence level" should {
      s"redirect to ${UrlPaths.unauthorizedPath}" in {
        stubAuthorizePostUnauthorised(insufficientConfidenceLevel)
        stubPost(s"/write/.*", OK, """{"x":2}""")

        val res =
          buildRequest(urlPath)
            .withHttpHeaders(xSessionId, xRequestId, csrfContent, sessionCookie)
            .get()

        whenReady(res) { resp =>
          resp.status shouldBe SEE_OTHER
          resp.header("Location").get should include(UrlPaths.unauthorizedPath)
        }
      }
    }

    "the user has no active session" should {
      s"redirect to login" in {
        stubAuthorizePostUnauthorised(sessionNotFound)
        stubPost(s"/write/.*", OK, """{"x":2}""")

        val res = buildRequest(urlPath)
          .withHttpHeaders(xSessionId, xRequestId, csrfContent)
          .get()

        whenReady(res) { resp =>
          resp.status shouldBe SEE_OTHER
          resp.header("Location").get should include("/bas-gateway/sign-in")
        }
      }
    }
  }

  s"POST $urlPath" when {
    "the session cache contains the redirect url" should {
      s"redirect to the redirect url" in {
        await(save[String](sessionId, "redirectURL", UrlPaths.returnUrl))
        val authResponse = authoriseResponseJson()
        stubAuthorizePost(OK, authResponse.toString())
        stubPost(s"/write/.*", OK, """{"x":2}""")

        val res = buildRequest(urlPath, followRedirects = true)
          .withHttpHeaders(csrfContent, xSessionId, xRequestId, sessionCookie)
          .post(Json.obj())

        whenReady(res) { resp =>
          resp.status shouldBe OK
          resp.uri.toString shouldBe UrlPaths.returnUrl
        }
      }
    }

    "the session cache does not contain the redirect url" should {
      s"render the error page" in {
        val authResponse = authoriseResponseJson()
        stubAuthorizePost(OK, authResponse.toString())
        stubPost(s"/write/.*", OK, """{"x":2}""")

        val res = buildRequest(urlPath, followRedirects = true)
          .withHttpHeaders(xSessionId, xRequestId, sessionCookie, csrfContent)
          .post(Json.obj())

        whenReady(res) { resp =>
          resp.status shouldBe INTERNAL_SERVER_ERROR
          resp.body should include(ErrorTemplateMessages.title)
        }
      }
    }
  }
}
