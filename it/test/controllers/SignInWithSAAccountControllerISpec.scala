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
import helpers.messages._
import helpers.{IntegrationSpecBase, ItUrlPaths}
import org.jsoup.Jsoup
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import play.api.http.Status
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, POST, contentAsString, defaultAwaitTimeout, redirectLocation, route, status, writeableOf_AnyContentAsEmpty, writeableOf_AnyContentAsJson}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.{AccountDetails, UserAnswers, UsersAssignedEnrolment}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.pages.{AccountDetailsForCredentialPage, AccountTypePage, RedirectUrlPage, UserAssignedSaEnrolmentPage}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.reporting.AuditEvent

import scala.concurrent.Future

class SignInWithSAAccountControllerISpec extends IntegrationSpecBase with Status {

  val urlPath: String = ItUrlPaths.saOnOtherAccountSigninAgainPath

  s"GET $urlPath" when {
    "the session cache has a credential for SA enrolment that is not the signed in account" should {
      s"return $OK with the sign in again page" when {
        "the user has not already been assigned a PT enrolment" in {
          val mockUserAnswers = UserAnswers(sessionId, generateNino.nino)
            .setOrException(AccountTypePage, SA_ASSIGNED_TO_OTHER_USER.toString)
            .setOrException(RedirectUrlPage, returnUrl)
            .setOrException(UserAssignedSaEnrolmentPage, UsersAssignedEnrolment(Some(CREDENTIAL_ID_2)))

          when(mockJourneyCacheRepository.get(any(), any()))
            .thenReturn(Future.successful(Some(mockUserAnswers)))

          when(mockJourneyCacheRepository.set(any[UserAnswers])).thenReturn(Future.successful(true))

          val authResponse = authoriseResponseJson()
          stubAuthorizePost(OK, authResponse.toString())
          stubPost(s"/write/.*", OK, """{"x":2}""")
          stubGet(
            s"/users-groups-search/users/$CREDENTIAL_ID",
            NON_AUTHORITATIVE_INFORMATION,
            usergroupsResponseJson().toString()
          )
          stubGet(
            s"/users-groups-search/users/$CREDENTIAL_ID_2",
            NON_AUTHORITATIVE_INFORMATION,
            usergroupsResponseJson().toString()
          )

          val request = FakeRequest(GET, "/protect-tax-info" + urlPath)
            .withSession(xAuthToken, xSessionId)
          val result = route(app, request).get
          val page = Jsoup.parse(contentAsString(result))

          status(result) shouldBe OK
          page.title should include(SignInAgainMessages.title)
          page
            .getElementsByClass("govuk-body")
            .get(0)
            .text shouldBe SignInAgainMessages.paragraphLinkText
          page
            .getElementsByTag("p")
            .get(1)
            .text shouldBe SignInAgainMessages.paragraph1(USER_ID)
          page
            .getElementsByTag("p")
            .get(2)
            .text shouldBe SignInAgainMessages.paragraph2
        }
      }
      s"redirect to ${ItUrlPaths.enrolledPTSAOnOtherAccountPath}" when {
        "the user has already been assigned a PT enrolment" in {

          val mockUserAnswers = UserAnswers(sessionId, generateNino.nino)
            .setOrException(AccountTypePage, SA_ASSIGNED_TO_OTHER_USER.toString)
            .setOrException(RedirectUrlPage, returnUrl)
            .setOrException(UserAssignedSaEnrolmentPage, UsersAssignedEnrolment(Some(CREDENTIAL_ID_2)))

          when(mockJourneyCacheRepository.get(any(), any()))
            .thenReturn(Future.successful(Some(mockUserAnswers)))

          when(mockJourneyCacheRepository.set(any[UserAnswers])).thenReturn(Future.successful(true))

          val authResponse = authoriseResponseWithPTEnrolment()
          stubAuthorizePost(OK, authResponse.toString())
          stubPost(s"/write/.*", OK, """{"x":2}""")
          stubGet( // TODO - Investigate why the double call now
            s"/users-groups-search/users/$CREDENTIAL_ID",
            NON_AUTHORITATIVE_INFORMATION,
            usergroupsResponseJson().toString()
          )
          stubGet(
            s"/users-groups-search/users/$CREDENTIAL_ID_2",
            NON_AUTHORITATIVE_INFORMATION,
            usergroupsResponseJson().toString()
          )

          val request = FakeRequest(GET, "/protect-tax-info" + urlPath)
            .withSession(xAuthToken, xSessionId)
          val result = route(app, request).get

          status(result) shouldBe SEE_OTHER
          redirectLocation(result).get should include(
            ItUrlPaths.enrolledPTSAOnOtherAccountPath
          )

        }
      }
    }

    List(
      PT_ASSIGNED_TO_CURRENT_USER,
      SINGLE_OR_MULTIPLE_ACCOUNTS,
      PT_ASSIGNED_TO_OTHER_USER,
      SA_ASSIGNED_TO_CURRENT_USER
    ).foreach { accountType =>
      s"the session cache has a credential with account type ${accountType.toString}" should {
        s"redirect to /protect-tax-info" in {
          val mockUserAnswers = UserAnswers(sessionId, generateNino.nino)
            .setOrException(AccountTypePage, accountType.toString)
            .setOrException(RedirectUrlPage, returnUrl)

          when(mockJourneyCacheRepository.get(any(), any()))
            .thenReturn(Future.successful(Some(mockUserAnswers)))

          when(mockJourneyCacheRepository.set(any[UserAnswers])).thenReturn(Future.successful(true))

          val authResponse = authoriseResponseJson()
          stubAuthorizePost(OK, authResponse.toString())
          stubPost(s"/write/.*", OK, """{"x":2}""")

          val request = FakeRequest(GET, "/protect-tax-info" + urlPath)
            .withSession(xAuthToken, xSessionId)
          val result = route(app, request).get

          status(result) shouldBe SEE_OTHER
          redirectLocation(result).get should include(
            accountCheckPath
          )

        }
      }
    }

    s"the session cache has a credential for SA enrolment that is the signed in account" should {
      s"render the error page" in {

        val mockUserAnswers = UserAnswers(sessionId, generateNino.nino)
          .setOrException(AccountTypePage, SA_ASSIGNED_TO_OTHER_USER.toString)
          .setOrException(RedirectUrlPage, returnUrl)
          .setOrException(UserAssignedSaEnrolmentPage, UsersAssignedEnrolment(Some(CREDENTIAL_ID)))

        when(mockJourneyCacheRepository.get(any(), any()))
          .thenReturn(Future.successful(Some(mockUserAnswers)))

        when(mockJourneyCacheRepository.set(any[UserAnswers])).thenReturn(Future.successful(true))

        val authResponse = authoriseResponseJson()
        stubAuthorizePost(OK, authResponse.toString())
        stubPost(s"/write/.*", OK, """{"x":2}""")

        val request = FakeRequest(GET, "/protect-tax-info" + urlPath)
          .withSession(xAuthToken, xSessionId)
        val result = route(app, request).get

        status(result) shouldBe INTERNAL_SERVER_ERROR
        contentAsString(result) should include(ErrorTemplateMessages.title)

      }
    }

    s"the session cache has no credentials with SA enrolment" should {
      s"render the error page" in {

        val mockUserAnswers = UserAnswers(sessionId, generateNino.nino)
          .setOrException(AccountTypePage, SA_ASSIGNED_TO_OTHER_USER.toString)
          .setOrException(RedirectUrlPage, returnUrl)
          .setOrException(UserAssignedSaEnrolmentPage, UsersAssignedEnrolment(None))

        when(mockJourneyCacheRepository.get(any(), any()))
          .thenReturn(Future.successful(Some(mockUserAnswers)))

        when(mockJourneyCacheRepository.set(any[UserAnswers])).thenReturn(Future.successful(true))
        val authResponse = authoriseResponseJson()
        stubAuthorizePost(OK, authResponse.toString())

        stubPost(s"/write/.*", OK, """{"x":2}""")

        val request = FakeRequest(GET, "/protect-tax-info" + urlPath)
          .withSession(xAuthToken, xSessionId)
        val result = route(app, request).get

        status(result) shouldBe INTERNAL_SERVER_ERROR
        contentAsString(result) should include(ErrorTemplateMessages.title)

      }
    }

    "the session cache has no redirectUrl" should {
      s"return $INTERNAL_SERVER_ERROR" in {
        val mockUserAnswers = UserAnswers(sessionId, generateNino.nino)
          .setOrException(AccountTypePage, SA_ASSIGNED_TO_OTHER_USER.toString)

        when(mockJourneyCacheRepository.get(any(), any()))
          .thenReturn(Future.successful(Some(mockUserAnswers)))
        val authResponse = authoriseResponseJson()
        stubAuthorizePost(OK, authResponse.toString())
        stubPost(s"/write/.*", OK, """{"x":2}""")

        val request = FakeRequest(GET, "/protect-tax-info" + urlPath)
          .withSession(xAuthToken, xSessionId)
        val result = route(app, request).get

        status(result) shouldBe INTERNAL_SERVER_ERROR
        contentAsString(result) should include(ErrorTemplateMessages.title)

      }
    }

    "users group search returns an error" should {
      "render the error page" in {
        val mockUserAnswers = UserAnswers(sessionId, generateNino.nino)
          .setOrException(AccountTypePage, SA_ASSIGNED_TO_OTHER_USER.toString)
          .setOrException(RedirectUrlPage, returnUrl)
          .setOrException(UserAssignedSaEnrolmentPage, UsersAssignedEnrolment(Some(CREDENTIAL_ID_2)))

        when(mockJourneyCacheRepository.get(any(), any()))
          .thenReturn(Future.successful(Some(mockUserAnswers)))

        val authResponse = authoriseResponseJson()
        stubAuthorizePost(OK, authResponse.toString())
        stubPost(s"/write/.*", OK, """{"x":2}""")
        stubGet(
          s"/users-groups-search/users/$CREDENTIAL_ID_2",
          INTERNAL_SERVER_ERROR,
          ""
        )

        val request = FakeRequest(GET, "/protect-tax-info" + urlPath)
          .withSession(xAuthToken, xSessionId)
        val result = route(app, request).get

        status(result) shouldBe INTERNAL_SERVER_ERROR
        contentAsString(result) should include(ErrorTemplateMessages.title)

      }
    }

    "the user has a session missing required element NINO" should {
      s"return $SEE_OTHER" in {
        val authResponse = authoriseResponseJson(optNino = None)
        stubAuthorizePost(OK, authResponse.toString())
        stubPost(s"/write/.*", OK, """{"x":2}""")

        val request = FakeRequest(GET, "/protect-tax-info" + urlPath)
          .withSession(xAuthToken, xSessionId)
        val result = route(app, request).get

        status(result) shouldBe SEE_OTHER
        redirectLocation(result).get should include(
          ItUrlPaths.unauthorizedPath
        )

      }
    }

    "the user has a session missing required element Credentials" should {
      s"return $SEE_OTHER" in {
        val authResponse = authoriseResponseJson(optCreds = None)
        stubAuthorizePost(OK, authResponse.toString())
        stubPost(s"/write/.*", OK, """{"x":2}""")

        val request = FakeRequest(GET, "/protect-tax-info" + urlPath)
          .withSession(xAuthToken, xSessionId)
        val result = route(app, request).get

        status(result) shouldBe SEE_OTHER
        redirectLocation(result).get should include(
          ItUrlPaths.unauthorizedPath
        )

      }
    }

    "the user has a insufficient confidence level" should {
      s"return $SEE_OTHER" in {
        stubAuthorizePostUnauthorised(insufficientConfidenceLevel)
        stubPost(s"/write/.*", OK, """{"x":2}""")

        val request = FakeRequest(GET, "/protect-tax-info" + urlPath)
          .withSession(xAuthToken, xSessionId)
        val result = route(app, request).get

        status(result) shouldBe SEE_OTHER
        redirectLocation(result).get should include(
          ItUrlPaths.unauthorizedPath
        )
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

    import play.api.test.Helpers.redirectLocation
    "the session cache has a credential for SA enrolment that is not the signed in account" should {
      s"redirect to ${ItUrlPaths.logoutPath}" in {

        val mockUserAnswers = UserAnswers(sessionId, generateNino.nino)
          .setOrException(AccountTypePage, SA_ASSIGNED_TO_OTHER_USER.toString)
          .setOrException(RedirectUrlPage, returnUrl)
          .setOrException(UserAssignedSaEnrolmentPage, saUsers)
          .setOrException(AccountDetailsForCredentialPage(CREDENTIAL_ID_2), accountDetails)(
            AccountDetails.mongoFormats(crypto.crypto)
          )

        when(mockJourneyCacheRepository.get(any(), any()))
          .thenReturn(Future.successful(Some(mockUserAnswers)))

        val authResponse = authoriseResponseJson()
        stubAuthorizePost(OK, authResponse.toString())
        stubPost(s"/write/.*", OK, """{"x":2}""")

        val request = FakeRequest(POST, "/protect-tax-info" + urlPath)
          .withSession(xAuthToken, xSessionId)
          .withJsonBody(Json.obj())

        val result = route(app, request).get

        status(result) shouldBe SEE_OTHER
        redirectLocation(result).get should include(
          ItUrlPaths.logoutPath
        )
        val expectedAuditEvent = AuditEvent.auditSigninAgainWithSACredential()(
          requestWithGivenMongoDataAndUserAnswers(requestWithAccountType(SA_ASSIGNED_TO_OTHER_USER), mockUserAnswers),
          messagesApi,
          crypto
        )
        verifyAuditEventSent(expectedAuditEvent)

      }
    }

    "the session cache has no redirectUrl" should {
      s"return $INTERNAL_SERVER_ERROR" in {
        val mockUserAnswers = UserAnswers(sessionId, generateNino.nino)
          .setOrException(AccountTypePage, SA_ASSIGNED_TO_OTHER_USER.toString)

        when(mockJourneyCacheRepository.get(any(), any()))
          .thenReturn(Future.successful(Some(mockUserAnswers)))
        val authResponse = authoriseResponseJson()
        stubAuthorizePost(OK, authResponse.toString())
        stubPost(s"/write/.*", OK, """{"x":2}""")

        val request = FakeRequest(POST, "/protect-tax-info" + urlPath)
          .withSession(xAuthToken, xSessionId)
          .withJsonBody(Json.obj())

        val result = route(app, request).get
        status(result) shouldBe INTERNAL_SERVER_ERROR
        contentAsString(result) should include(ErrorTemplateMessages.title)

      }
    }
    "the user has a session missing required element NINO" should {
      s"redirect to ${ItUrlPaths.unauthorizedPath}" in {
        val authResponse = authoriseResponseJson(optNino = None)
        stubAuthorizePost(OK, authResponse.toString())
        stubPost(s"/write/.*", OK, """{"x":2}""")

        val request = FakeRequest(POST, "/protect-tax-info" + urlPath)
          .withSession(xAuthToken, xSessionId)
          .withJsonBody(Json.obj())

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

        val request = FakeRequest(POST, "/protect-tax-info" + urlPath)
          .withSession(xAuthToken, xSessionId)
          .withJsonBody(Json.obj())

        val result = route(app, request).get

        status(result) shouldBe SEE_OTHER
        redirectLocation(result).get should include(ItUrlPaths.unauthorizedPath)

      }
    }

    "the user has a insufficient confidence level" should {
      s"redirect to ${ItUrlPaths.unauthorizedPath}" in {
        stubAuthorizePostUnauthorised(insufficientConfidenceLevel)
        stubPost(s"/write/.*", OK, """{"x":2}""")

        val request = FakeRequest(POST, "/protect-tax-info" + urlPath)
          .withSession(xAuthToken, xSessionId)
          .withJsonBody(Json.obj())

        val result = route(app, request).get

        status(result) shouldBe SEE_OTHER
        redirectLocation(result).get should include(ItUrlPaths.unauthorizedPath)

      }
    }

    "the user has no active session" should {
      s"redirect to login" in {
        stubAuthorizePostUnauthorised(sessionNotFound)
        stubPost(s"/write/.*", OK, """{"x":2}""")

        val request = FakeRequest(POST, "/protect-tax-info" + urlPath)
          .withSession(xAuthToken, xSessionId)
          .withJsonBody(Json.obj())

        val result = route(app, request).get

        status(result) shouldBe SEE_OTHER
        redirectLocation(result).get should include("/bas-gateway/sign-in")

      }
    }
  }
}
