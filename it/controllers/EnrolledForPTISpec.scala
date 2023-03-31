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

import helpers.{IntegrationSpecBase, ItUrlPaths, ThrottleHelperISpec}
import helpers.TestITData._
import play.api.test.Helpers.{GET, POST, await, contentAsString, defaultAwaitTimeout, redirectLocation, route, status}
import play.api.test.Helpers.{writeableOf_AnyContentAsEmpty, writeableOf_AnyContentAsJson}
import helpers.messages._
import org.jsoup.Jsoup
import play.api.http.Status.{INTERNAL_SERVER_ERROR, NON_AUTHORITATIVE_INFORMATION, NOT_FOUND, OK, SEE_OTHER}
import play.api.libs.json.Json
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes._
import play.api.test.FakeRequest

class EnrolledForPTISpec extends IntegrationSpecBase with ThrottleHelperISpec {

  val urlPath: String = ItUrlPaths.enrolledPTNoSAOnAnyAccountPath

  s"GET $urlPath" when {

    throttleSpecificTests { () =>
      val request = FakeRequest(GET, "/protect-tax-info" + urlPath)
        .withSession(xSessionId, xAuthToken)
      route(app, request).get
    }

    s"the session cache has Account type of $MULTIPLE_ACCOUNTS" should {
      s"render the EnrolledForPT page" in {
        await(save[String](sessionId, "redirectURL", returnUrl))
        await(
          save[AccountTypes.Value](sessionId, "ACCOUNT_TYPE", MULTIPLE_ACCOUNTS)
        )
        stubAuthorizePost(OK, authoriseResponseWithPTEnrolment().toString())
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

        status(result) shouldBe OK
        page.title should include(EnrolledForPTPageMessages.title)

      }
    }

    List(PT_ASSIGNED_TO_OTHER_USER, PT_ASSIGNED_TO_CURRENT_USER, SINGLE_ACCOUNT)
      .foreach { accountType =>
        s"the session cache has Account type of $accountType" should {
          s"redirect to {accountCheckPath}" in {
            await(save[String](sessionId, "redirectURL", returnUrl))
            await(
              save[AccountTypes.Value](sessionId, "ACCOUNT_TYPE", accountType)
            )
            stubAuthorizePost(OK, authoriseResponseWithPTEnrolment().toString())
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
        stubAuthorizePost(OK, authoriseResponseWithPTEnrolment().toString())
        stubPost(s"/write/.*", OK, """{"x":2}""")

        val request = FakeRequest(GET, "/protect-tax-info" + urlPath)
          .withSession(xSessionId, xAuthToken)
        val result = route(app, request).get

        status(result) shouldBe SEE_OTHER
        redirectLocation(result).get should include("/bas-gateway/sign-in")

      }
    }

    "an authorised user with no credential uses the service" should {
      s"render the error page" in {
        stubAuthorizePost(OK, authoriseResponseWithPTEnrolment().toString())
        await(save[String](sessionId, "redirectURL", returnUrl))
        await(save[AccountTypes.Value](sessionId, "ACCOUNT_TYPE", SA_ASSIGNED_TO_CURRENT_USER))
        stubPost(s"/write/.*", OK, """{"x":2}""")
        stubGetWithQueryParam(
          "/identity-verification/nino",
          "nino",
          NINO,
          NOT_FOUND,
          ""
        )

        val request = FakeRequest(GET, "/protect-tax-info" + urlPath)
          .withSession(xSessionId, xAuthToken)
        val result = route(app, request).get

        status(result) shouldBe INTERNAL_SERVER_ERROR
        contentAsString(result) should include(ErrorTemplateMessages.title)
      }
    }

    "an authorised user but IV returns internal error" should {
      s"return $INTERNAL_SERVER_ERROR" in {
        await(save[String](sessionId, "redirectURL", returnUrl))
        await(save[AccountTypes.Value](sessionId, "ACCOUNT_TYPE", SA_ASSIGNED_TO_CURRENT_USER))
        stubAuthorizePost(OK, authoriseResponseWithPTEnrolment().toString())
        stubPost(s"/write/.*", OK, """{"x":2}""")
        stubGetWithQueryParam(
          "/identity-verification/nino",
          "nino",
          NINO,
          INTERNAL_SERVER_ERROR,
          ""
        )

        val request = FakeRequest(GET, "/protect-tax-info" + urlPath)
          .withSession(xSessionId, xAuthToken)
        val result = route(app, request).get

        status(result) shouldBe INTERNAL_SERVER_ERROR
        contentAsString(result) should include(ErrorTemplateMessages.title)

      }
    }

    "the user has a session missing required element NINO" should {
      s"redirect to ${ItUrlPaths.unauthorizedPath}" in {
        val authResponse = authoriseResponseWithPTEnrolment(optNino = None)
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
        val authResponse = authoriseResponseWithPTEnrolment(optCreds = None)
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

    throttleSpecificTests { () =>
      val request = FakeRequest(POST, "/protect-tax-info" + urlPath)
        .withSession(xSessionId, xAuthToken)
        .withJsonBody(Json.obj())
      route(app, request).get
    }

    "the session cache contains the redirect url" should {
      s"redirect to the redirect url" in {
        await(save[String](sessionId, "redirectURL", returnUrl))
        await(save[AccountTypes.Value](sessionId, "ACCOUNT_TYPE", PT_ASSIGNED_TO_CURRENT_USER))
        val authResponse = authoriseResponseWithPTEnrolment()
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
        val authResponse = authoriseResponseWithPTEnrolment()
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
