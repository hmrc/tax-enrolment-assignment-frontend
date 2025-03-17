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
import helpers.{IntegrationSpecBase, ItUrlPaths}
import helpers.TestITData._
import play.api.test.Helpers.{GET, await, contentAsString, defaultAwaitTimeout, redirectLocation, route}
import play.api.test.Helpers.{status, writeableOf_AnyContentAsEmpty}
import helpers.messages._
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import play.api.http.Status.{INTERNAL_SERVER_ERROR, OK, SEE_OTHER}
import play.api.libs.json.{JsString, Json}
import play.api.mvc.{AnyContentAsEmpty, Result}
import play.api.test.FakeRequest
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes.{PT_ASSIGNED_TO_CURRENT_USER, PT_ASSIGNED_TO_OTHER_USER, SA_ASSIGNED_TO_CURRENT_USER, SA_ASSIGNED_TO_OTHER_USER, SINGLE_OR_MULTIPLE_ACCOUNTS}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.UsersAssignedEnrolment
import uk.gov.hmrc.taxenrolmentassignmentfrontend.reporting.AuditEvent
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.SessionKeys.{USER_ASSIGNED_PT_ENROLMENT, USER_ASSIGNED_SA_ENROLMENT}

import scala.concurrent.Future
import scala.jdk.CollectionConverters._

class PTEnrolmentOnOtherAccountControllerISpec extends IntegrationSpecBase {

  val urlPath: String = ItUrlPaths.ptOnOtherAccountPath

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
          .get(0)
          .text() shouldBe PTEnrolmentOtherAccountMesages.saHeading
        page
          .getElementsByClass("govuk-body")
          .get(3)
          .text() should equal(
          PTEnrolmentOtherAccountMesages.saText3
        )

        val expectedAuditEvent: AuditEvent = AuditEvent.auditPTEnrolmentOnOtherAccount(
          accountDetailsUserFriendly(CREDENTIAL_ID_2)
        )(requestWithAccountType(PT_ASSIGNED_TO_OTHER_USER), messagesApi)

        verifyAuditEventSent(expectedAuditEvent)
      }
    }

//    "the signed in user has SA enrolment and a PT enrolment on another account" should {
//      s"render the pt on another account page" in new DataAndMockSetup {
//        saveDataToCache(optSAEnrolledCredential = None)
//        stubAuthoriseSuccess(true)
//        stubUserGroupSearchSuccess(CREDENTIAL_ID, usersGroupSearchResponse)
//        stubUserGroupSearchSuccess(
//          CREDENTIAL_ID_2,
//          usersGroupSearchResponsePTEnrolment(USER_ID)
//        )
//
//        val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest(GET, "/protect-tax-info" + urlPath)
//          .withSession(xAuthToken, xSessionId)
//        val result: Future[Result] = route(app, request).get
//        val page: Document = Jsoup.parse(contentAsString(result))
//
//        status(result) shouldBe OK
//        page.title should include(PTEnrolmentOtherAccountMesages.title)
//        page
//          .getElementsByClass("govuk-heading-m")
//          .text() shouldBe PTEnrolmentOtherAccountMesages.saHeading
//
//        page
//          .getElementsByClass("govuk-body")
//          .asScala
//          .toList
//          .map(_.text()) should contain(
//          PTEnrolmentOtherAccountMesages.saText2
//        )
//
//        val expectedAuditEvent: AuditEvent = AuditEvent.auditPTEnrolmentOnOtherAccount(
//          accountDetailsUserFriendly(CREDENTIAL_ID_2, "1234")
//        )(requestWithAccountType(PT_ASSIGNED_TO_OTHER_USER), messagesApi)
//
//        verifyAuditEventSent(expectedAuditEvent)
//
//      }
//    }

//    "the user signed in has SA enrolment and PT enrolment on two other separate accounts" should {
//      s"render the pt on another account page" in new DataAndMockSetup {
//        saveDataToCache(optSAEnrolledCredential = Some(CREDENTIAL_ID_3))
//        stubAuthoriseSuccess()
//        stubUserGroupSearchSuccess(CREDENTIAL_ID, usersGroupSearchResponse)
//        stubUserGroupSearchSuccess(
//          CREDENTIAL_ID_2,
//          usersGroupSearchResponsePTEnrolment(USER_ID)
//        )
//        stubUserGroupSearchSuccess(
//          CREDENTIAL_ID_3,
//          usersGroupSearchResponseSAEnrolment
//        )
//
//        val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest(GET, "/protect-tax-info" + urlPath)
//          .withSession(xAuthToken, xSessionId)
//        val result: Future[Result] = route(app, request).get
//        val page: Document = Jsoup.parse(contentAsString(result))
//
//        status(result) shouldBe OK
//        page.title should include(PTEnrolmentOtherAccountMesages.title)
//        page
//          .getElementsByClass("govuk-heading-m")
//          .text() shouldBe PTEnrolmentOtherAccountMesages.saHeading
//        page
//          .getElementsByClass("govuk-body")
//          .asScala
//          .toList
//          .map(_.text()) should contain(PTEnrolmentOtherAccountMesages.saText)
//
//        val expectedAuditEvent: AuditEvent = AuditEvent.auditPTEnrolmentOnOtherAccount(
//          accountDetailsUserFriendly(CREDENTIAL_ID_2)
//        )(requestWithAccountType(PT_ASSIGNED_TO_OTHER_USER), messagesApi)
//
//        verifyAuditEventSent(expectedAuditEvent)
//
//      }
//    }
//
//    "the signed in user has no SA on any accounts but has PT enrolment on another account" should {
//      s"render the pt on another account page" in new DataAndMockSetup {
//        saveDataToCache(optSAEnrolledCredential = None)
//        stubAuthoriseSuccess()
//        stubUserGroupSearchSuccess(CREDENTIAL_ID, usersGroupSearchResponse)
//        stubUserGroupSearchSuccess(
//          CREDENTIAL_ID_2,
//          usersGroupSearchResponsePTEnrolment(USER_ID)
//        )
//
//        val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest(GET, "/protect-tax-info" + urlPath)
//          .withSession(xAuthToken, xSessionId)
//        val result: Future[Result] = route(app, request).get
//        val page: Document = Jsoup.parse(contentAsString(result))
//
//        status(result) shouldBe OK
//        page.title should include(PTEnrolmentOtherAccountMesages.title)
//        page.getElementsByClass("govuk-heading-m").text().isEmpty
//
//        val expectedAuditEvent: AuditEvent = AuditEvent.auditPTEnrolmentOnOtherAccount(
//          accountDetailsUserFriendly(CREDENTIAL_ID_2)
//        )(requestWithAccountType(PT_ASSIGNED_TO_OTHER_USER), messagesApi)
//
//        verifyAuditEventSent(expectedAuditEvent)
//
//      }
//    }

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
        await(save[AccountTypes.Value](xSessionId._2, "ACCOUNT_TYPE", PT_ASSIGNED_TO_CURRENT_USER))
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
    ): Boolean = {
      val dataMap = Map(
        "redirectURL"  -> JsString(returnUrl),
        "ACCOUNT_TYPE" -> JsString(accountType.toString),
        USER_ASSIGNED_PT_ENROLMENT -> Json.toJson(
          UsersAssignedEnrolment(optPTEnrolledCredential)
        ),
        USER_ASSIGNED_SA_ENROLMENT -> Json.toJson(
          UsersAssignedEnrolment(optSAEnrolledCredential)
        )
      )
      await(save(xSessionId._2, dataMap))
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
