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
import org.mockito.ArgumentMatchers.{any, anyString}
import org.mockito.Mockito.when
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.UserAnswers
import uk.gov.hmrc.taxenrolmentassignmentfrontend.pages.{AccountTypePage, RedirectUrlPage, ReportedFraudPage, UserAssignedSaEnrolmentPage}

import scala.concurrent.Future

class EnrolledPTWithSAOnOtherAccountControllerISpec extends IntegrationSpecBase {

  val urlPath: String =
    ItUrlPaths.enrolledPTSAOnOtherAccountPath

  s"GET $urlPath" when {
    s"the session cache has Account type of $SA_ASSIGNED_TO_OTHER_USER and no fraud reported" should {
      s"render the enrolledPTPage that includes SA details" in {

        val mockUserAnswers = UserAnswers(sessionId, generateNino.nino)
          .setOrException(AccountTypePage, SA_ASSIGNED_TO_OTHER_USER.toString)
          .setOrException(RedirectUrlPage, returnUrl)
          .setOrException(UserAssignedSaEnrolmentPage, saUsers)

        when(mockJourneyCacheRepository.get(any(), any()))
          .thenReturn(Future.successful(Some(mockUserAnswers)))

        when(mockJourneyCacheRepository.set(any[UserAnswers])).thenReturn(Future.successful(true))

        val authResponse = authoriseResponseWithPTEnrolment()
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
        page.title should include(
          EnrolledPTWithSAOnOtherAccountMessages.title
        )
        page
          .getElementsByTag("p")
          .get(0)
          .text() shouldBe EnrolledPTWithSAOnOtherAccountMessages.paragraph1(USER_ID)
        page
          .getElementsByTag("p")
          .get(1)
          .text() shouldBe EnrolledPTWithSAOnOtherAccountMessages.paragraph2("********6037")
        page
          .getElementsByTag("p")
          .get(2)
          .text() shouldBe EnrolledPTWithSAOnOtherAccountMessages.paragraph3
      }
    }

    s"the session cache has Account type of $SA_ASSIGNED_TO_OTHER_USER and the user has reported fraud" should {
      s"render INTERNAL_SERVER_ERROR with no self assessment information" in {
        val mockUserAnswers = UserAnswers(sessionId, generateNino.nino)
          .setOrException(AccountTypePage, SA_ASSIGNED_TO_OTHER_USER.toString)
          .setOrException(RedirectUrlPage, returnUrl)
          .setOrException(ReportedFraudPage, true)

        when(mockJourneyCacheRepository.get(any(), any()))
          .thenReturn(Future.successful(Some(mockUserAnswers)))

        when(mockJourneyCacheRepository.set(any[UserAnswers])).thenReturn(Future.successful(true))

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

        status(result) shouldBe INTERNAL_SERVER_ERROR
      }
    }

    s"the session cache has Account type of $SA_ASSIGNED_TO_OTHER_USER but users group search fails" should {
      s"return $INTERNAL_SERVER_ERROR" in {

        val mockUserAnswers = UserAnswers(sessionId, generateNino.nino)
          .setOrException(AccountTypePage, SA_ASSIGNED_TO_OTHER_USER.toString)
          .setOrException(RedirectUrlPage, returnUrl)

        when(mockJourneyCacheRepository.get(any(), any()))
          .thenReturn(Future.successful(Some(mockUserAnswers)))

        val authResponse = authoriseResponseWithPTEnrolment(
          optCreds = Some(creds.copy(providerId = CREDENTIAL_ID_3))
        )
        stubAuthorizePost(OK, authResponse.toString())
        stubPost(s"/write/.*", OK, """{"x":2}""")
        stubGet(
          s"/users-groups-search/users/$CREDENTIAL_ID_3",
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

    List(
      PT_ASSIGNED_TO_OTHER_USER,
      PT_ASSIGNED_TO_CURRENT_USER,
      SINGLE_OR_MULTIPLE_ACCOUNTS,
      SA_ASSIGNED_TO_CURRENT_USER
    ).foreach { accountType =>
      s"the session cache has Account type of $accountType" should {
        s"redirect to /protect-tax-info" in {
          val mockUserAnswers = UserAnswers(sessionId, generateNino.nino)
            .setOrException(AccountTypePage, accountType.toString)
            .setOrException(RedirectUrlPage, returnUrl)

          when(mockJourneyCacheRepository.get(any(), any()))
            .thenReturn(Future.successful(Some(mockUserAnswers)))

          val authResponse = authoriseResponseWithPTEnrolment()
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

    "the session cache is empty" should {
      s"redirect to login" in {

        when(mockJourneyCacheRepository.get(any(), any()))
          .thenReturn(Future.successful(None))

        val authResponse = authoriseResponseWithPTEnrolment()
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
        val authResponse = authoriseResponseWithPTEnrolment(optNino = None)
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
        val authResponse = authoriseResponseWithPTEnrolment(optCreds = None)
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
    "the session cache contains the redirect url" should {
      s"redirect to the redirect url" in {
        val mockUserAnswers = UserAnswers(sessionId, generateNino.nino)
          .setOrException(AccountTypePage, PT_ASSIGNED_TO_CURRENT_USER.toString)
          .setOrException(RedirectUrlPage, returnUrl)

        when(mockJourneyCacheRepository.get(any(), any()))
          .thenReturn(Future.successful(Some(mockUserAnswers)))

        when(mockJourneyCacheRepository.clear(anyString(), anyString())) thenReturn Future.successful(true)

        val authResponse = authoriseResponseWithPTEnrolment()
        stubAuthorizePost(OK, authResponse.toString())
        stubPost(s"/write/.*", OK, """{"x":2}""")

        val request = FakeRequest(POST, "/protect-tax-info" + urlPath)
          .withSession(xAuthToken, xSessionId)
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
        when(mockJourneyCacheRepository.clear(anyString(), anyString())).thenReturn(Future.successful(true))

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
