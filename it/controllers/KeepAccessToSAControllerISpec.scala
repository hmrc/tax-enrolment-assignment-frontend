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

import helpers.TestITData._
import play.api.test.Helpers.{GET, POST, await, contentAsString, defaultAwaitTimeout, redirectLocation, route}
import play.api.test.Helpers.{status, writeableOf_AnyContentAsEmpty}
import helpers.messages._
import helpers.{IntegrationSpecBase, ItUrlPaths}
import org.jsoup.Jsoup
import play.api.http.Status
import play.api.libs.json.{JsString, Json}
import play.api.test.FakeRequest
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes.{MULTIPLE_ACCOUNTS, PT_ASSIGNED_TO_CURRENT_USER, PT_ASSIGNED_TO_OTHER_USER, SA_ASSIGNED_TO_CURRENT_USER, SA_ASSIGNED_TO_OTHER_USER, SINGLE_ACCOUNT}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.forms.KeepAccessToSAThroughPTA
import uk.gov.hmrc.taxenrolmentassignmentfrontend.reporting.AuditEvent
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.SessionKeys._

class KeepAccessToSAControllerISpec extends IntegrationSpecBase with Status {

  val urlPath: String = ItUrlPaths.saOnOtherAccountKeepAccessToSAPath

  s"GET $urlPath" when {
    s"the session cache contains Account type of $SA_ASSIGNED_TO_OTHER_USER and no page data" should {
      s"render the KeepAccessToSA page with radio buttons unchecked" in {
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

        val request = FakeRequest(GET, "/protect-tax-info" + urlPath)
          .withSession(xAuthToken, xSessionId)
        val result = route(app, request).get
        val page = Jsoup.parse(contentAsString(result))

        status(result) shouldBe OK
        page.title should include(KeepAccessToSAMessages.title)
        val radioInputs = page.getElementsByClass("govuk-radios__input")
        radioInputs.size() shouldBe 2
        radioInputs.get(0).attr("value") shouldBe "yes"
        radioInputs.get(0).hasAttr("checked") shouldBe false
        radioInputs.get(1).attr("value") shouldBe "no"
        radioInputs.get(1).hasAttr("checked") shouldBe false
      }
    }

    s"the session cache contains Account type of $SA_ASSIGNED_TO_OTHER_USER and user has previously selected yes" should {
      s"render the KeepAccessToSA page with radio buttons unchecked" in {
        await(save[String](sessionId, "redirectURL", returnUrl))
        await(
          save[AccountTypes.Value](
            sessionId,
            "ACCOUNT_TYPE",
            SA_ASSIGNED_TO_OTHER_USER
          )
        )
        await(
          save[KeepAccessToSAThroughPTA](
            sessionId,
            KEEP_ACCESS_TO_SA_THROUGH_PTA_FORM,
            KeepAccessToSAThroughPTA(true)
          )
        )
        val authResponse = authoriseResponseJson()
        stubAuthorizePost(OK, authResponse.toString())
        stubPost(s"/write/.*", OK, """{"x":2}""")

        val request = FakeRequest(GET, "/protect-tax-info" + urlPath)
          .withSession(xAuthToken, xSessionId)
        val result = route(app, request).get
        val page = Jsoup.parse(contentAsString(result))

        status(result) shouldBe OK
        page.title should include(KeepAccessToSAMessages.title)
        val radioInputs = page.getElementsByClass("govuk-radios__input")
        radioInputs.size() shouldBe 2
        radioInputs.get(0).attr("value") shouldBe "yes"
        radioInputs.get(0).hasAttr("checked") shouldBe true
        radioInputs.get(1).attr("value") shouldBe "no"
        radioInputs.get(1).hasAttr("checked") shouldBe false
      }
    }

    s"the session cache contains Account type of $SA_ASSIGNED_TO_OTHER_USER and user has previously selected no" should {
      s"render the KeepAccessToSA page with radio buttons unchecked" in {
        await(save[String](sessionId, "redirectURL", returnUrl))
        await(
          save[AccountTypes.Value](
            sessionId,
            "ACCOUNT_TYPE",
            SA_ASSIGNED_TO_OTHER_USER
          )
        )
        await(
          save[KeepAccessToSAThroughPTA](
            sessionId,
            KEEP_ACCESS_TO_SA_THROUGH_PTA_FORM,
            KeepAccessToSAThroughPTA(false)
          )
        )
        val authResponse = authoriseResponseJson()
        stubAuthorizePost(OK, authResponse.toString())
        stubPost(s"/write/.*", OK, """{"x":2}""")

        val request = FakeRequest(GET, "/protect-tax-info" + urlPath)
          .withSession(xAuthToken, xSessionId)
        val result = route(app, request).get
        val page = Jsoup.parse(contentAsString(result))

        status(result) shouldBe OK
        page.title should include(KeepAccessToSAMessages.title)
        val radioInputs = page.getElementsByClass("govuk-radios__input")
        radioInputs.size() shouldBe 2
        radioInputs.get(0).attr("value") shouldBe "yes"
        radioInputs.get(0).hasAttr("checked") shouldBe false
        radioInputs.get(1).attr("value") shouldBe "no"
        radioInputs.get(1).hasAttr("checked") shouldBe true

      }
    }

    s"the session cache contains Account type of $SA_ASSIGNED_TO_OTHER_USER and auth contains a ptEnrolment" should {
      s"redirect to ${ItUrlPaths.enrolledPTSAOnOtherAccountPath}" in {
        await(save[String](sessionId, "redirectURL", returnUrl))
        await(
          save[AccountTypes.Value](
            sessionId,
            "ACCOUNT_TYPE",
            SA_ASSIGNED_TO_OTHER_USER
          )
        )
        await(
          save[KeepAccessToSAThroughPTA](
            sessionId,
            KEEP_ACCESS_TO_SA_THROUGH_PTA_FORM,
            KeepAccessToSAThroughPTA(false)
          )
        )
        val authResponse = authoriseResponseWithPTEnrolment()
        stubAuthorizePost(OK, authResponse.toString())
        stubPost(s"/write/.*", OK, """{"x":2}""")
        stubGet(
          s"/users-groups-search/users/$CREDENTIAL_ID",
          NON_AUTHORITATIVE_INFORMATION,
          usergroupsResponseJson().toString()
        )

        val request = FakeRequest(GET, "/protect-tax-info" + urlPath)
          .withSession(xAuthToken, xSessionId)
        val result = route(app, request).get

        status(result) shouldBe SEE_OTHER
        redirectLocation(result).get should include(ItUrlPaths.enrolledPTSAOnOtherAccountPath)

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
        s"redirect to /protect-tax-info" in {
          await(save[String](sessionId, "redirectURL", returnUrl))
          await(
            save[AccountTypes.Value](sessionId, "ACCOUNT_TYPE", accountType)
          )
          val authResponse = authoriseResponseJson()
          stubAuthorizePost(OK, authResponse.toString())
          stubPost(s"/write/.*", OK, """{"x":2}""")

          val request = FakeRequest(GET, "/protect-tax-info" + urlPath)
            .withSession(xAuthToken, xSessionId)
          val result = route(app, request).get

          status(result) shouldBe SEE_OTHER
          redirectLocation(result).get should include(accountCheckPath)
        }
      }
    }

    "the session cache is empty" should {
      s"redirect to login" in {
        val authResponse = authoriseResponseJson()
        stubAuthorizePost(OK, authResponse.toString())
        stubPost(s"/write/.*", OK, """{"x":2}""")

        val request = FakeRequest(GET, "/protect-tax-info" + urlPath)
          .withSession(xAuthToken, xSessionId)
        val result = route(app, request).get

        status(result) shouldBe SEE_OTHER
        redirectLocation(result).get should include("/bas-gateway/sign-in")
      }
    }

    "the user has a session missing required element NINO" should {
      s"redirect to ${ItUrlPaths.unauthorizedPath}" in {
        val authResponse = authoriseResponseJson(optNino = None)
        stubAuthorizePost(OK, authResponse.toString())
        stubPost(s"/write/.*", OK, """{"x":2}""")

        val request = FakeRequest(GET, "/protect-tax-info" + urlPath)
          .withSession(xAuthToken, xSessionId)
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
          .withSession(xAuthToken, xSessionId)
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
          .withSession(xAuthToken, xSessionId)
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
          .withSession(xAuthToken, xSessionId)
        val result = route(app, request).get

        status(result) shouldBe SEE_OTHER
        redirectLocation(result).get should include("/bas-gateway/sign-in")
      }
    }
  }

  s"POST $urlPath" when {

    import play.api.test.Helpers.status
    s"the session cache contains Account type of $SA_ASSIGNED_TO_OTHER_USER" should {
      s"redirect to the ${ItUrlPaths.saOnOtherAccountSigninAgainPath}" when {
        "the user selects yes and has not already been assigned a PT enrolment" in {
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

          val request = FakeRequest(POST, "/protect-tax-info" + urlPath)
            .withSession(xAuthToken, xSessionId)
            .withBody(Map("select-continue" -> Seq("yes")))
          val result = route(app, request).get

          status(result) shouldBe SEE_OTHER
          redirectLocation(result).get should include(
            ItUrlPaths.saOnOtherAccountSigninAgainPath
          )
        }
      }

      s"redirect to the ${ItUrlPaths.enrolledPTSAOnOtherAccountPath}" when {
        "the user selects yes and has already been assigned a PT enrolment" in {
          await(save[String](sessionId, "redirectURL", returnUrl))
          await(
            save[AccountTypes.Value](
              sessionId,
              "ACCOUNT_TYPE",
              SA_ASSIGNED_TO_OTHER_USER
            )
          )

          val authResponse = authoriseResponseWithPTEnrolment()
          stubAuthorizePost(OK, authResponse.toString())
          stubPost(s"/write/.*", OK, """{"x":2}""")

          val request = FakeRequest(POST, "/protect-tax-info" + urlPath)
            .withSession(xAuthToken, xSessionId)
            .withBody(Map("select-continue" -> Seq("yes")))
          val result = route(app, request).get

          status(result) shouldBe SEE_OTHER
          redirectLocation(result).get should include(
            ItUrlPaths.enrolledPTSAOnOtherAccountPath
          )
        }
      }

      s"enrol the user for PT and redirect to ${ItUrlPaths.enrolledPTSAOnOtherAccountPath} url" when {
        "the user selects no" in {
          val cacheData = Map(
            ACCOUNT_TYPE                                 -> Json.toJson(SA_ASSIGNED_TO_OTHER_USER),
            REDIRECT_URL                                 -> JsString(returnUrl),
            USER_ASSIGNED_SA_ENROLMENT                   -> Json.toJson(saUsers),
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

          val request = FakeRequest(POST, "/protect-tax-info" + urlPath)
            .withSession(xAuthToken, xSessionId)
            .withBody(Map("select-continue" -> Seq("no")))
          val result = route(app, request).get

          status(result) shouldBe SEE_OTHER
          redirectLocation(result).get should include(
            ItUrlPaths.enrolledPTSAOnOtherAccountPath
          )

          val expectedAuditEvent = AuditEvent.auditSuccessfullyEnrolledPTWhenSAOnOtherAccount(
          )(requestWithAccountType(SA_ASSIGNED_TO_OTHER_USER, mongoCacheData = cacheData), messagesApi)
          verifyAuditEventSent(expectedAuditEvent)
        }
      }

      s"not enrol the user again for PT and redirect to ${ItUrlPaths.enrolledPTSAOnOtherAccountPath} url" when {
        "the user selects no and has already been assigned a PT enrolment" in {
          val cacheData = Map(
            ACCOUNT_TYPE                                 -> Json.toJson(SA_ASSIGNED_TO_OTHER_USER),
            REDIRECT_URL                                 -> JsString(returnUrl),
            USER_ASSIGNED_SA_ENROLMENT                   -> Json.toJson(saUsers),
            accountDetailsForCredential(CREDENTIAL_ID_2) -> Json.toJson(accountDetails)
          )
          await(save(sessionId, cacheData))

          val authResponse = authoriseResponseWithPTEnrolment()
          stubAuthorizePost(OK, authResponse.toString())
          stubPost(s"/write/.*", OK, """{"x":2}""")

          val request = FakeRequest(POST, "/protect-tax-info" + urlPath)
            .withSession(xAuthToken, xSessionId)
            .withBody(Map("select-continue" -> Seq("no")))
          val result = route(app, request).get

          status(result) shouldBe SEE_OTHER
          redirectLocation(result).get should include(
            ItUrlPaths.enrolledPTSAOnOtherAccountPath
          )
        }
      }

      "render the error page if enrolment fails" when {
        "the use selected no" in {
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

          val request = FakeRequest(POST, "/protect-tax-info" + urlPath)
            .withSession(xAuthToken, xSessionId)
            .withBody(Map("select-continue" -> Seq("no")))
          val result = route(app, request).get

          status(result) shouldBe INTERNAL_SERVER_ERROR
          contentAsString(result) should include(ErrorTemplateMessages.title)

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
        s"redirect to /protect-tax-info" when {
          "yes is selected" in {
            await(save[String](sessionId, "redirectURL", returnUrl))
            await(
              save[AccountTypes.Value](sessionId, "ACCOUNT_TYPE", accountType)
            )
            val authResponse = authoriseResponseJson()
            stubAuthorizePost(OK, authResponse.toString())
            stubPost(s"/write/.*", OK, """{"x":2}""")

            val request = FakeRequest(POST, "/protect-tax-info" + urlPath)
              .withSession(xAuthToken, xSessionId)
              .withBody(Map("select-continue" -> Seq("yes")))
            val result = route(app, request).get

            status(result) shouldBe SEE_OTHER
            redirectLocation(result).get should include(accountCheckPath)
          }
          "no is selected" in {
            await(save[String](sessionId, "redirectURL", returnUrl))
            await(
              save[AccountTypes.Value](sessionId, "ACCOUNT_TYPE", accountType)
            )
            val authResponse = authoriseResponseJson()
            stubAuthorizePost(OK, authResponse.toString())
            stubPost(s"/write/.*", OK, """{"x":2}""")

            val request = FakeRequest(POST, "/protect-tax-info" + urlPath)
              .withSession(xAuthToken, xSessionId)
              .withBody(Map("select-continue" -> Seq("no")))
            val result = route(app, request).get

            status(result) shouldBe SEE_OTHER
            redirectLocation(result).get should include(accountCheckPath)
          }
        }
      }
    }

    "the session cache does not contain the redirect url" should {
      s"return $INTERNAL_SERVER_ERROR" when {
        "yes is selected" in {
          val authResponse = authoriseResponseJson()
          stubAuthorizePost(OK, authResponse.toString())
          stubPost(s"/write/.*", OK, """{"x":2}""")
          await(
            save[AccountTypes.Value](sessionId, "ACCOUNT_TYPE", SA_ASSIGNED_TO_CURRENT_USER)
          )

          val request = FakeRequest(POST, "/protect-tax-info" + urlPath)
            .withSession(xAuthToken, xSessionId)
            .withBody(Map("select-continue" -> Seq("yes")))
          val result = route(app, request).get

          status(result) shouldBe INTERNAL_SERVER_ERROR
          contentAsString(result) should include(ErrorTemplateMessages.title)
        }

        "no is selected" in {
          val authResponse = authoriseResponseJson()
          stubAuthorizePost(OK, authResponse.toString())
          stubPost(s"/write/.*", OK, """{"x":2}""")
          await(
            save[AccountTypes.Value](sessionId, "ACCOUNT_TYPE", SA_ASSIGNED_TO_CURRENT_USER)
          )

          val request = FakeRequest(POST, "/protect-tax-info" + urlPath)
            .withSession(xAuthToken, xSessionId)
            .withBody(Map("select-continue" -> Seq("no")))
          val result = route(app, request).get

          status(result) shouldBe INTERNAL_SERVER_ERROR
          contentAsString(result) should include(ErrorTemplateMessages.title)
        }
      }
    }

    "an invalid form is supplied" should {
      "render the keepAccessToSA page with errors" in {
        val authResponse = authoriseResponseJson()
        stubAuthorizePost(OK, authResponse.toString())
        await(save[AccountTypes.Value](sessionId, "ACCOUNT_TYPE", SA_ASSIGNED_TO_CURRENT_USER))
        await(save[String](sessionId, "redirectURL", returnUrl))
        stubPost(s"/write/.*", OK, """{"x":2}""")

        val request = FakeRequest(POST, "/protect-tax-info" + urlPath)
          .withSession(xAuthToken, xSessionId)
          .withBody(Map("select-continue" -> Seq("error")))
        val result = route(app, request).get
        val page = Jsoup.parse(contentAsString(result))

        status(result) shouldBe BAD_REQUEST
        page.title() should include(s"Error - ${KeepAccessToSAMessages.title}")
        page
          .getElementsByClass("govuk-error-summary__title")
          .text() shouldBe KeepAccessToSAMessages.errorTitle
        page
          .getElementsByClass("govuk-list govuk-error-summary__list")
          .first()
          .text() shouldBe KeepAccessToSAMessages.errorMessage

      }
    }
  }
}
