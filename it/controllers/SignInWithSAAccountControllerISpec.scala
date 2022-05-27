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
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.UsersAssignedEnrolment
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.SessionKeys.{ACCOUNT_TYPE, REDIRECT_URL, USER_ASSIGNED_SA_ENROLMENT, accountDetailsForCredential}
import play.api.libs.ws.DefaultWSCookie
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.routes.AccountCheckController
import uk.gov.hmrc.taxenrolmentassignmentfrontend.reporting.AuditEvent

class SignInWithSAAccountControllerISpec extends TestHelper with Status with ThrottleHelperISpec {

  val urlPath: String = UrlPaths.saOnOtherAccountSigninAgainPath

  s"GET $urlPath" when {

    throttleSpecificTests(() => buildRequest(urlPath)
      .addCookies(DefaultWSCookie("mdtp", authAndSessionCookie))
      .addHttpHeaders(xSessionId, csrfContent, sessionCookie)
      .get())

    "the session cache has a credential for SA enrolment that is not the signed in account" should {
      s"return $OK with the sign in again page" in {
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
        val res = buildRequest(urlPath, followRedirects = true)
          .addCookies(DefaultWSCookie("mdtp", authAndSessionCookie))
          .addHttpHeaders(xSessionId, csrfContent, sessionCookie)
          .get()

        whenReady(res) { resp =>
          val page = Jsoup.parse(resp.body)

          resp.status shouldBe OK
          page.title should include(SignInAgainMessages.title)
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

            val res = buildRequest(urlPath, followRedirects = false)
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

          val res = buildRequest(urlPath, followRedirects = false)
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

          val res = buildRequest(urlPath, followRedirects = false)
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
          val res = buildRequest(urlPath)
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
          val res = buildRequest(urlPath, followRedirects = true)
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
        s"return $SEE_OTHER" in {
          val authResponse = authoriseResponseJson(optNino = None)
          stubAuthorizePost(OK, authResponse.toString())
          stubPost(s"/write/.*", OK, """{"x":2}""")

          val res =
            buildRequest(urlPath)
              .addCookies(DefaultWSCookie("mdtp", authAndSessionCookie))
              .addHttpHeaders(xSessionId, xRequestId, csrfContent, sessionCookie)
              .get()

          whenReady(res) { resp =>
            resp.status shouldBe SEE_OTHER
            resp.header("Location").get should include(
              UrlPaths.unauthorizedPath
            )
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
              .addCookies(DefaultWSCookie("mdtp", authAndSessionCookie))
              .addHttpHeaders(xSessionId, xRequestId, csrfContent, sessionCookie)
              .get()

          whenReady(res) { resp =>
            resp.status shouldBe SEE_OTHER
            resp.header("Location").get should include(
              UrlPaths.unauthorizedPath
            )
          }
        }
      }

      "the user has a insufficient confidence level" should {
        s"return $SEE_OTHER" in {
          stubAuthorizePostUnauthorised(insufficientConfidenceLevel)
          stubPost(s"/write/.*", OK, """{"x":2}""")

          val res =
            buildRequest(urlPath)
              .addCookies(DefaultWSCookie("mdtp", authAndSessionCookie))
              .addHttpHeaders(xSessionId, xRequestId, csrfContent, sessionCookie)
              .get()

          whenReady(res) { resp =>
            resp.status shouldBe SEE_OTHER
            resp.header("Location").get should include(
              UrlPaths.unauthorizedPath
            )
          }
        }
      }

      "the user has no active session" should {
        s"redirect to login" in {
          stubAuthorizePostUnauthorised(sessionNotFound)
          stubPost(s"/write/.*", OK, """{"x":2}""")

          val res = buildRequest(urlPath)
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
  }

  s"POST $urlPath" when {

    throttleSpecificTests(() => buildRequest(urlPath)
      .addCookies(DefaultWSCookie("mdtp", authAndSessionCookie))
      .addHttpHeaders(xSessionId, xRequestId, sessionCookie, csrfContent)
      .post(Json.obj()))

    "the session cache has a credential for SA enrolment that is not the signed in account" should {
      s"redirect to ${UrlPaths.logoutPath}" in {
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
        val res = buildRequest(urlPath)
          .addCookies(DefaultWSCookie("mdtp", authAndSessionCookie))
          .addHttpHeaders(xSessionId, xRequestId, sessionCookie, csrfContent)
          .post(Json.obj())

        whenReady(res) { resp =>
          resp.status shouldBe SEE_OTHER
          resp.header("Location").get should include(
            UrlPaths.logoutPath
          )
          val expectedAuditEvent = AuditEvent.auditSigninAgainWithSACredential()(requestWithAccountType(SA_ASSIGNED_TO_OTHER_USER, mongoCacheData = cacheData))
          verifyAuditEventSent(expectedAuditEvent)
        }
      }
    }

    "the session cache has no redirectUrl" should {
      s"return $INTERNAL_SERVER_ERROR" in {
        val authResponse = authoriseResponseJson()
        stubAuthorizePost(OK, authResponse.toString())
        stubPost(s"/write/.*", OK, """{"x":2}""")
        val res = buildRequest(urlPath)
          .addCookies(DefaultWSCookie("mdtp", authAndSessionCookie))
          .addHttpHeaders(xSessionId, xRequestId, sessionCookie, csrfContent)
          .post(Json.obj())

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
            .addCookies(DefaultWSCookie("mdtp", authAndSessionCookie))
            .addHttpHeaders(xSessionId, xRequestId, sessionCookie, csrfContent)
            .post(Json.obj())

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
            .addCookies(DefaultWSCookie("mdtp", authAndSessionCookie))
            .addHttpHeaders(xSessionId, xRequestId, sessionCookie, csrfContent)
            .post(Json.obj())

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
            .addCookies(DefaultWSCookie("mdtp", authAndSessionCookie))
            .addHttpHeaders(xSessionId, xRequestId, sessionCookie, csrfContent)
            .post(Json.obj())

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
          .addCookies(DefaultWSCookie("mdtp", authAndSessionCookie))
          .addHttpHeaders(xSessionId, xRequestId, sessionCookie, csrfContent)
          .post(Json.obj())

        whenReady(res) { resp =>
          resp.status shouldBe SEE_OTHER
          resp.header("Location").get should include("/bas-gateway/sign-in")
        }
      }
    }
  }
}
