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

import helpers.{TestHelper, ThrottleHelperISpec}
import helpers.TestITData._
import helpers.WiremockHelper._
import helpers.messages._
import org.jsoup.Jsoup
import play.api.http.Status
import play.api.libs.json.{JsString, Json}
import play.api.libs.ws.DefaultWSCookie
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.{AccountDetails, UsersAssignedEnrolment}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.SessionKeys._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.reporting.AuditEvent

class ReportSuspiciousIDControllerISpec extends TestHelper with Status with ThrottleHelperISpec {

  val urlPathSA: String = UrlPaths.reportFraudSAAccountPath
  val urlPathPT: String = UrlPaths.reportFraudPTAccountPath

  s"GET $urlPathSA" when {

    throttleSpecificTests(() => buildRequest(urlPathSA)
      .addCookies(DefaultWSCookie("mdtp", authAndSessionCookie))
      .addHttpHeaders(xSessionId, xRequestId, sessionCookie)
      .get())

    "the session cache has a credential for SA enrolment that is not the signed in account" should {
      s"render the report suspiciousId page with a continue button" in {
        await(save[String](sessionId, "redirectURL", UrlPaths.returnUrl))
        await(
          save[AccountTypes.Value](
            sessionId,
            "ACCOUNT_TYPE",
            SA_ASSIGNED_TO_OTHER_USER
          )
        )
        await(
          save[UsersAssignedEnrolment](
            sessionId,
            USER_ASSIGNED_SA_ENROLMENT,
            UsersAssignedEnrolment(Some(CREDENTIAL_ID_2))
          )
        )
        val authResponse = authoriseResponseJson()
        stubAuthorizePost(OK, authResponse.toString())
        stubPost(s"/write/.*", OK, """{"x":2}""")
        stubGet(
          s"/users-groups-search/users/$CREDENTIAL_ID_2",
          NON_AUTHORITATIVE_INFORMATION,
          usergroupsResponseJson().toString()
        )
        val res = buildRequest(urlPathSA, followRedirects = true)
          .addCookies(DefaultWSCookie("mdtp", authAndSessionCookie))
          .addHttpHeaders(xSessionId, xRequestId, sessionCookie)
          .get()

        whenReady(res) { resp =>
          val page = Jsoup.parse(resp.body)

          resp.status shouldBe OK
          page.title should include(ReportSuspiciousIDMessages.title)
          page.getElementsByClass("govuk-button").size() shouldBe 1
          val saAccountDetails =
            new AccountDetails(usersGroupSearchResponse, CREDENTIAL_ID_2)
          val expectedAuditEvent = AuditEvent.auditReportSuspiciousSAAccount(
            saAccountDetails
          )(requestWithAccountType(SA_ASSIGNED_TO_OTHER_USER), messagesApi)
          verifyAuditEventSent(expectedAuditEvent)
        }
      }
    }

    "the session cache has a credential for SA enrolment that is not the signed in account" should {
      s"still render the report suspiciousId page with a continue button" when {
        "the current user has been assigned a PT enrolment" in {
          await(save[String](sessionId, "redirectURL", UrlPaths.returnUrl))
          await(
            save[AccountTypes.Value](
              sessionId,
              "ACCOUNT_TYPE",
              SA_ASSIGNED_TO_OTHER_USER
            )
          )
          await(
            save[UsersAssignedEnrolment](
              sessionId,
              USER_ASSIGNED_SA_ENROLMENT,
              UsersAssignedEnrolment(Some(CREDENTIAL_ID_2))
            )
          )
          val authResponse = authoriseResponseWithPTEnrolment()
          stubAuthorizePost(OK, authResponse.toString())
          stubPost(s"/write/.*", OK, """{"x":2}""")
          stubGet(
            s"/users-groups-search/users/$CREDENTIAL_ID_2",
            NON_AUTHORITATIVE_INFORMATION,
            usergroupsResponseJson().toString()
          )
          val res = buildRequest(urlPathSA, followRedirects = true)
            .addCookies(DefaultWSCookie("mdtp", authAndSessionCookie))
            .addHttpHeaders(xSessionId, xRequestId, sessionCookie)
            .get()

          whenReady(res) { resp =>
            val page = Jsoup.parse(resp.body)

            resp.status shouldBe OK
            page.title should include(ReportSuspiciousIDMessages.title)
            page.getElementsByClass("govuk-button").size() shouldBe 1
            val saAccountDetails =
              new AccountDetails(usersGroupSearchResponse, CREDENTIAL_ID_2)
            val expectedAuditEvent = AuditEvent.auditReportSuspiciousSAAccount(
              saAccountDetails
            )(requestWithAccountType(SA_ASSIGNED_TO_OTHER_USER), messagesApi)
          }
        }
      }
    }

    List(
      SINGLE_ACCOUNT,
      PT_ASSIGNED_TO_CURRENT_USER,
      MULTIPLE_ACCOUNTS,
      PT_ASSIGNED_TO_OTHER_USER,
      SA_ASSIGNED_TO_CURRENT_USER
    ).foreach { accountType =>
      s"the session cache has a credential with account type ${accountType.toString}" should {
        s"redirect to ${UrlPaths.accountCheckPath}" in {
          await(save[String](sessionId, "redirectURL", UrlPaths.returnUrl))
          await(
            save[AccountTypes.Value](sessionId, "ACCOUNT_TYPE", accountType)
          )
          val authResponse = authoriseResponseJson()
          stubAuthorizePost(OK, authResponse.toString())
          stubPost(s"/write/.*", OK, """{"x":2}""")

          val res = buildRequest(urlPathSA, followRedirects = false)
            .addCookies(DefaultWSCookie("mdtp", authAndSessionCookie))
            .addHttpHeaders(xSessionId, xRequestId, sessionCookie)
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

    s"the session cache has a credential for SA enrolment that is the signed in account" should {
      s"render the error page" in {
        await(save[String](sessionId, "redirectURL", UrlPaths.returnUrl))
        await(
          save[AccountTypes.Value](
            sessionId,
            "ACCOUNT_TYPE",
            SA_ASSIGNED_TO_OTHER_USER
          )
        )
        await(
          save[UsersAssignedEnrolment](
            sessionId,
            USER_ASSIGNED_SA_ENROLMENT,
            UsersAssignedEnrolment(Some(CREDENTIAL_ID))
          )
        )
        val authResponse = authoriseResponseJson()
        stubAuthorizePost(OK, authResponse.toString())
        stubPost(s"/write/.*", OK, """{"x":2}""")

        val res = buildRequest(urlPathSA, followRedirects = false)
          .addCookies(DefaultWSCookie("mdtp", authAndSessionCookie))
          .addHttpHeaders(xSessionId, xRequestId, sessionCookie)
          .get()

        whenReady(res) { resp =>
          resp.status shouldBe INTERNAL_SERVER_ERROR
          resp.body should include(ErrorTemplateMessages.title)
        }
      }
    }

    s"the session cache has no credentials with SA enrolment" should {
      s"render the error page" in {
        await(save[String](sessionId, "redirectURL", UrlPaths.returnUrl))
        await(
          save[AccountTypes.Value](
            sessionId,
            "ACCOUNT_TYPE",
            SA_ASSIGNED_TO_OTHER_USER
          )
        )
        val authResponse = authoriseResponseJson()
        stubAuthorizePost(OK, authResponse.toString())
        await(
          save[UsersAssignedEnrolment](
            sessionId,
            USER_ASSIGNED_SA_ENROLMENT,
            UsersAssignedEnrolment(None)
          )
        )
        stubPost(s"/write/.*", OK, """{"x":2}""")

        val res = buildRequest(urlPathSA, followRedirects = false)
          .addCookies(DefaultWSCookie("mdtp", authAndSessionCookie))
          .addHttpHeaders(xSessionId, xRequestId, sessionCookie)
          .get()

        whenReady(res) { resp =>
          resp.status shouldBe INTERNAL_SERVER_ERROR
          resp.body should include(ErrorTemplateMessages.title)
        }
      }
    }

    "the session cache has no redirectUrl" should {
      s"return $INTERNAL_SERVER_ERROR" in {
        val authResponse = authoriseResponseJson()
        stubAuthorizePost(OK, authResponse.toString())
        stubPost(s"/write/.*", OK, """{"x":2}""")
        val res = buildRequest(urlPathSA)
          .addCookies(DefaultWSCookie("mdtp", authAndSessionCookie))
          .addHttpHeaders(xSessionId, xRequestId, sessionCookie)
          .get()

        whenReady(res) { resp =>
          resp.status shouldBe INTERNAL_SERVER_ERROR
          resp.body should include(ErrorTemplateMessages.title)
        }
      }
    }

    "users group search returns an error" should {
      "render the error page" in {
        await(save[String](sessionId, "redirectURL", UrlPaths.returnUrl))
        await(
          save[AccountTypes.Value](
            sessionId,
            "ACCOUNT_TYPE",
            SA_ASSIGNED_TO_OTHER_USER
          )
        )
        await(
          save[UsersAssignedEnrolment](
            sessionId,
            USER_ASSIGNED_SA_ENROLMENT,
            UsersAssignedEnrolment(Some(CREDENTIAL_ID_2))
          )
        )
        val authResponse = authoriseResponseJson()
        stubAuthorizePost(OK, authResponse.toString())
        stubPost(s"/write/.*", OK, """{"x":2}""")
        stubGet(
          s"/users-groups-search/users/$CREDENTIAL_ID_2",
          INTERNAL_SERVER_ERROR,
          ""
        )
        val res = buildRequest(urlPathSA, followRedirects = true)
          .addCookies(DefaultWSCookie("mdtp", authAndSessionCookie))
          .addHttpHeaders(xSessionId, xRequestId, sessionCookie)
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
        await(save[String](sessionId, "redirectURL", UrlPaths.returnUrl))
        await(
          save[AccountTypes.Value](
            sessionId,
            "ACCOUNT_TYPE",
            SA_ASSIGNED_TO_OTHER_USER
          )
        )
        stubPost(s"/write/.*", OK, """{"x":2}""")
        stubGetWithQueryParam(
          "/identity-verification/nino",
          "nino",
          NINO,
          Status.NOT_FOUND,
          ""
        )
        val res = buildRequest(urlPathSA)
          .addCookies(DefaultWSCookie("mdtp", authAndSessionCookie))
          .addHttpHeaders(xSessionId, xRequestId, sessionCookie)
          .get()

        whenReady(res) { resp =>
          resp.status shouldBe INTERNAL_SERVER_ERROR
          resp.body should include(ErrorTemplateMessages.title)
        }
      }
    }

    "an authorised user but IV returns internal error" should {
      s"return $INTERNAL_SERVER_ERROR" in {
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
        val res = buildRequest(urlPathSA)
          .addCookies(DefaultWSCookie("mdtp", authAndSessionCookie))
          .addHttpHeaders(xSessionId, xRequestId, sessionCookie)
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
          buildRequest(urlPathSA)
            .addCookies(DefaultWSCookie("mdtp", authAndSessionCookie))
            .addHttpHeaders(xSessionId, xRequestId, csrfContent, sessionCookie)
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
          buildRequest(urlPathSA)
            .addCookies(DefaultWSCookie("mdtp", authAndSessionCookie))
            .addHttpHeaders(xSessionId, xRequestId, csrfContent, sessionCookie)
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
          buildRequest(urlPathSA)
            .addCookies(DefaultWSCookie("mdtp", authAndSessionCookie))
            .addHttpHeaders(xSessionId, xRequestId, csrfContent, sessionCookie)
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

        val res = buildRequest(urlPathSA)
          .addCookies(DefaultWSCookie("mdtp", authAndSessionCookie))
          .addHttpHeaders(xSessionId, xRequestId, csrfContent)
          .get()

        whenReady(res) { resp =>
          resp.status shouldBe SEE_OTHER
          resp.header("Location").get should include("/bas-gateway/sign-in")
        }
      }
    }
  }

  s"GET $urlPathPT" when {

    throttleSpecificTests(() => buildRequest(urlPathPT)
      .addCookies(DefaultWSCookie("mdtp", authAndSessionCookie))
      .addHttpHeaders(xSessionId, xRequestId, sessionCookie)
      .get())

    "the session cache has a credential for PT enrolment that is not the signed in account" should {
      s"render the report suspiciousId page with no continue button" in {
        await(save[String](sessionId, "redirectURL", UrlPaths.returnUrl))
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
          s"/users-groups-search/users/$CREDENTIAL_ID_2",
          NON_AUTHORITATIVE_INFORMATION,
          usergroupsResponseJson().toString()
        )
        val res = buildRequest(urlPathPT, followRedirects = true)
          .addCookies(DefaultWSCookie("mdtp", authAndSessionCookie))
          .addHttpHeaders(xSessionId, xRequestId, sessionCookie)
          .get()

        whenReady(res) { resp =>
          val page = Jsoup.parse(resp.body)

          resp.status shouldBe OK
          page.title should include(ReportSuspiciousIDMessages.title)
          page.getElementsByClass("govuk-button").size() shouldBe 0
          val ptAccountDetails =
            new AccountDetails(usersGroupSearchResponse, CREDENTIAL_ID_2)
          val expectedAuditEvent = AuditEvent.auditReportSuspiciousPTAccount(
            ptAccountDetails
          )(requestWithAccountType(PT_ASSIGNED_TO_OTHER_USER), messagesApi)
          verifyAuditEventSent(expectedAuditEvent)
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
        s"redirect to ${UrlPaths.accountCheckPath}" in {
          await(save[String](sessionId, "redirectURL", UrlPaths.returnUrl))
          await(
            save[AccountTypes.Value](sessionId, "ACCOUNT_TYPE", accountType)
          )
          val authResponse = authoriseResponseJson()
          stubAuthorizePost(OK, authResponse.toString())
          stubPost(s"/write/.*", OK, """{"x":2}""")

          val res = buildRequest(urlPathPT, followRedirects = false)
            .addCookies(DefaultWSCookie("mdtp", authAndSessionCookie))
            .addHttpHeaders(xSessionId, xRequestId, sessionCookie)
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

    s"the session cache has a credential for PT enrolment that is the signed in account" should {
      s"render the error page" in {
        await(save[String](sessionId, "redirectURL", UrlPaths.returnUrl))
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

        val res = buildRequest(urlPathPT, followRedirects = false)
          .addCookies(DefaultWSCookie("mdtp", authAndSessionCookie))
          .addHttpHeaders(xSessionId, xRequestId, sessionCookie)
          .get()

        whenReady(res) { resp =>
          resp.status shouldBe INTERNAL_SERVER_ERROR
          resp.body should include(ErrorTemplateMessages.title)
        }
      }
    }

    s"the session cache has no credentials with PT enrolment" should {
      s"render the error page" in {
        await(save[String](sessionId, "redirectURL", UrlPaths.returnUrl))
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

        val res = buildRequest(urlPathPT, followRedirects = false)
          .addCookies(DefaultWSCookie("mdtp", authAndSessionCookie))
          .addHttpHeaders(xSessionId, xRequestId, sessionCookie)
          .get()

        whenReady(res) { resp =>
          resp.status shouldBe INTERNAL_SERVER_ERROR
          resp.body should include(ErrorTemplateMessages.title)
        }
      }
    }

    "the session cache has no redirectUrl" should {
      s"return $INTERNAL_SERVER_ERROR" in {
        val authResponse = authoriseResponseJson()
        stubAuthorizePost(OK, authResponse.toString())
        stubPost(s"/write/.*", OK, """{"x":2}""")
        val res = buildRequest(urlPathPT)
          .addCookies(DefaultWSCookie("mdtp", authAndSessionCookie))
          .addHttpHeaders(xSessionId, xRequestId, sessionCookie)
          .get()

        whenReady(res) { resp =>
          resp.status shouldBe INTERNAL_SERVER_ERROR
          resp.body should include(ErrorTemplateMessages.title)
        }
      }
    }

    "users group search returns an error" should {
      "render the error page" in {
        await(save[String](sessionId, "redirectURL", UrlPaths.returnUrl))
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
          s"/users-groups-search/users/$CREDENTIAL_ID_2",
          INTERNAL_SERVER_ERROR,
          ""
        )
        val res = buildRequest(urlPathPT, followRedirects = true)
          .addCookies(DefaultWSCookie("mdtp", authAndSessionCookie))
          .addHttpHeaders(xSessionId, xRequestId, sessionCookie)
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
        await(save[String](sessionId, "redirectURL", UrlPaths.returnUrl))
        await(
          save[AccountTypes.Value](
            sessionId,
            "ACCOUNT_TYPE",
            PT_ASSIGNED_TO_OTHER_USER
          )
        )
        stubAuthorizePost(OK, authResponse.toString())
        stubPost(s"/write/.*", OK, """{"x":2}""")
        stubGetWithQueryParam(
          "/identity-verification/nino",
          "nino",
          NINO,
          Status.NOT_FOUND,
          ""
        )
        val res = buildRequest(urlPathPT)
          .addCookies(DefaultWSCookie("mdtp", authAndSessionCookie))
          .addHttpHeaders(xSessionId, xRequestId, sessionCookie)
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
        await(save[String](sessionId, "redirectURL", UrlPaths.returnUrl))
        await(
          save[AccountTypes.Value](
            sessionId,
            "ACCOUNT_TYPE",
            PT_ASSIGNED_TO_OTHER_USER
          )
        )
        stubAuthorizePost(OK, authResponse.toString())
        stubPost(s"/write/.*", OK, """{"x":2}""")
        stubGetWithQueryParam(
          "/identity-verification/nino",
          "nino",
          NINO,
          Status.INTERNAL_SERVER_ERROR,
          ""
        )
        val res = buildRequest(urlPathPT)
          .addCookies(DefaultWSCookie("mdtp", authAndSessionCookie))
          .addHttpHeaders(xSessionId, xRequestId, sessionCookie)
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
          buildRequest(urlPathPT)
            .addCookies(DefaultWSCookie("mdtp", authAndSessionCookie))
            .addHttpHeaders(xSessionId, xRequestId, csrfContent, sessionCookie)
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
          buildRequest(urlPathPT)
            .addCookies(DefaultWSCookie("mdtp", authAndSessionCookie))
            .addHttpHeaders(xSessionId, xRequestId, csrfContent, sessionCookie)
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
          buildRequest(urlPathPT)
            .addCookies(DefaultWSCookie("mdtp", authAndSessionCookie))
            .addHttpHeaders(xSessionId, xRequestId, csrfContent, sessionCookie)
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

        val res = buildRequest(urlPathPT)
          .addCookies(DefaultWSCookie("mdtp", authAndSessionCookie))
          .addHttpHeaders(xSessionId, xRequestId, csrfContent)
          .get()

        whenReady(res) { resp =>
          resp.status shouldBe SEE_OTHER
          resp.header("Location").get should include("/bas-gateway/sign-in")
        }
      }
    }
  }

  s"POST $urlPathSA" when {

    throttleSpecificTests(() => buildRequest(urlPathSA)
      .addCookies(DefaultWSCookie("mdtp", authAndSessionCookie))
      .addHttpHeaders(xSessionId, xRequestId, sessionCookie, csrfContent)
      .post(Json.obj()))

    "the user has account type of SA_ASSIGNED_TO_OTHER_USER" should {
      s"enrol the user for PT and redirect to the EnroledAfterReportingFraud" when {
        "the user hasn't already been assigned a PT enrolment" in {
          val cacheData = Map(
            ACCOUNT_TYPE -> Json.toJson(SA_ASSIGNED_TO_OTHER_USER),
            REDIRECT_URL -> JsString(UrlPaths.returnUrl),
            USER_ASSIGNED_SA_ENROLMENT -> Json.toJson(saUsers),
            accountDetailsForCredential(CREDENTIAL_ID_2) -> Json.toJson(accountDetails)
          )
          await(save(sessionId, cacheData))
          val authResponse = authoriseResponseJson()
          stubAuthorizePost(OK, authResponse.toString())
          stubPost(s"/write/.*", OK, """{"x":2}""")
          stubPut(
            s"/tax-enrolments/service/HMRC-PT/enrolment",
            Status.NO_CONTENT,
            ""
          )

          val res = buildRequest(urlPathSA, followRedirects = false)
            .addCookies(DefaultWSCookie("mdtp", authAndSessionCookie))
            .addHttpHeaders(xSessionId, xRequestId, sessionCookie, csrfContent)
            .post(Json.obj())

          whenReady(res) { resp =>
            resp.status shouldBe SEE_OTHER
            resp.header("Location").get should include(
              "/protect-tax-info/enrol-pt/enrolment-success-no-sa"
            )
            val expectedAuditEvent = AuditEvent.auditSuccessfullyEnrolledPTWhenSAOnOtherAccount(
              true)(requestWithAccountType(SA_ASSIGNED_TO_OTHER_USER, mongoCacheData = cacheData), messagesApi)
            verifyAuditEventSent(expectedAuditEvent)
          }
        }
      }

      s"not enrol the user for PT and redirect to the EnroledAfterReportingFraud" when {
        "the user has already been assigned a PT enrolment" in {
          val cacheData = Map(
            ACCOUNT_TYPE -> Json.toJson(SA_ASSIGNED_TO_OTHER_USER),
            REDIRECT_URL -> JsString(UrlPaths.returnUrl),
            USER_ASSIGNED_SA_ENROLMENT -> Json.toJson(saUsers),
            accountDetailsForCredential(CREDENTIAL_ID_2) -> Json.toJson(accountDetails)
          )
          await(save(sessionId, cacheData))
          val authResponse = authoriseResponseWithPTEnrolment()
          stubAuthorizePost(OK, authResponse.toString())
          stubPost(s"/write/.*", OK, """{"x":2}""")

          val res = buildRequest(urlPathSA, followRedirects = false)
            .addCookies(DefaultWSCookie("mdtp", authAndSessionCookie))
            .addHttpHeaders(xSessionId, xRequestId, sessionCookie, csrfContent)
            .post(Json.obj())

          whenReady(res) { resp =>
            resp.status shouldBe SEE_OTHER
            resp.header("Location").get should include(
              "/enrol-pt/enrolment-success-sa-access-not-wanted"
            )
          }
        }
      }
    }

    "the user has account type of SA_ASSIGNED_TO_OTHER_USER but silent enrolment fails" should {
      s"render the error page" in {
        await(save[String](sessionId, "redirectURL", UrlPaths.returnUrl))
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

        val res = buildRequest(urlPathSA, followRedirects = false)
          .addCookies(DefaultWSCookie("mdtp", authAndSessionCookie))
          .addHttpHeaders(xSessionId, xRequestId, sessionCookie, csrfContent)
          .post(Json.obj())

        whenReady(res) { resp =>
          resp.status shouldBe INTERNAL_SERVER_ERROR
          resp.body should include(ErrorTemplateMessages.title)
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
          await(save[String](sessionId, "redirectURL", UrlPaths.returnUrl))
          await(
            save[AccountTypes.Value](sessionId, "ACCOUNT_TYPE", accountType)
          )
          val authResponse = authoriseResponseJson()
          stubAuthorizePost(OK, authResponse.toString())
          stubPost(s"/write/.*", OK, """{"x":2}""")

          val res = buildRequest(urlPathSA, followRedirects = false)
            .addCookies(DefaultWSCookie("mdtp", authAndSessionCookie))
            .addHttpHeaders(xSessionId, xRequestId, sessionCookie, csrfContent)
            .post(Json.obj())

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
      s"return $INTERNAL_SERVER_ERROR" in {
        val authResponse = authoriseResponseJson()
        stubAuthorizePost(OK, authResponse.toString())
        stubPost(s"/write/.*", OK, """{"x":2}""")
        val res = buildRequest(urlPathSA)
          .addCookies(DefaultWSCookie("mdtp", authAndSessionCookie))
          .addHttpHeaders(xSessionId, xRequestId, sessionCookie, csrfContent)
          .post(Json.obj())

        whenReady(res) { resp =>
          resp.status shouldBe INTERNAL_SERVER_ERROR
          resp.body should include(ErrorTemplateMessages.title)
        }
      }
    }
  }
}
