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
import org.mockito.Mockito.{reset, when}
import play.api.http.Status.{INTERNAL_SERVER_ERROR, NON_AUTHORITATIVE_INFORMATION, NOT_FOUND, OK, SEE_OTHER}
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, POST, contentAsString, defaultAwaitTimeout, redirectLocation, route, status, writeableOf_AnyContentAsEmpty, writeableOf_AnyContentAsJson}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.UserAnswers
import uk.gov.hmrc.taxenrolmentassignmentfrontend.pages.{AccountTypePage, RedirectUrlPage}

import scala.concurrent.Future

class EnrolledForPTISpec extends IntegrationSpecBase {

  val urlPath: String = ItUrlPaths.enrolledPTNoSAOnAnyAccountPath

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockJourneyCacheRepository)
    when(mockJourneyCacheRepository.set(any[UserAnswers])).thenReturn(Future.successful(true))
    when(mockJourneyCacheRepository.clear(any(), any())).thenReturn(Future.successful(true))
  }

  s"GET $urlPath" when {
    s"the session cache has Account type of $SINGLE_OR_MULTIPLE_ACCOUNTS" should {
      s"render the EnrolledForPT page" in {

        val mockUserAnswers = UserAnswers(sessionId, generateNino.nino)
          .setOrException(AccountTypePage, SINGLE_OR_MULTIPLE_ACCOUNTS.toString)
          .setOrException(RedirectUrlPage, returnUrl)

        when(mockJourneyCacheRepository.get(any(), any()))
          .thenReturn(Future.successful(Some(mockUserAnswers)))

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

    List(PT_ASSIGNED_TO_OTHER_USER, PT_ASSIGNED_TO_CURRENT_USER)
      .foreach { accountType =>
        s"the session cache has Account type of $accountType" should {
          s"redirect to {accountCheckPath}" in {
            val mockUserAnswers = UserAnswers(sessionId, generateNino.nino)
              .setOrException(AccountTypePage, accountType.toString)
              .setOrException(RedirectUrlPage, returnUrl)

            when(mockJourneyCacheRepository.get(any(), any()))
              .thenReturn(Future.successful(Some(mockUserAnswers)))

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

        when(mockJourneyCacheRepository.get(any(), any()))
          .thenReturn(Future.successful(None))

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
        val mockUserAnswers = UserAnswers(sessionId, generateNino.nino)
          .setOrException(AccountTypePage, SA_ASSIGNED_TO_CURRENT_USER.toString)
          .setOrException(RedirectUrlPage, returnUrl)

        when(mockJourneyCacheRepository.get(any(), any()))
          .thenReturn(Future.successful(Some(mockUserAnswers)))

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

        status(result) shouldBe INTERNAL_SERVER_ERROR
        contentAsString(result) should include(ErrorTemplateMessages.title)
      }
    }

    "an authorised user but IV returns internal error" should {
      s"return $INTERNAL_SERVER_ERROR" in {
        val mockUserAnswers = UserAnswers(sessionId, generateNino.nino)
          .setOrException(AccountTypePage, SA_ASSIGNED_TO_CURRENT_USER.toString)
          .setOrException(RedirectUrlPage, returnUrl)

        when(mockJourneyCacheRepository.get(any(), any()))
          .thenReturn(Future.successful(Some(mockUserAnswers)))

        stubAuthorizePost(OK, authoriseResponseWithPTEnrolment().toString())
        stubPost(s"/write/.*", OK, """{"x":2}""")
        stubGetWithQueryParam(
          "/identity-verification/nino",
          "nino",
          NINO.nino,
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

        val mockUserAnswers = UserAnswers(sessionId, generateNino.nino)
          .setOrException(AccountTypePage, SA_ASSIGNED_TO_CURRENT_USER.toString)
          .setOrException(RedirectUrlPage, returnUrl)

        when(mockJourneyCacheRepository.get(any(), any()))
          .thenReturn(Future.successful(Some(mockUserAnswers)))

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
    "the session cache contains the redirect url" should {
      s"redirect to the redirect url" in {
        val mockUserAnswers = UserAnswers(sessionId, generateNino.nino)
          .setOrException(AccountTypePage, PT_ASSIGNED_TO_CURRENT_USER.toString)
          .setOrException(RedirectUrlPage, returnUrl)

        when(mockJourneyCacheRepository.get(any(), any()))
          .thenReturn(Future.successful(Some(mockUserAnswers)))

        val authResponse = authoriseResponseWithPTEnrolment()
        stubAuthorizePost(OK, authResponse.toString())
        stubPost(s"/write/.*", OK, """{"x":2}""")

        val request = FakeRequest(POST, "/protect-tax-info" + urlPath)
          .withSession(xSessionId, xAuthToken)
          .withJsonBody(Json.obj())
        val result = route(app, request).get

        status(result) shouldBe SEE_OTHER
        redirectLocation(result).get should include(returnUrl)

      }
    }

    "the session cache is empty" should {
      s"redirect to login" in {

        when(mockJourneyCacheRepository.get(any(), any()))
          .thenReturn(Future.successful(None))

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
