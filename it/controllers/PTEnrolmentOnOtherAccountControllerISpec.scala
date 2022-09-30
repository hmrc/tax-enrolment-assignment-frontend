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

import com.github.tomakehurst.wiremock.stubbing.StubMapping
import helpers.{TestHelper, ThrottleHelperISpec}
import helpers.TestITData._
import helpers.WiremockHelper._
import helpers.messages._
import org.jsoup.Jsoup
import play.api.http.Status
import play.api.libs.json.{JsString, Json}
import play.api.libs.ws.DefaultWSCookie
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes.{MULTIPLE_ACCOUNTS, PT_ASSIGNED_TO_CURRENT_USER, PT_ASSIGNED_TO_OTHER_USER, SA_ASSIGNED_TO_CURRENT_USER, SA_ASSIGNED_TO_OTHER_USER, SINGLE_ACCOUNT}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.{UsersAssignedEnrolment, UsersGroupResponse}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.reporting.AuditEvent
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.SessionKeys.{USER_ASSIGNED_PT_ENROLMENT, USER_ASSIGNED_SA_ENROLMENT}

import java.util.UUID
import scala.collection.JavaConverters._

class PTEnrolmentOnOtherAccountControllerISpec extends TestHelper with Status with ThrottleHelperISpec {

  val urlPath: String = UrlPaths.ptOnOtherAccountPath

  s"GET $urlPath" when {

    throttleSpecificTests(() => buildRequest(urlPath)
      .addCookies(DefaultWSCookie("mdtp", authAndSessionCookie))
      .withHttpHeaders(xSessionId, xRequestId, sessionCookie)
      .get())

    "the signed in user has SA enrolment in session and PT enrolment on another account" should {
      s"render the pt on another account page" in new DataAndMockSetup {
        saveDataToCache(optSAEnrolledCredential = None)
        stubAuthoriseSuccess(true)
        stubUserGroupSearchSuccess(CREDENTIAL_ID, usersGroupSearchResponse)
        stubUserGroupSearchSuccess(
          CREDENTIAL_ID_2,
          usersGroupSearchResponsePTEnrolment(USER_ID)
        )

        val res = buildRequest(urlPath, followRedirects = true)
          .addCookies(DefaultWSCookie("mdtp", sessionAndAuthForTestForTest))
          .withHttpHeaders(xSessionID, xRequestId, sessionCookie)
          .get()

        whenReady(res) { resp =>
          val page = Jsoup.parse(resp.body)

          resp.status shouldBe OK
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

          val expectedAuditEvent = AuditEvent.auditPTEnrolmentOnOtherAccount(
            accountDetailsUserFriendly(CREDENTIAL_ID_2)
          )(requestWithAccountType(PT_ASSIGNED_TO_OTHER_USER), messagesApi)

          verifyAuditEventSent(expectedAuditEvent)

        }
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

        val res = buildRequest(urlPath, followRedirects = true)
          .addCookies(DefaultWSCookie("mdtp", sessionAndAuthForTestForTest))
          .withHttpHeaders(xSessionID, xRequestId, sessionCookie)
          .get()

        whenReady(res) { resp =>
          val page = Jsoup.parse(resp.body)

          resp.status shouldBe OK
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

          val expectedAuditEvent = AuditEvent.auditPTEnrolmentOnOtherAccount(
            accountDetailsUserFriendly(CREDENTIAL_ID_2, "1234")
          )(requestWithAccountType(PT_ASSIGNED_TO_OTHER_USER), messagesApi)

          verifyAuditEventSent(expectedAuditEvent)

        }
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

        val res = buildRequest(urlPath, followRedirects = true)
          .addCookies(DefaultWSCookie("mdtp", sessionAndAuthForTestForTest))
          .withHttpHeaders(xSessionID, xRequestId, sessionCookie)
          .get()

        whenReady(res) { resp =>
          val page = Jsoup.parse(resp.body)

          resp.status shouldBe OK
          page.title should include(PTEnrolmentOtherAccountMesages.title)
          page
            .getElementsByClass("govuk-heading-m")
            .text() shouldBe PTEnrolmentOtherAccountMesages.saHeading
          page
            .getElementsByClass("govuk-body")
            .asScala
            .toList
            .map(_.text()) should contain(PTEnrolmentOtherAccountMesages.saText)

          val expectedAuditEvent = AuditEvent.auditPTEnrolmentOnOtherAccount(
            accountDetailsUserFriendly(CREDENTIAL_ID_2)
          )(requestWithAccountType(PT_ASSIGNED_TO_OTHER_USER), messagesApi)

          verifyAuditEventSent(expectedAuditEvent)

        }
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

        val res = buildRequest(urlPath, followRedirects = true)
          .addCookies(DefaultWSCookie("mdtp", sessionAndAuthForTestForTest))
          .addHttpHeaders(xSessionID, xRequestId, sessionCookie)
          .get()

        whenReady(res) { resp =>
          val page = Jsoup.parse(resp.body)

          resp.status shouldBe OK
          page.title should include(PTEnrolmentOtherAccountMesages.title)
          page.getElementsByClass("govuk-heading-m").text().isEmpty

          val expectedAuditEvent = AuditEvent.auditPTEnrolmentOnOtherAccount(
            accountDetailsUserFriendly(CREDENTIAL_ID_2)
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
        s"redirect to ${UrlPaths.accountCheckPath}" in new DataAndMockSetup {
          saveDataToCache(accountType = accountType, optSAEnrolledCredential = Some(CREDENTIAL_ID_3))
          stubAuthoriseSuccess()

          val res = buildRequest(urlPath, followRedirects = false)
            .addCookies(DefaultWSCookie("mdtp", sessionAndAuthForTestForTest))
            .addHttpHeaders(xSessionID, xRequestId, sessionCookie)
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
      s"render the error page" in new DataAndMockSetup {
        saveDataToCache(optPTEnrolledCredential = Some(CREDENTIAL_ID), optSAEnrolledCredential = None)
        stubAuthoriseSuccess()
        stubUserGroupSearchSuccess(CREDENTIAL_ID, usersGroupSearchResponse)

        val res = buildRequest(urlPath, followRedirects = false)
          .addCookies(DefaultWSCookie("mdtp", sessionAndAuthForTestForTest))
          .addHttpHeaders(xSessionID, xRequestId, sessionCookie)
          .get()

        whenReady(res) { resp =>
          resp.status shouldBe INTERNAL_SERVER_ERROR
          resp.body should include(ErrorTemplateMessages.title)
        }
      }
    }

    s"the session cache has found $USER_ASSIGNED_PT_ENROLMENT" should {
      s"render the error page" in new DataAndMockSetup {
        saveDataToCache(optPTEnrolledCredential = None,optSAEnrolledCredential = None)
        stubAuthoriseSuccess()
        stubUserGroupSearchSuccess(CREDENTIAL_ID, usersGroupSearchResponse)

        val res = buildRequest(urlPath, followRedirects = false)
          .addCookies(DefaultWSCookie("mdtp", sessionAndAuthForTestForTest))
          .addHttpHeaders(xSessionID, xRequestId, sessionCookie)
          .get()

        whenReady(res) { resp =>
          resp.status shouldBe INTERNAL_SERVER_ERROR
          resp.body should include(ErrorTemplateMessages.title)
        }
      }
    }

    s"the session cache has a PT enrolment but $USER_ASSIGNED_SA_ENROLMENT does not exist" should {
      s"render the error page" in new DataAndMockSetup {
        saveDataToCache(optPTEnrolledCredential = Some(CREDENTIAL_ID),optSAEnrolledCredential = None)
        stubAuthoriseSuccess()
        stubUserGroupSearchSuccess(CREDENTIAL_ID, usersGroupSearchResponse)

        val res = buildRequest(urlPath, followRedirects = false)
          .addCookies(DefaultWSCookie("mdtp", sessionAndAuthForTestForTest))
          .addHttpHeaders(xSessionID, xRequestId, sessionCookie)
          .get()

        whenReady(res) { resp =>
          resp.status shouldBe INTERNAL_SERVER_ERROR
          resp.body should include(ErrorTemplateMessages.title)
        }
      }
    }

    "the session cache has no redirectUrl" should {
      "render the error page" in new DataAndMockSetup {
        stubAuthoriseSuccess()
        val res = buildRequest(urlPath, followRedirects = true)
          .addCookies(DefaultWSCookie("mdtp", sessionAndAuthForTestForTest))
          .addHttpHeaders(xSessionID, xRequestId, sessionCookie)
          .get()

        whenReady(res) { resp =>
          resp.status shouldBe INTERNAL_SERVER_ERROR
          resp.body should include(ErrorTemplateMessages.title)
        }
      }
    }

    "users group search for current account in the session returns an error" should {
      "render the error page" in new DataAndMockSetup {
        saveDataToCache(optSAEnrolledCredential = Some(CREDENTIAL_ID_3))
        stubAuthoriseSuccess()
        stubUserGroupSearchFailure(CREDENTIAL_ID)

        val res = buildRequest(urlPath, followRedirects = true)
          .addCookies(DefaultWSCookie("mdtp", sessionAndAuthForTestForTest))
          .addHttpHeaders(xSessionID, xRequestId, sessionCookie)
          .get()

        whenReady(res) { resp =>
          resp.status shouldBe INTERNAL_SERVER_ERROR
          resp.body should include(ErrorTemplateMessages.title)
        }
      }
    }

    "users group search returns for account with PT enrolment returns an error" should {
      "render the error page" in new DataAndMockSetup {
        saveDataToCache(optSAEnrolledCredential = Some(CREDENTIAL_ID_3))
        stubAuthoriseSuccess()
        stubUserGroupSearchSuccess(CREDENTIAL_ID, usersGroupSearchResponse)
        stubUserGroupSearchFailure(CREDENTIAL_ID_2)

        val res = buildRequest(urlPath, followRedirects = true)
          .addCookies(DefaultWSCookie("mdtp", sessionAndAuthForTestForTest))
          .addHttpHeaders(xSessionID, xRequestId, sessionCookie)
          .get()

        whenReady(res) { resp =>
          resp.status shouldBe INTERNAL_SERVER_ERROR
          resp.body should include(ErrorTemplateMessages.title)
        }
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

        val res = buildRequest(urlPath, followRedirects = true)
          .addCookies(DefaultWSCookie("mdtp", sessionAndAuthForTestForTest))
          .addHttpHeaders(xSessionID, xRequestId, sessionCookie)
          .get()

        whenReady(res) { resp =>
          resp.status shouldBe INTERNAL_SERVER_ERROR
          resp.body should include(ErrorTemplateMessages.title)
        }
      }
    }

    "the user has a session missing required element NINO" should {
      s"redirect to ${UrlPaths.unauthorizedPath}" in new DataAndMockSetup {
        stubUnAuthorised(hasNino = false)

        val res =
          buildRequest(urlPath)
            .addCookies(DefaultWSCookie("mdtp", sessionAndAuthForTestForTest))
            .addHttpHeaders(xSessionID, xRequestId, csrfContent, sessionCookie)
            .get()

        whenReady(res) { resp =>
          resp.status shouldBe SEE_OTHER
          resp.header("Location").get should include(UrlPaths.unauthorizedPath)
        }
      }
    }

    "the user has a session missing required element Credentials" should {
      s"redirect to ${UrlPaths.unauthorizedPath}" in new DataAndMockSetup {
        stubUnAuthorised(hasCred = false)

        val res =
          buildRequest(urlPath)
            .addCookies(DefaultWSCookie("mdtp", sessionAndAuthForTestForTest))
            .addHttpHeaders(xSessionID, xRequestId, csrfContent, sessionCookie)
            .get()

        whenReady(res) { resp =>
          resp.status shouldBe SEE_OTHER
          resp.header("Location").get should include(UrlPaths.unauthorizedPath)
        }
      }
    }

    "the user has a insufficient confidence level" should {
      s"redirect to ${UrlPaths.unauthorizedPath}" in new DataAndMockSetup {
        stubUnAuthorised(unauthorisedError = Some(insufficientConfidenceLevel))

        val res =
          buildRequest(urlPath)
            .addCookies(DefaultWSCookie("mdtp", sessionAndAuthForTestForTest))
            .addHttpHeaders(xSessionID, xRequestId, csrfContent, sessionCookie)
            .get()

        whenReady(res) { resp =>
          resp.status shouldBe SEE_OTHER
          resp.header("Location").get should include(UrlPaths.unauthorizedPath)
        }
      }
    }

    "the user has no active session" should {
      s"redirect to login" in new DataAndMockSetup {
        stubUnAuthorised(unauthorisedError = Some(sessionNotFound))

        val res = buildRequest(urlPath)
          .addCookies(DefaultWSCookie("mdtp", sessionAndAuthForTestForTest))
          .addHttpHeaders(xSessionID, xRequestId, csrfContent)
          .get()

        whenReady(res) { resp =>
          resp.status shouldBe SEE_OTHER
          resp.header("Location").get should include("/bas-gateway/sign-in")
        }
      }
    }
  }


  class DataAndMockSetup {

    val sessionID = UUID.randomUUID().toString
    val xSessionID: (String, String) = "X-Session-ID" -> sessionID
    val xRequestID: (String, String) = "X-Request-ID" -> sessionID
    val sessionDataForTest = Map("sessionId" -> sessionID)
    val newSessionCookie
    : (String, String) = ("COOKIE" -> createSessionCookieAsString(
      sessionDataForTest
    ))
    val sessionAndAuthForTest =
      Map("authToken" -> AUTHORIZE_HEADER_VALUE, "sessionId" -> sessionID)

    val sessionAndAuthForTestForTest: String =
      createSessionCookieAsString(sessionAndAuthForTest).substring(5)

    stubPost(s"/write/.*", OK, """{"x":2}""")

    def saveDataToCache(
                         accountType: AccountTypes.Value = PT_ASSIGNED_TO_OTHER_USER,
                         optPTEnrolledCredential: Option[String] = Some(CREDENTIAL_ID_2),
                         optSAEnrolledCredential: Option[String]
                       ): Boolean = {
      val dataMap = Map(
        "redirectURL" -> JsString(UrlPaths.returnUrl),
        "ACCOUNT_TYPE" -> JsString(accountType.toString),
        USER_ASSIGNED_PT_ENROLMENT -> Json.toJson(
          UsersAssignedEnrolment(optPTEnrolledCredential)
        ),
        USER_ASSIGNED_SA_ENROLMENT -> Json.toJson(
          UsersAssignedEnrolment(optSAEnrolledCredential)
        )
      )
      await(save(sessionID, dataMap))
    }

    def stubAuthoriseSuccess(hasSAEnrolment: Boolean = false): StubMapping = {
      val authResponse = authoriseResponseJson(
        enrolments = if (hasSAEnrolment) { saEnrolmentOnly } else noEnrolments
      )
      stubAuthorizePost(OK, authResponse.toString())
    }

    def stubUnAuthorised(
                          hasNino: Boolean = true,
                          hasCred: Boolean = true,
                          unauthorisedError: Option[String] = None
                        ): StubMapping = {
      unauthorisedError match {
        case Some(error) => stubAuthorizePostUnauthorised(error)
        case None =>
          val authResponse =
            authoriseResponseJson(optNino = if (hasNino) { Some(NINO) } else {
              None
            }, optCreds = if (hasCred) { Some(creds) } else None)
          stubAuthorizePost(OK, authResponse.toString())
      }
    }

    def stubUserGroupSearchSuccess(
                                    credId: String,
                                    usersGroupResponse: UsersGroupResponse
                                  ): StubMapping = stubGet(
      s"/users-groups-search/users/$credId",
      NON_AUTHORITATIVE_INFORMATION,
      usergroupsResponseJson(usersGroupResponse).toString()
    )

    def stubUserGroupSearchFailure(
                                    credId: String,
                                    responseCode: Int = INTERNAL_SERVER_ERROR
                                  ): StubMapping =
      stubGet(s"/users-groups-search/users/$credId", INTERNAL_SERVER_ERROR, "")
  }
}
