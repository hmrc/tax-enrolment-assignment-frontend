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

import helpers.{IntegrationSpecBase, TestITData}
import helpers.WiremockHelper._
import helpers.TestITData._
import org.jsoup.Jsoup
import play.api.http.Status
import play.api.http.Status.OK
import play.api.libs.ws.DefaultWSCookie
import play.api.mvc._
import play.api.test.Helpers
import play.libs.ws.WSCookie
import uk.gov.hmrc.crypto.PlainText
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes.{
  MULTIPLE_ACCOUNTS,
  PT_ASSIGNED_TO_CURRENT_USER,
  PT_ASSIGNED_TO_OTHER_USER,
  SA_ASSIGNED_TO_CURRENT_USER,
  SA_ASSIGNED_TO_OTHER_USER,
  SINGLE_ACCOUNT
}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.testOnly
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.UsersAssignedEnrolment
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.SessionKeys.USER_ASSIGNED_PT_ENROLMENT

class PTEnrolmentOnOtherAccountControllerISpec
  extends IntegrationSpecBase
    with Status {

  val teaHost = s"localhost:$port"
  val returnUrl: String = testOnly.routes.TestOnlyController.successfulCall
    .absoluteURL(false, teaHost)
  val urlPath =
    s"/no-pt-enrolment"

  val sessionCookie
  : (String, String) = ("COOKIE" -> createSessionCookieAsString(sessionData))

  s"GET $urlPath" when {
    "the session cache has a credential for PT enrolment that is not the signed in account" should {
      s"render the pt on another account page" in {
        await(save[String](sessionId, "redirectURL", returnUrl))
        await(
          save[AccountTypes.Value](
            sessionId,
            "ACCOUNT_TYPE",
            PT_ASSIGNED_TO_OTHER_USER
          )
        )
        await(
          save[UsersAssignedEnrolment](
            sessionId,
            USER_ASSIGNED_PT_ENROLMENT,
            UsersAssignedEnrolment(Some(CREDENTIAL_ID_2))
          )
        )
        val authResponse = authoriseResponseJson()
        stubAuthorizePost(OK, authResponse.toString())
        stubPost(s"/write/.*", OK, """{"x":2}""")
        stubGet(
          s"/users-group-search/users/$CREDENTIAL_ID_2",
          OK,
          usergroupsResponseJson().toString()
        )
        val res = buildRequest(urlPath, followRedirects = true)
          .withHttpHeaders(xSessionId, xRequestId, sessionCookie)
          .get()

        whenReady(res) { resp =>
          val page = Jsoup.parse(resp.body)

          resp.status shouldBe OK
          page.title should include(
            TestITData.ptEnrolledOnOtherAccountPageTitle
          )
        }
      }
    }

    List(
      SINGLE_ACCOUNT,
      PT_ASSIGNED_TO_CURRENT_USER,
      MULTIPLE_ACCOUNTS,
      SA_ASSIGNED_TO_OTHER_USER,
      SA_ASSIGNED_TO_CURRENT_USER
    ).foreach { accountType =>
      s"the session cache has a credential with account type ${accountType.toString}" should {
        s"redirect to account check page" in {
          await(save[String](sessionId, "redirectURL", returnUrl))
          await(
            save[AccountTypes.Value](sessionId, "ACCOUNT_TYPE", accountType)
          )
          val authResponse = authoriseResponseJson()
          stubAuthorizePost(OK, authResponse.toString())
          stubPost(s"/write/.*", OK, """{"x":2}""")
          val res = buildRequest(urlPath, followRedirects = false)
            .withHttpHeaders(xSessionId, xRequestId, sessionCookie)
            .get()

          whenReady(res) { resp =>
            val page = Jsoup.parse(resp.body)

            resp.status shouldBe SEE_OTHER
            resp.header("Location").get should include(
              s"/protect-tax-info"
            )
          }
        }
      }
    }

    s"the session cache has a credential for PT enrolment that is the signed in account" should {
      s"render the error page" in {
        await(save[String](sessionId, "redirectURL", returnUrl))
        await(
          save[AccountTypes.Value](
            sessionId,
            "ACCOUNT_TYPE",
            PT_ASSIGNED_TO_OTHER_USER
          )
        )
        await(
          save[UsersAssignedEnrolment](
            sessionId,
            USER_ASSIGNED_PT_ENROLMENT,
            UsersAssignedEnrolment(Some(CREDENTIAL_ID))
          )
        )
        val authResponse = authoriseResponseJson()
        stubAuthorizePost(OK, authResponse.toString())
        stubPost(s"/write/.*", OK, """{"x":2}""")
        val res = buildRequest(urlPath, followRedirects = false)
          .withHttpHeaders(xSessionId, xRequestId, sessionCookie)
          .get()

        whenReady(res) { resp =>
          resp.status shouldBe OK
          resp.body should include("Sorry, there is a problem with the service")
        }
      }
    }

    s"the session cache has no credentials with PT enrolment" should {
      s"render the error page" in {
        await(save[String](sessionId, "redirectURL", returnUrl))
        await(
          save[AccountTypes.Value](
            sessionId,
            "ACCOUNT_TYPE",
            PT_ASSIGNED_TO_OTHER_USER
          )
        )
        val authResponse = authoriseResponseJson()
        stubAuthorizePost(OK, authResponse.toString())
        await(
          save[UsersAssignedEnrolment](
            sessionId,
            USER_ASSIGNED_PT_ENROLMENT,
            UsersAssignedEnrolment(None)
          )
        )
        stubPost(s"/write/.*", OK, """{"x":2}""")
        val res = buildRequest(urlPath, followRedirects = false)
          .withHttpHeaders(xSessionId, xRequestId, sessionCookie)
          .get()

        whenReady(res) { resp =>
          resp.status shouldBe OK
          resp.body should include("Sorry, there is a problem with the service")
        }
      }
    }

    "the session cache has no redirectUrl" should {
      "render the error page" in {
        val authResponse = authoriseResponseJson()
        stubAuthorizePost(OK, authResponse.toString())
        stubPost(s"/write/.*", OK, """{"x":2}""")
        val res = buildRequest(urlPath, followRedirects = true)
          .withHttpHeaders(xSessionId, xRequestId, sessionCookie)
          .get()

        whenReady(res) { resp =>
          resp.status shouldBe OK
          resp.body should include("Sorry, there is a problem with the service")
        }
      }
    }

    "users group search returns an error" should {
      "render the error page" in {
        await(save[String](sessionId, "redirectURL", returnUrl))
        await(
          save[AccountTypes.Value](
            sessionId,
            "ACCOUNT_TYPE",
            PT_ASSIGNED_TO_OTHER_USER
          )
        )
        await(
          save[UsersAssignedEnrolment](
            sessionId,
            USER_ASSIGNED_PT_ENROLMENT,
            UsersAssignedEnrolment(Some(CREDENTIAL_ID_2))
          )
        )
        val authResponse = authoriseResponseJson()
        stubAuthorizePost(OK, authResponse.toString())
        stubPost(s"/write/.*", OK, """{"x":2}""")
        stubGet(
          s"/users-group-search/users/$CREDENTIAL_ID_2",
          INTERNAL_SERVER_ERROR,
          ""
        )
        val res = buildRequest(urlPath, followRedirects = true)
          .withHttpHeaders(xSessionId, xRequestId, sessionCookie)
          .get()

        whenReady(res) { resp =>
          resp.status shouldBe OK
          resp.body should include("Sorry, there is a problem with the service")
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
          resp.status shouldBe OK
          resp.body should include("Sorry, there is a problem with the service")
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
          resp.status shouldBe OK
          resp.body should include("Sorry, there is a problem with the service")
        }
      }
    }

    "the user has a session missing required element NINO" should {
      s"return $SEE_OTHER" in {
        val authResponse = authoriseResponseJson(optNino = None)
        stubAuthorizePost(OK, authResponse.toString())
        stubPost(s"/write/.*", OK, """{"x":2}""")

        val res =
          buildRequest(urlPath)
            .withHttpHeaders(xSessionId, xRequestId, csrfContent, sessionCookie)
            .get()

        whenReady(res) { resp =>
          resp.status shouldBe SEE_OTHER
          resp.header("Location").get should include("/unauthorised")
        }
      }
    }

    "the user has a session missing required element Credentials" should {
      s"return $SEE_OTHER" in {
        val authResponse = authoriseResponseJson(optCreds = None)
        stubAuthorizePost(OK, authResponse.toString())
        stubPost(s"/write/.*", OK, """{"x":2}""")

        val res =
          buildRequest(urlPath)
            .withHttpHeaders(xSessionId, xRequestId, csrfContent, sessionCookie)
            .get()

        whenReady(res) { resp =>
          resp.status shouldBe SEE_OTHER
          resp.header("Location").get should include("/unauthorised")
        }
      }
    }

    "the user has a insufficient confidence level" should {
      s"return $SEE_OTHER" in {
        stubAuthorizePostUnauthorised(insufficientConfidenceLevel)
        stubPost(s"/write/.*", OK, """{"x":2}""")

        val res =
          buildRequest(urlPath)
            .withHttpHeaders(xSessionId, xRequestId, csrfContent, sessionCookie)
            .get()

        whenReady(res) { resp =>
          resp.status shouldBe SEE_OTHER
          resp.header("Location").get should include("/unauthorised")
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
}
