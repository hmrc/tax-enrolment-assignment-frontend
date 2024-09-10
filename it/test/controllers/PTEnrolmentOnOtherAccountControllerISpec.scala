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

import com.github.tomakehurst.wiremock.stubbing.StubMapping
import helpers.TestITData._
import helpers.messages._
import helpers.{IntegrationSpecBase, ItUrlPaths}
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, when}
import org.mockito.stubbing.OngoingStubbing
import play.api.http.Status.{INTERNAL_SERVER_ERROR, OK, SEE_OTHER}
import play.api.mvc.{AnyContentAsEmpty, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, contentAsString, defaultAwaitTimeout, redirectLocation, route, status, writeableOf_AnyContentAsEmpty}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes.{PT_ASSIGNED_TO_CURRENT_USER, PT_ASSIGNED_TO_OTHER_USER, SA_ASSIGNED_TO_CURRENT_USER, SA_ASSIGNED_TO_OTHER_USER, SINGLE_OR_MULTIPLE_ACCOUNTS}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.{UserAnswers, UsersAssignedEnrolment}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.pages.{AccountTypePage, RedirectUrlPage, UserAssignedPtaEnrolmentPage, UserAssignedSaEnrolmentPage}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.reporting.AuditEvent
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.SessionKeys.{USER_ASSIGNED_PT_ENROLMENT, USER_ASSIGNED_SA_ENROLMENT}

import scala.concurrent.Future
import scala.jdk.CollectionConverters._

class PTEnrolmentOnOtherAccountControllerISpec extends IntegrationSpecBase {

  val urlPath: String = ItUrlPaths.ptOnOtherAccountPath

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockJourneyCacheRepository)
    when(mockJourneyCacheRepository.set(any[UserAnswers])).thenReturn(Future.successful(true))
    when(mockJourneyCacheRepository.clear(any(), any())).thenReturn(Future.successful(true))
  }

  s"GET $urlPath" when {
    "the signed in user has SA enrolment in session and PT enrolment on another account" should {
      s"render the pt on another account page" in new DataAndMockSetup {
        saveDataToCache(optSAEnrolledCredential = None)
        stubAuthoriseSuccess(true)
        stubUserGroupSearchSuccess(CREDENTIAL_ID, usersGroupSearchResponse)
        stubUserGroupSearchSuccess(
          CREDENTIAL_ID_2,
          usersGroupSearchResponsePTEnrolment(USER_ID)
        )

        val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest(GET, "/protect-tax-info" + urlPath)
          .withSession(xAuthToken, xSessionId)
        val result: Future[Result] = route(app, request).get
        val page: Document = Jsoup.parse(contentAsString(result))

        status(result) shouldBe OK
        page.title should include(PTEnrolmentOtherAccountMesages.title)
        page
          .getElementsByClass("govuk-heading-m")
          .text() shouldBe PTEnrolmentOtherAccountMesages.saHeading
        page
          .getElementsByClass("govuk-body")
          .asScala
          .toList
          .map(_.text()) should contain(
          PTEnrolmentOtherAccountMesages.saText3
        )

        val expectedAuditEvent: AuditEvent = AuditEvent.auditPTEnrolmentOnOtherAccount(
          accountDetailsUserFriendly(CREDENTIAL_ID_2)
        )(requestWithGivenMongoData(requestWithAccountType(PT_ASSIGNED_TO_OTHER_USER)), messagesApi)

        verifyAuditEventSent(expectedAuditEvent)
      }
    }

    "the signed in user has SA enrolment and a PT enrolment on another account" should {
      s"render the pt on another account page" in new DataAndMockSetup {
        saveDataToCache(optSAEnrolledCredential = Some(CREDENTIAL_ID_2))
        stubAuthoriseSuccess()
        stubUserGroupSearchSuccess(CREDENTIAL_ID, usersGroupSearchResponse)
        stubUserGroupSearchSuccess(
          CREDENTIAL_ID_2,
          usersGroupSearchResponsePTEnrolment()
        )

        val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest(GET, "/protect-tax-info" + urlPath)
          .withSession(xAuthToken, xSessionId)
        val result: Future[Result] = route(app, request).get
        val page: Document = Jsoup.parse(contentAsString(result))

        status(result) shouldBe OK
        page.title should include(PTEnrolmentOtherAccountMesages.title)
        page
          .getElementsByClass("govuk-heading-m")
          .text() shouldBe PTEnrolmentOtherAccountMesages.saHeading

        page
          .getElementsByClass("govuk-body")
          .asScala
          .toList
          .map(_.text()) should contain(
          PTEnrolmentOtherAccountMesages.saText2
        )

        val expectedAuditEvent: AuditEvent = AuditEvent.auditPTEnrolmentOnOtherAccount(
          accountDetailsUserFriendly(CREDENTIAL_ID_2, "1234")
        )(requestWithGivenMongoData(requestWithAccountType(PT_ASSIGNED_TO_OTHER_USER)), messagesApi)

        verifyAuditEventSent(expectedAuditEvent)

      }
    }

    "the user signed in has SA enrolment and PT enrolment on two other separate accounts" should {
      s"render the pt on another account page" in new DataAndMockSetup {
        saveDataToCache(optSAEnrolledCredential = Some(CREDENTIAL_ID_3))
        stubAuthoriseSuccess()
        stubUserGroupSearchSuccess(CREDENTIAL_ID, usersGroupSearchResponse)
        stubUserGroupSearchSuccess(
          CREDENTIAL_ID_2,
          usersGroupSearchResponsePTEnrolment(USER_ID)
        )
        stubUserGroupSearchSuccess(
          CREDENTIAL_ID_3,
          usersGroupSearchResponseSAEnrolment
        )

        val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest(GET, "/protect-tax-info" + urlPath)
          .withSession(xAuthToken, xSessionId)
        val result: Future[Result] = route(app, request).get
        val page: Document = Jsoup.parse(contentAsString(result))

        status(result) shouldBe OK
        page.title should include(PTEnrolmentOtherAccountMesages.title)
        page
          .getElementsByClass("govuk-heading-m")
          .text() shouldBe PTEnrolmentOtherAccountMesages.saHeading
        page
          .getElementsByClass("govuk-body")
          .asScala
          .toList
          .map(_.text()) should contain(PTEnrolmentOtherAccountMesages.saText)

        val expectedAuditEvent: AuditEvent = AuditEvent.auditPTEnrolmentOnOtherAccount(
          accountDetailsUserFriendly(CREDENTIAL_ID_2)
        )(requestWithGivenMongoData(requestWithAccountType(PT_ASSIGNED_TO_OTHER_USER)), messagesApi)

        verifyAuditEventSent(expectedAuditEvent)

      }
    }

    "the signed in user has no SA on any accounts but has PT enrolment on another account" should {
      s"render the pt on another account page" in new DataAndMockSetup {
        saveDataToCache(optSAEnrolledCredential = None)
        stubAuthoriseSuccess()
        stubUserGroupSearchSuccess(CREDENTIAL_ID, usersGroupSearchResponse)
        stubUserGroupSearchSuccess(
          CREDENTIAL_ID_2,
          usersGroupSearchResponsePTEnrolment(USER_ID)
        )

        val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest(GET, "/protect-tax-info" + urlPath)
          .withSession(xAuthToken, xSessionId)
        val result: Future[Result] = route(app, request).get
        val page: Document = Jsoup.parse(contentAsString(result))

        status(result) shouldBe OK
        page.title should include(PTEnrolmentOtherAccountMesages.title)
        page.getElementsByClass("govuk-heading-m").text().isEmpty

        val expectedAuditEvent: AuditEvent = AuditEvent.auditPTEnrolmentOnOtherAccount(
          accountDetailsUserFriendly(CREDENTIAL_ID_2)
        )(requestWithGivenMongoData(requestWithAccountType(PT_ASSIGNED_TO_OTHER_USER)), messagesApi)

        verifyAuditEventSent(expectedAuditEvent)

      }
    }

    List(
      PT_ASSIGNED_TO_CURRENT_USER,
      SINGLE_OR_MULTIPLE_ACCOUNTS,
      SA_ASSIGNED_TO_OTHER_USER,
      SA_ASSIGNED_TO_CURRENT_USER
    ).foreach { accountType =>
      s"the session cache has a credential with account type ${accountType.toString}" should {
        s"redirect to /protect-tax-info" in new DataAndMockSetup {
          saveDataToCache(accountType = accountType, optSAEnrolledCredential = Some(CREDENTIAL_ID_3))
          stubAuthoriseSuccess()

          val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest(GET, "/protect-tax-info" + urlPath)
            .withSession(xAuthToken, xSessionId)
          val result: Future[Result] = route(app, request).get

          status(result) shouldBe SEE_OTHER
          redirectLocation(result).get should include(
            accountCheckPath
          )

        }
      }
    }

    s"the session cache has a credential for PT enrolment that is the signed in account" should {
      s"render the error page" in new DataAndMockSetup {
        saveDataToCache(optPTEnrolledCredential = Some(CREDENTIAL_ID), optSAEnrolledCredential = None)
        stubAuthoriseSuccess()
        stubUserGroupSearchSuccess(CREDENTIAL_ID, usersGroupSearchResponse)

        val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest(GET, "/protect-tax-info" + urlPath)
          .withSession(xAuthToken, xSessionId)
        val result: Future[Result] = route(app, request).get

        status(result) shouldBe INTERNAL_SERVER_ERROR
        contentAsString(result) should include(ErrorTemplateMessages.title)
      }
    }

    s"the session cache has found $USER_ASSIGNED_PT_ENROLMENT" should {
      s"render the error page" in new DataAndMockSetup {
        saveDataToCache(optPTEnrolledCredential = None, optSAEnrolledCredential = None)
        stubAuthoriseSuccess()
        stubUserGroupSearchSuccess(CREDENTIAL_ID, usersGroupSearchResponse)

        val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest(GET, "/protect-tax-info" + urlPath)
          .withSession(xAuthToken, xSessionId)
        val result: Future[Result] = route(app, request).get

        status(result) shouldBe INTERNAL_SERVER_ERROR
        contentAsString(result) should include(ErrorTemplateMessages.title)
      }
    }

    s"the session cache has a PT enrolment but $USER_ASSIGNED_SA_ENROLMENT does not exist" should {
      s"render the error page" in new DataAndMockSetup {
        saveDataToCache(optPTEnrolledCredential = Some(CREDENTIAL_ID), optSAEnrolledCredential = None)
        stubAuthoriseSuccess()
        stubUserGroupSearchSuccess(CREDENTIAL_ID, usersGroupSearchResponse)

        val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest(GET, "/protect-tax-info" + urlPath)
          .withSession(xAuthToken, xSessionId)
        val result: Future[Result] = route(app, request).get

        status(result) shouldBe INTERNAL_SERVER_ERROR
        contentAsString(result) should include(ErrorTemplateMessages.title)
      }
    }

    "the session cache has no redirectUrl" should {
      "render the error page" in new DataAndMockSetup {

        val mockUserAnswers: UserAnswers = UserAnswers(sessionId, generateNino.nino)
          .setOrException(AccountTypePage, PT_ASSIGNED_TO_CURRENT_USER.toString)

        when(mockJourneyCacheRepository.get(any(), any()))
          .thenReturn(Future.successful(Some(mockUserAnswers)))

        stubAuthoriseSuccess()

        val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest(GET, "/protect-tax-info" + urlPath)
          .withSession(xAuthToken, xSessionId)
        val result: Future[Result] = route(app, request).get

        status(result) shouldBe INTERNAL_SERVER_ERROR
        contentAsString(result) should include(ErrorTemplateMessages.title)
      }
    }

    "users group search for current account in the session returns an error" should {
      "render the error page" in new DataAndMockSetup {
        saveDataToCache(optSAEnrolledCredential = Some(CREDENTIAL_ID_3))
        stubAuthoriseSuccess()
        stubUserGroupSearchFailure(CREDENTIAL_ID)

        val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest(GET, "/protect-tax-info" + urlPath)
          .withSession(xAuthToken, xSessionId)
        val result: Future[Result] = route(app, request).get

        status(result) shouldBe INTERNAL_SERVER_ERROR
        contentAsString(result) should include(ErrorTemplateMessages.title)
      }
    }

    "users group search returns for account with PT enrolment returns an error" should {
      "render the error page" in new DataAndMockSetup {
        saveDataToCache(optSAEnrolledCredential = Some(CREDENTIAL_ID_3))
        stubAuthoriseSuccess()
        stubUserGroupSearchSuccess(CREDENTIAL_ID, usersGroupSearchResponse)
        stubUserGroupSearchFailure(CREDENTIAL_ID_2)

        val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest(GET, "/protect-tax-info" + urlPath)
          .withSession(xAuthToken, xSessionId)
        val result: Future[Result] = route(app, request).get

        status(result) shouldBe INTERNAL_SERVER_ERROR
        contentAsString(result) should include(ErrorTemplateMessages.title)
      }
    }

    "users group search returns for account with SA enrolment returns an error" should {
      "render the error page" in new DataAndMockSetup {
        saveDataToCache(optSAEnrolledCredential = Some(CREDENTIAL_ID_3))
        stubAuthoriseSuccess()
        stubUserGroupSearchSuccess(CREDENTIAL_ID, usersGroupSearchResponse)
        stubUserGroupSearchSuccess(
          CREDENTIAL_ID_2,
          usersGroupSearchResponsePTEnrolment()
        )
        stubUserGroupSearchFailure(CREDENTIAL_ID)

        val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest(GET, "/protect-tax-info" + urlPath)
          .withSession(xAuthToken, xSessionId)
        val result: Future[Result] = route(app, request).get

        status(result) shouldBe INTERNAL_SERVER_ERROR
        contentAsString(result) should include(ErrorTemplateMessages.title)
      }
    }

    "the user has a session missing required element NINO" should {
      s"redirect to ${ItUrlPaths.unauthorizedPath}" in new DataAndMockSetup {
        stubUnAuthorised(hasNino = false)

        val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest(GET, "/protect-tax-info" + urlPath)
          .withSession(xAuthToken, xSessionId)
        val result: Future[Result] = route(app, request).get

        status(result) shouldBe SEE_OTHER
        redirectLocation(result).get should include(ItUrlPaths.unauthorizedPath)
      }
    }

    "the user has a session missing required element Credentials" should {
      s"redirect to ${ItUrlPaths.unauthorizedPath}" in new DataAndMockSetup {
        stubUnAuthorised(hasCred = false)

        val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest(GET, "/protect-tax-info" + urlPath)
          .withSession(xAuthToken, xSessionId)
        val result: Future[Result] = route(app, request).get

        status(result) shouldBe SEE_OTHER
        redirectLocation(result).get should include(ItUrlPaths.unauthorizedPath)
      }
    }

    "the user has a insufficient confidence level" should {
      s"redirect to ${ItUrlPaths.unauthorizedPath}" in new DataAndMockSetup {
        stubUnAuthorised(unauthorisedError = Some(insufficientConfidenceLevel))

        val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest(GET, "/protect-tax-info" + urlPath)
          .withSession(xAuthToken, xSessionId)
        val result: Future[Result] = route(app, request).get

        status(result) shouldBe SEE_OTHER
        redirectLocation(result).get should include(ItUrlPaths.unauthorizedPath)
      }
    }

    "the user has no active session" should {
      s"redirect to login" in new DataAndMockSetup {
        stubUnAuthorised(unauthorisedError = Some(sessionNotFound))

        val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest(GET, "/protect-tax-info" + urlPath)
          .withSession(xAuthToken, xSessionId)
        val result: Future[Result] = route(app, request).get

        status(result) shouldBe SEE_OTHER
        redirectLocation(result).get should include("/bas-gateway/sign-in")
      }
    }
  }

  class DataAndMockSetup {
    stubPost(s"/write/.*", OK, """{"x":2}""")

    def saveDataToCache(
      accountType: AccountTypes.Value = PT_ASSIGNED_TO_OTHER_USER,
      optPTEnrolledCredential: Option[String] = Some(CREDENTIAL_ID_2),
      optSAEnrolledCredential: Option[String]
    ): OngoingStubbing[Future[Option[UserAnswers]]] = {

      val mockUserAnswers = UserAnswers(xSessionId._2, generateNino.nino)
        .setOrException(AccountTypePage, accountType.toString)
        .setOrException(RedirectUrlPage, returnUrl)
        .setOrException(UserAssignedSaEnrolmentPage, UsersAssignedEnrolment(optSAEnrolledCredential))
        .setOrException(UserAssignedPtaEnrolmentPage, UsersAssignedEnrolment(optPTEnrolledCredential))

      when(mockJourneyCacheRepository.get(any(), any()))
        .thenReturn(Future.successful(Some(mockUserAnswers)))
    }

    def stubAuthoriseSuccess(hasSAEnrolment: Boolean = false): StubMapping = {
      val authResponse = authoriseResponseJson(
        enrolments = if (hasSAEnrolment) { saEnrolmentOnly }
        else { noEnrolments }
      )
      stubAuthorizePost(OK, authResponse.toString())
    }

    def stubUnAuthorised(
      hasNino: Boolean = true,
      hasCred: Boolean = true,
      unauthorisedError: Option[String] = None
    ): StubMapping =
      unauthorisedError match {
        case Some(error) => stubAuthorizePostUnauthorised(error)
        case None =>
          val authResponse =
            authoriseResponseJson(
              optNino = if (hasNino) { Some(NINO.nino) }
              else {
                None
              },
              optCreds = if (hasCred) { Some(creds) }
              else { None }
            )
          stubAuthorizePost(OK, authResponse.toString())
      }
  }
}
