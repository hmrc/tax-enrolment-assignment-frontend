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
import play.api.test.Helpers.{GET, POST, await, contentAsString, defaultAwaitTimeout, redirectLocation}
import play.api.test.Helpers.{route, status, writeableOf_AnyContentAsEmpty, writeableOf_AnyContentAsJson}
import play.api.http.Status.{SEE_OTHER, OK, INTERNAL_SERVER_ERROR, NON_AUTHORITATIVE_INFORMATION}
import helpers.messages._
import org.jsoup.Jsoup
import org.mongodb.scala.bson.BsonDocument
import play.api.http.Status
import play.api.libs.json.{JsString, Json}
import play.api.libs.ws.DefaultWSCookie
import play.api.mvc.Cookie
import play.api.test.FakeRequest
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.{AccountDetails, UsersAssignedEnrolment}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.SessionKeys._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.reporting.AuditEvent

class ReportSuspiciousIDControllerISpec extends IntegrationSpecBase with ThrottleHelperISpec {

  val urlPathSa: String = ItUrlPaths.reportFraudSAAccountPath
  val urlPathPT: String = ItUrlPaths.reportFraudPTAccountPath

  s"GET $urlPathSa" should {

    throttleSpecificTests { () =>
      val request = FakeRequest(GET, "/protect-tax-info" + urlPathSa)
        .withSession(xAuthToken, xSessionId)
      route(app, request).get
    }

    "the session cache has a credential for SA enrolment that is not the signed in account" when {
      s"render the report suspiciousId page with a continue button" in {
        await(save[String](sessionId, "redirectURL", returnUrl))
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

        val request = FakeRequest(GET, "/protect-tax-info" + urlPathSa)
          .withSession(xAuthToken, xSessionId)
        val result = route(app, request).get
        val page = Jsoup.parse(contentAsString(result))

          status(result) shouldBe OK
          page.title should include(ReportSuspiciousIDMessages.title)
          page.getElementsByClass("govuk-button").size() shouldBe 1

          val expectedAuditEvent = AuditEvent.auditReportSuspiciousSAAccount(
            accountDetailsUserFriendly(CREDENTIAL_ID_2)
          )(requestWithAccountType(SA_ASSIGNED_TO_OTHER_USER), messagesApi)

          verifyAuditEventSent(expectedAuditEvent)
      }
    }

    "the session cache has a credential for SA enrolment that is not the signed in account" when {
      s"still render the report suspiciousId page with a continue button" when {
        "the current user has been assigned a PT enrolment" in {
          await(save[String](sessionId, "redirectURL", returnUrl))
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

          val request = FakeRequest(GET, "/protect-tax-info" + urlPathSa)
            .withSession(xAuthToken, xSessionId)
          val result = route(app, request).get
          val page = Jsoup.parse(contentAsString(result))

          status(result) shouldBe OK
            page.title should include(ReportSuspiciousIDMessages.title)
            page.getElementsByClass("govuk-button").size() shouldBe 1
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
      s"the session cache has a credential with account type ${accountType.toString}" when {
        s"redirect to /protect-tax-info" in {
          await(save[String](sessionId, "redirectURL", returnUrl))
          await(
            save[AccountTypes.Value](sessionId, "ACCOUNT_TYPE", accountType)
          )
          val authResponse = authoriseResponseJson()
          stubAuthorizePost(OK, authResponse.toString())
          stubPost(s"/write/.*", OK, """{"x":2}""")

          val request = FakeRequest(GET, "/protect-tax-info" + urlPathSa)
            .withSession(xAuthToken, xSessionId)
          val result = route(app, request).get
          val page = Jsoup.parse(contentAsString(result))

          status(result) shouldBe SEE_OTHER
            redirectLocation(result).get should include(
              accountCheckPath
            )
        }
      }
    }

    s"the session cache has a credential for SA enrolment that is the signed in account" when {
      s"render the error page" in {
        await(save[String](sessionId, "redirectURL", returnUrl))
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

        val request = FakeRequest(GET, "/protect-tax-info" + urlPathSa)
          .withSession(xAuthToken, xSessionId)
        val result = route(app, request).get
        val page = Jsoup.parse(contentAsString(result))

        status(result) shouldBe INTERNAL_SERVER_ERROR
          contentAsString(result) should include(ErrorTemplateMessages.title)

      }
    }

    s"the session cache has no credentials with SA enrolment" when {
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
        await(
          save[UsersAssignedEnrolment](
            sessionId,
            USER_ASSIGNED_SA_ENROLMENT,
            UsersAssignedEnrolment(None)
          )
        )
        stubPost(s"/write/.*", OK, """{"x":2}""")

        val request = FakeRequest(GET, "/protect-tax-info" + urlPathSa)
          .withSession(xAuthToken, xSessionId)
        val result = route(app, request).get
        val page = Jsoup.parse(contentAsString(result))

        status(result) shouldBe INTERNAL_SERVER_ERROR
          contentAsString(result) should include(ErrorTemplateMessages.title)

      }
    }

    "the session cache is empty" when {
      s"redirect to login" in {
        val authResponse = authoriseResponseJson()
        stubAuthorizePost(OK, authResponse.toString())
        stubPost(s"/write/.*", OK, """{"x":2}""")

        val request = FakeRequest(GET, "/protect-tax-info" + urlPathSa)
          .withSession(xAuthToken, xSessionId)
        val result = route(app, request).get
        val page = Jsoup.parse(contentAsString(result))

        status(result) shouldBe SEE_OTHER
          redirectLocation(result).get should include("/bas-gateway/sign-in")

      }
    }

    "users group search returns an error" when {
      "render the error page" in {
        await(save[String](sessionId, "redirectURL", returnUrl))
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

        val request = FakeRequest(GET, "/protect-tax-info" + urlPathSa)
          .withSession(xAuthToken, xSessionId)
        val result = route(app, request).get
        val page = Jsoup.parse(contentAsString(result))

        status(result) shouldBe INTERNAL_SERVER_ERROR
          contentAsString(result) should include(ErrorTemplateMessages.title)

      }
    }

    "an authorised user with no credential uses the service" when {
      s"render the error page" in {
        val authResponse = authoriseResponseJson()
        stubAuthorizePost(OK, authResponse.toString())
        await(save[String](sessionId, "redirectURL", returnUrl))
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

        val request = FakeRequest(GET, "/protect-tax-info" + urlPathSa)
          .withSession(xAuthToken, xSessionId)
        val result = route(app, request).get
        val page = Jsoup.parse(contentAsString(result))

        status(result) shouldBe INTERNAL_SERVER_ERROR
          contentAsString(result) should include(ErrorTemplateMessages.title)

      }
    }

    "an authorised user but IV returns internal error" when {
      s"return $INTERNAL_SERVER_ERROR" in {
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
        stubGetWithQueryParam(
          "/identity-verification/nino",
          "nino",
          NINO,
          Status.INTERNAL_SERVER_ERROR,
          ""
        )

        val request = FakeRequest(GET, "/protect-tax-info" + urlPathSa)
          .withSession(xAuthToken, xSessionId)
        val result = route(app, request).get
        val page = Jsoup.parse(contentAsString(result))

        status(result) shouldBe INTERNAL_SERVER_ERROR
          contentAsString(result) should include(ErrorTemplateMessages.title)

      }
    }

    "the user has a session missing required element NINO" when {
      s"redirect to ${ItUrlPaths.unauthorizedPath}" in {
        val authResponse = authoriseResponseJson(optNino = None)
        stubAuthorizePost(OK, authResponse.toString())
        stubPost(s"/write/.*", OK, """{"x":2}""")

        val request = FakeRequest(GET, "/protect-tax-info" + urlPathSa)
          .withSession(xAuthToken, xSessionId)
        val result = route(app, request).get
        val page = Jsoup.parse(contentAsString(result))

        status(result) shouldBe SEE_OTHER
          redirectLocation(result).get should include(ItUrlPaths.unauthorizedPath)

      }
    }

    "the user has a session missing required element Credentials" when {
      s"redirect to ${ItUrlPaths.unauthorizedPath}" in {
        val authResponse = authoriseResponseJson(optCreds = None)
        stubAuthorizePost(OK, authResponse.toString())
        stubPost(s"/write/.*", OK, """{"x":2}""")

        val request = FakeRequest(GET, "/protect-tax-info" + urlPathSa)
          .withSession(xAuthToken, xSessionId)
        val result = route(app, request).get
        val page = Jsoup.parse(contentAsString(result))

        status(result) shouldBe SEE_OTHER
          redirectLocation(result).get should include(ItUrlPaths.unauthorizedPath)

      }
    }

    "the user has a insufficient confidence level" when {
      s"redirect to ${ItUrlPaths.unauthorizedPath}" in {
        stubAuthorizePostUnauthorised(insufficientConfidenceLevel)
        stubPost(s"/write/.*", OK, """{"x":2}""")

        val request = FakeRequest(GET, "/protect-tax-info" + urlPathSa)
          .withSession(xAuthToken, xSessionId)
        val result = route(app, request).get
        val page = Jsoup.parse(contentAsString(result))

        status(result) shouldBe SEE_OTHER
          redirectLocation(result).get should include(ItUrlPaths.unauthorizedPath)

      }
    }

    "the user has no active session" when {
      s"redirect to login" in {
        stubAuthorizePostUnauthorised(sessionNotFound)
        stubPost(s"/write/.*", OK, """{"x":2}""")

        val request = FakeRequest(GET, "/protect-tax-info" + urlPathSa)
          .withSession(xAuthToken, xSessionId)
        val result = route(app, request).get
        val page = Jsoup.parse(contentAsString(result))

        status(result) shouldBe SEE_OTHER
        redirectLocation(result).get should include("/bas-gateway/sign-in")

      }
    }
  }

  s"GET $urlPathPT" when {

    throttleSpecificTests { () =>
      val request = FakeRequest(GET, "/protect-tax-info" + urlPathPT)
        .withSession(xAuthToken, xSessionId)
      route(app, request).get
    }

    "the session cache has a credential for PT enrolment that is not the signed in account" when {
      s"render the report suspiciousId page with no continue button" in {
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
          s"/users-groups-search/users/$CREDENTIAL_ID_2",
          NON_AUTHORITATIVE_INFORMATION,
          usergroupsResponseJson().toString()
        )

        val request = FakeRequest(GET, "/protect-tax-info" + urlPathPT)
          .withSession(xAuthToken, xSessionId)
        val result = route(app, request).get
        val page = Jsoup.parse(contentAsString(result))

        status(result) shouldBe OK
          page.title should include(ReportSuspiciousIDMessages.title)
          page.getElementsByClass("govuk-button").size() shouldBe 0

          val expectedAuditEvent = AuditEvent.auditReportSuspiciousPTAccount(
            accountDetailsUserFriendly(CREDENTIAL_ID_2)
          )(requestWithAccountType(PT_ASSIGNED_TO_OTHER_USER), messagesApi)
          verifyAuditEventSent(expectedAuditEvent)

      }
    }

    List(
      SINGLE_ACCOUNT,
      PT_ASSIGNED_TO_CURRENT_USER,
      MULTIPLE_ACCOUNTS,
      SA_ASSIGNED_TO_OTHER_USER,
      SA_ASSIGNED_TO_CURRENT_USER
    ).foreach { accountType =>
      s"the session cache has a credential with account type ${accountType.toString}" when {
        s"redirect to /protect-tax-info" in {
          await(save[String](sessionId, "redirectURL", returnUrl))
          await(
            save[AccountTypes.Value](sessionId, "ACCOUNT_TYPE", accountType)
          )
          val authResponse = authoriseResponseJson()
          stubAuthorizePost(OK, authResponse.toString())
          stubPost(s"/write/.*", OK, """{"x":2}""")

          val request = FakeRequest(GET, "/protect-tax-info" + urlPathPT)
            .withSession(xAuthToken, xSessionId)
          val result = route(app, request).get
          val page = Jsoup.parse(contentAsString(result))

          status(result) shouldBe SEE_OTHER
            redirectLocation(result).get should include(
              accountCheckPath
            )

        }
      }
    }

    s"the session cache has a credential for PT enrolment that is the signed in account" when {
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

        val request = FakeRequest(GET, "/protect-tax-info" + urlPathPT)
          .withSession(xAuthToken, xSessionId)
        val result = route(app, request).get
        val page = Jsoup.parse(contentAsString(result))

        status(result) shouldBe INTERNAL_SERVER_ERROR
          contentAsString(result) should include(ErrorTemplateMessages.title)

      }
    }

    s"the session cache has no credentials with PT enrolment" when {
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

        val request = FakeRequest(GET, "/protect-tax-info" + urlPathPT)
          .withSession(xAuthToken, xSessionId)
        val result = route(app, request).get
        val page = Jsoup.parse(contentAsString(result))

        status(result) shouldBe INTERNAL_SERVER_ERROR
          contentAsString(result) should include(ErrorTemplateMessages.title)

      }
    }

    "the session cache has no redirectUrl" when {
      s"return $INTERNAL_SERVER_ERROR" in {
        await(
          save[AccountTypes.Value](
            sessionId,
            "ACCOUNT_TYPE",
            PT_ASSIGNED_TO_OTHER_USER
          )
        )
        val authResponse = authoriseResponseJson()
        stubAuthorizePost(OK, authResponse.toString())
        stubPost(s"/write/.*", OK, """{"x":2}""")

        val request = FakeRequest(GET, "/protect-tax-info" + urlPathPT)
          .withSession(xAuthToken, xSessionId)
        val result = route(app, request).get
        val page = Jsoup.parse(contentAsString(result))

        status(result) shouldBe INTERNAL_SERVER_ERROR
          contentAsString(result) should include(ErrorTemplateMessages.title)

      }
    }

    "users group search returns an error" when {
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
          s"/users-groups-search/users/$CREDENTIAL_ID_2",
          INTERNAL_SERVER_ERROR,
          ""
        )

        val request = FakeRequest(GET, "/protect-tax-info" + urlPathPT)
          .withSession(xAuthToken, xSessionId)
        val result = route(app, request).get
        val page = Jsoup.parse(contentAsString(result))

        status(result) shouldBe INTERNAL_SERVER_ERROR
          contentAsString(result) should include(ErrorTemplateMessages.title)

      }
    }

    "an authorised user with no credential uses the service" when {
      s"render the error page" in {
        val authResponse = authoriseResponseJson()
        await(save[String](sessionId, "redirectURL", returnUrl))
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

        val request = FakeRequest(GET, "/protect-tax-info" + urlPathPT)
          .withSession(xAuthToken, xSessionId)
        val result = route(app, request).get
        val page = Jsoup.parse(contentAsString(result))

        status(result) shouldBe INTERNAL_SERVER_ERROR
          contentAsString(result) should include(ErrorTemplateMessages.title)
      }
    }

    "an authorised user but IV returns internal error" when {
      s"render the error page" in {
        val authResponse = authoriseResponseJson()
        await(save[String](sessionId, "redirectURL", returnUrl))
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
        val request = FakeRequest(GET, "/protect-tax-info" + urlPathPT)
          .withSession(xAuthToken, xSessionId)
        val result = route(app, request).get
        val page = Jsoup.parse(contentAsString(result))

        status(result) shouldBe INTERNAL_SERVER_ERROR
        contentAsString(result) should include(ErrorTemplateMessages.title)

      }
    }

    "the user has a session missing required element NINO" when {
      s"redirect to ${ItUrlPaths.unauthorizedPath}" in {
        val authResponse = authoriseResponseJson(optNino = None)
        stubAuthorizePost(OK, authResponse.toString())
        stubPost(s"/write/.*", OK, """{"x":2}""")

        val request = FakeRequest(GET, "/protect-tax-info" + urlPathPT)
          .withSession(xAuthToken, xSessionId)
        val result = route(app, request).get
        val page = Jsoup.parse(contentAsString(result))

        status(result) shouldBe SEE_OTHER

          redirectLocation(result).get should include(ItUrlPaths.unauthorizedPath)

      }
    }

    "the user has a session missing required element Credentials" when {
      s"redirect to ${ItUrlPaths.unauthorizedPath}" in {
        val authResponse = authoriseResponseJson(optCreds = None)
        stubAuthorizePost(OK, authResponse.toString())
        stubPost(s"/write/.*", OK, """{"x":2}""")

        val request = FakeRequest(GET, "/protect-tax-info" + urlPathPT)
          .withSession(xAuthToken, xSessionId)
        val result = route(app, request).get
        val page = Jsoup.parse(contentAsString(result))

        status(result) shouldBe SEE_OTHER
          redirectLocation(result).get should include(ItUrlPaths.unauthorizedPath)

      }
    }

    "the user has a insufficient confidence level" when {
      s"redirect to ${ItUrlPaths.unauthorizedPath}" in {
        stubAuthorizePostUnauthorised(insufficientConfidenceLevel)
        stubPost(s"/write/.*", OK, """{"x":2}""")

        val request = FakeRequest(GET, "/protect-tax-info" + urlPathPT)
          .withSession(xAuthToken, xSessionId)
        val result = route(app, request).get
        val page = Jsoup.parse(contentAsString(result))

        status(result) shouldBe SEE_OTHER
          redirectLocation(result).get should include(ItUrlPaths.unauthorizedPath)

      }
    }

    "the user has no active session" when {
      s"redirect to login" in {
        stubAuthorizePostUnauthorised(sessionNotFound)
        stubPost(s"/write/.*", OK, """{"x":2}""")

        val request = FakeRequest(GET, "/protect-tax-info" + urlPathPT)
          .withSession(xAuthToken, xSessionId)
        val result = route(app, request).get
        val page = Jsoup.parse(contentAsString(result))

        status(result) shouldBe SEE_OTHER
          redirectLocation(result).get should include("/bas-gateway/sign-in")

      }
    }
  }

  s"POST $urlPathSa" when {

    throttleSpecificTests { () =>
      val request = FakeRequest(POST, "/protect-tax-info" + urlPathSa)
        .withSession(xAuthToken, xSessionId)
        .withJsonBody(Json.obj())
      route(app, request).get
    }

    "the user has account type of SA_ASSIGNED_TO_OTHER_USER" when {
      s"enrol the user for PT and redirect to the EnroledAfterReportingFraud" when {
        "the user hasn't already been assigned a PT enrolment" in {
          val cacheData = Map(
            ACCOUNT_TYPE -> Json.toJson(SA_ASSIGNED_TO_OTHER_USER),
            REDIRECT_URL -> JsString(returnUrl),
            USER_ASSIGNED_SA_ENROLMENT -> Json.toJson(saUsers),
            accountDetailsForCredential(CREDENTIAL_ID_2) -> Json.toJson(accountDetails) (AccountDetails.mongoFormats(crypto.crypto))
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

          val request = FakeRequest(POST, "/protect-tax-info" + urlPathSa)
            .withSession(xAuthToken, xSessionId)
            .withJsonBody(Json.obj())
          val result = route(app, request).get
          val page = Jsoup.parse(contentAsString(result))

          status(result) shouldBe SEE_OTHER
            redirectLocation(result).get should include("/protect-tax-info/enrol-pt/enrolment-success-no-sa")
            val expectedAuditEvent = AuditEvent.auditSuccessfullyEnrolledPTWhenSAOnOtherAccount(
              true)(requestWithAccountType(SA_ASSIGNED_TO_OTHER_USER, mongoCacheData = cacheData), messagesApi)
            verifyAuditEventSent(expectedAuditEvent)

        }
      }

      s"not enrol the user for PT and redirect to the EnroledAfterReportingFraud" when {
        "the user has already been assigned a PT enrolment" in {
          val cacheData = Map(
            ACCOUNT_TYPE -> Json.toJson(SA_ASSIGNED_TO_OTHER_USER),
            REDIRECT_URL -> JsString(returnUrl),
            USER_ASSIGNED_SA_ENROLMENT -> Json.toJson(saUsers),
            accountDetailsForCredential(CREDENTIAL_ID_2) -> Json.toJson(accountDetails)
          )
          await(save(sessionId, cacheData))
          val authResponse = authoriseResponseWithPTEnrolment()
          stubAuthorizePost(OK, authResponse.toString())
          stubPost(s"/write/.*", OK, """{"x":2}""")

          val request = FakeRequest(POST, "/protect-tax-info" + urlPathSa)
            .withSession(xAuthToken, xSessionId)
            .withJsonBody(Json.obj())
          val result = route(app, request).get
          val page = Jsoup.parse(contentAsString(result))

          status(result) shouldBe SEE_OTHER
            redirectLocation(result).get should include(
              "/enrol-pt/enrolment-success-sa-access-not-wanted"
            )

        }
      }
    }

    "the user has account type of SA_ASSIGNED_TO_OTHER_USER but silent enrolment fails" when {
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

        val request = FakeRequest(POST, "/protect-tax-info" + urlPathSa)
          .withSession(xAuthToken, xSessionId)
          .withJsonBody(Json.obj())
        val result = route(app, request).get
        val page = Jsoup.parse(contentAsString(result))

        status(result) shouldBe INTERNAL_SERVER_ERROR
          contentAsString(result) should include(ErrorTemplateMessages.title)

      }
    }

    List(
      SINGLE_ACCOUNT,
      PT_ASSIGNED_TO_OTHER_USER,
      PT_ASSIGNED_TO_CURRENT_USER,
      MULTIPLE_ACCOUNTS,
      SA_ASSIGNED_TO_CURRENT_USER
    ).foreach { accountType =>
      s"the session cache has Account type of $accountType" when {
        s"redirect to accountCheck" in {
          await(save[String](sessionId, "redirectURL", returnUrl))
          await(
            save[AccountTypes.Value](sessionId, "ACCOUNT_TYPE", accountType)
          )
          val authResponse = authoriseResponseJson()
          stubAuthorizePost(OK, authResponse.toString())
          stubPost(s"/write/.*", OK, """{"x":2}""")

          val request = FakeRequest(POST, "/protect-tax-info" + urlPathSa)
            .withSession(xAuthToken, xSessionId)
            .withJsonBody(Json.obj())
          val result = route(app, request).get
          val page = Jsoup.parse(contentAsString(result))

          status(result) shouldBe SEE_OTHER
           redirectLocation(result).get should include(
              accountCheckPath
            )

        }
      }
    }

    "the session cache is empty" when {
      s"redirect to login" in {
        await(sessionRepository.collection.deleteMany(BsonDocument()).toFuture())
        val authResponse = authoriseResponseJson()
        stubAuthorizePost(OK, authResponse.toString())
        stubPost(s"/write/.*", OK, """{"x":2}""")

        val request = FakeRequest(POST, "/protect-tax-info" + urlPathSa)
          .withSession(xAuthToken, xSessionId)
          .withJsonBody(Json.obj())
        val result = route(app, request).get
        val page = Jsoup.parse(contentAsString(result))

        status(result) shouldBe SEE_OTHER
          redirectLocation(result).get should include("/bas-gateway/sign-in")

      }
    }
  }
}
