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
import helpers.TestITData._
import play.api.test.Helpers.{GET, POST, await, contentAsString, defaultAwaitTimeout, redirectLocation}
import play.api.test.Helpers.{route, status, writeableOf_AnyContentAsEmpty, writeableOf_AnyContentAsJson}
import helpers.messages._
import org.jsoup.Jsoup
import play.api.http.Status.{INTERNAL_SERVER_ERROR, NON_AUTHORITATIVE_INFORMATION, NOT_FOUND, OK, SEE_OTHER}
import play.api.libs.json.Json
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes._
import play.api.test.FakeRequest

class EnrolledForPTWithSAISpec extends IntegrationSpecBase {

  val urlPath: String = ItUrlPaths.enrolledPTWithSAOnAnyAccountPath

  s"GET $urlPath" when {
    s"the session cache has Account type of $SA_ASSIGNED_TO_CURRENT_USER" should {
      s"render the EnrolledForPTWithSA page ($urlPath)" in {
        await(save[String](sessionId, "redirectURL", returnUrl))
        await(
          save[AccountTypes.Value](sessionId, "ACCOUNT_TYPE", SA_ASSIGNED_TO_CURRENT_USER)
        )
        val authResponse = authoriseResponseJson()
        stubAuthorizePost(OK, authResponse.toString())
        stubPost(s"/write/.*", OK, """{"x":2}""")
        stubGet(
          s"/users-groups-search/users/$CREDENTIAL_ID",
          NON_AUTHORITATIVE_INFORMATION,
          usergroupsResponseJson().toString()
        )

        val request = FakeRequest(GET, "/protect-tax-info" + urlPath)
          .withSession(xSessionId, xAuthToken)
        val result = route(app, request).get
        val page = Jsoup.parse(contentAsString(result))

        redirectLocation(result) shouldBe None
        status(result) shouldBe OK
        page.title should include(EnrolledForPTPageMessages.title)
      }
    }

    List(PT_ASSIGNED_TO_OTHER_USER, PT_ASSIGNED_TO_CURRENT_USER, SINGLE_ACCOUNT)
      .foreach { accountType =>
        s"the session cache has Account type of $accountType" should {
          s"redirect to /protect-tax-info?redirectUrl=" in {
            await(save[String](sessionId, "redirectURL", returnUrl))
            await(
              save[AccountTypes.Value](sessionId, "ACCOUNT_TYPE", accountType)
            )
            val authResponse = authoriseResponseJson()
            stubAuthorizePost(OK, authResponse.toString())
            stubPost(s"/write/.*", OK, """{"x":2}""")

            val request = FakeRequest(GET, "/protect-tax-info" + urlPath)
              .withSession(xSessionId, xAuthToken)
            val result = route(app, request).get

            status(result) shouldBe SEE_OTHER
            redirectLocation(result).get should include(
              accountCheckPath
            )
          }
        }
      }

    "the session cache is empty" should {
      s"redirect to login" in {
        val authResponse = authoriseResponseJson()
        stubAuthorizePost(OK, authResponse.toString())
        stubPost(s"/write/.*", OK, """{"x":2}""")

        val request = FakeRequest(GET, "/protect-tax-info" + urlPath)
          .withSession(xAuthToken)
        val result = route(app, request).get

        status(result) shouldBe SEE_OTHER
        redirectLocation(result).get should include("/bas-gateway/sign-in")

      }
    }

    "an authorised user with no credential uses the service" should {
      s"render the error page" in {
        val authResponse = authoriseResponseJson()
        stubAuthorizePost(OK, authResponse.toString())
        await(save[String](sessionId, "redirectURL", returnUrl))
        await(save[AccountTypes.Value](sessionId, "ACCOUNT_TYPE", SA_ASSIGNED_TO_CURRENT_USER))
        stubPost(s"/write/.*", OK, """{"x":2}""")
        stubGetWithQueryParam(
          "/identity-verification/nino",
          "nino",
          NINO.nino,
          NOT_FOUND,
          ""
        )

        val request = FakeRequest(GET, "/protect-tax-info" + urlPath)
          .withSession(xSessionId, xAuthToken)
        val result = route(app, request).get

        redirectLocation(result) shouldBe None
        status(result) shouldBe INTERNAL_SERVER_ERROR
        contentAsString(result) should include(ErrorTemplateMessages.title)

      }
    }

    "an authorised user but IV returns internal error" should {
      s"return $INTERNAL_SERVER_ERROR" in {
        val authResponse = authoriseResponseJson()
        await(save[String](sessionId, "redirectURL", returnUrl))
        await(save[AccountTypes.Value](sessionId, "ACCOUNT_TYPE", SA_ASSIGNED_TO_CURRENT_USER))
        stubAuthorizePost(OK, authResponse.toString())
        stubPost(s"/write/.*", OK, """{"x":2}""")

        val request = FakeRequest(GET, "/protect-tax-info" + urlPath)
          .withSession(xSessionId, xAuthToken)
        val result = route(app, request).get

        status(result) shouldBe INTERNAL_SERVER_ERROR
        contentAsString(result) should include(ErrorTemplateMessages.title)

      }
    }

    "the user has a session missing required element NINO" should {
      s"redirect to ${ItUrlPaths.unauthorizedPath}" in {
        val authResponse = authoriseResponseJson(optNino = None)
        await(save[String](sessionId, "redirectURL", returnUrl))
        await(save[AccountTypes.Value](sessionId, "ACCOUNT_TYPE", SA_ASSIGNED_TO_CURRENT_USER))
        stubAuthorizePost(OK, authResponse.toString())
        stubPost(s"/write/.*", OK, """{"x":2}""")

        val request = FakeRequest(GET, "/protect-tax-info" + urlPath)
          .withSession(xSessionId, xAuthToken)
        val result = route(app, request).get

        status(result) shouldBe SEE_OTHER
        redirectLocation(result).get should include(ItUrlPaths.unauthorizedPath)

      }
    }

    "the user has a session missing required element Credentials" should {
      s"redirect to ${ItUrlPaths.unauthorizedPath}" in {
        val authResponse = authoriseResponseJson(optCreds = None)
        stubAuthorizePost(OK, authResponse.toString())
        stubPost(s"/write/.*", OK, """{"x":2}""")

        val request = FakeRequest(GET, "/protect-tax-info" + urlPath)
          .withSession(xSessionId, xAuthToken)
        val result = route(app, request).get

        status(result) shouldBe SEE_OTHER
        redirectLocation(result).get should include(ItUrlPaths.unauthorizedPath)

      }
    }

    "the user has a insufficient confidence level" should {
      s"redirect to ${ItUrlPaths.unauthorizedPath}" in {
        stubAuthorizePostUnauthorised(insufficientConfidenceLevel)
        stubPost(s"/write/.*", OK, """{"x":2}""")

        val request = FakeRequest(GET, "/protect-tax-info" + urlPath)
          .withSession(xSessionId, xAuthToken)
        val result = route(app, request).get

        status(result) shouldBe SEE_OTHER
        redirectLocation(result).get should include(ItUrlPaths.unauthorizedPath)

      }
    }

    "the user has no active session" should {
      s"redirect to login" in {
        stubAuthorizePostUnauthorised(sessionNotFound)
        stubPost(s"/write/.*", OK, """{"x":2}""")

        val request = FakeRequest(GET, "/protect-tax-info" + urlPath)
          .withSession(xSessionId, xAuthToken)
        val result = route(app, request).get

        status(result) shouldBe SEE_OTHER
        redirectLocation(result).get should include("/bas-gateway/sign-in")
      }
    }
  }

  s"POST $urlPath" when {
    "the session cache contains the redirect url" should {
      s"redirect to the redirect url" in {
        await(save[String](sessionId, "redirectURL", returnUrl))
        await(save[AccountTypes.Value](sessionId, "ACCOUNT_TYPE", PT_ASSIGNED_TO_CURRENT_USER))
        val authResponse = authoriseResponseJson()
        stubAuthorizePost(OK, authResponse.toString())
        stubPost(s"/write/.*", OK, """{"x":2}""")

        val request = FakeRequest(POST, "/protect-tax-info" + urlPath)
          .withSession(xSessionId, xAuthToken)
          .withJsonBody(Json.obj())
        val result = route(app, request).get

        status(result) shouldBe SEE_OTHER
        redirectLocation(result).get should include(returnUrl)
        recordExistsInMongo shouldBe false

      }
    }

    "the session cache is empty" should {
      s"redirect to login" in {
        val authResponse = authoriseResponseJson()
        stubAuthorizePost(OK, authResponse.toString())
        stubPost(s"/write/.*", OK, """{"x":2}""")

        val request = FakeRequest(POST, "/protect-tax-info" + urlPath)
          .withSession(xSessionId, xAuthToken)
          .withJsonBody(Json.obj())
        val result = route(app, request).get

        status(result) shouldBe SEE_OTHER
        redirectLocation(result).get should include("/bas-gateway/sign-in")

      }
    }
  }
}
