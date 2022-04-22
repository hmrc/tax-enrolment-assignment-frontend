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
import helpers.TestHelper
import helpers.TestITData._
import helpers.WiremockHelper._
import helpers.messages._
import org.jsoup.Jsoup
import play.api.http.Status
import play.api.libs.ws.DefaultWSCookie
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes.{
  MULTIPLE_ACCOUNTS,
  PT_ASSIGNED_TO_CURRENT_USER,
  PT_ASSIGNED_TO_OTHER_USER,
  SA_ASSIGNED_TO_CURRENT_USER,
  SA_ASSIGNED_TO_OTHER_USER,
  SINGLE_ACCOUNT
}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.{
  UsersAssignedEnrolment,
  UsersGroupResponse
}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.SessionKeys.{
  USER_ASSIGNED_PT_ENROLMENT,
  USER_ASSIGNED_SA_ENROLMENT
}
import scala.collection.JavaConverters._

class PTEnrolmentOnOtherAccountControllerISpec extends TestHelper with Status {

  val urlPath: String = UrlPaths.ptOnOtherAccountPath

  s"GET $urlPath" when {
    "the signed in user has SA enrolment in session and PT enrolment on another account" should {
      s"render the pt on another account page" in new DataAndMockSetup {
        saveRedirectUrlToCache
        saveAccountTypeToCache()
        savePTEnrolmentCredentialToCache()
        stubAuthoriseSuccess(true)
        stubUserGroupSearchSuccess(CREDENTIAL_ID, usersGroupSearchResponse)
        stubUserGroupSearchSuccess(
          CREDENTIAL_ID_2,
          usersGroupSearchResponsePTEnrolment
        )

        val res = buildRequest(urlPath, followRedirects = true)
          .addCookies(DefaultWSCookie("mdtp", authAndSessionCookie))
          .withHttpHeaders(xSessionId, xRequestId, sessionCookie)
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
        }
      }
    }

    "the signed in user has SA enrolment and a PT enrolment on another account" should {
      s"render the pt on another account page" in new DataAndMockSetup {
        saveRedirectUrlToCache
        saveAccountTypeToCache()
        savePTEnrolmentCredentialToCache()
        saveSAEnrolmentCredentialToCache(Some(CREDENTIAL_ID_2))
        stubAuthoriseSuccess()
        stubUserGroupSearchSuccess(CREDENTIAL_ID, usersGroupSearchResponse)
        stubUserGroupSearchSuccess(
          CREDENTIAL_ID_2,
          usersGroupSearchResponsePTEnrolment
        )

        val res = buildRequest(urlPath, followRedirects = true)
          .addCookies(DefaultWSCookie("mdtp", authAndSessionCookie))
          .withHttpHeaders(xSessionId, xRequestId, sessionCookie)
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
        }
      }
    }

    "the user signed in has SA enrolment and PT enrolment on two other seperate accounts" should {
      s"render the pt on another account page" in new DataAndMockSetup {
        saveRedirectUrlToCache
        saveAccountTypeToCache()
        savePTEnrolmentCredentialToCache()
        saveSAEnrolmentCredentialToCache(Some(CREDENTIAL_ID_3))
        stubAuthoriseSuccess()
        stubUserGroupSearchSuccess(CREDENTIAL_ID, usersGroupSearchResponse)
        stubUserGroupSearchSuccess(
          CREDENTIAL_ID_2,
          usersGroupSearchResponsePTEnrolment
        )
        stubUserGroupSearchSuccess(
          CREDENTIAL_ID_3,
          usersGroupSearchResponseSAEnrolment
        )

        val res = buildRequest(urlPath, followRedirects = true)
          .addCookies(DefaultWSCookie("mdtp", authAndSessionCookie))
          .withHttpHeaders(xSessionId, xRequestId, sessionCookie)
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
        }
      }
    }

    "the signed in user has no SA on any accounts but has PT enrolment on another account" should {
      s"render the pt on another account page" in new DataAndMockSetup {
        saveRedirectUrlToCache
        saveAccountTypeToCache()
        savePTEnrolmentCredentialToCache()
        saveSAEnrolmentCredentialToCache(None)
        stubAuthoriseSuccess()
        stubUserGroupSearchSuccess(CREDENTIAL_ID, usersGroupSearchResponse)
        stubUserGroupSearchSuccess(
          CREDENTIAL_ID_2,
          usersGroupSearchResponsePTEnrolment
        )

        val res = buildRequest(urlPath, followRedirects = true)
          .addCookies(DefaultWSCookie("mdtp", authAndSessionCookie))
          .addHttpHeaders(xSessionId, xRequestId, sessionCookie)
          .get()

        whenReady(res) { resp =>
          val page = Jsoup.parse(resp.body)

          resp.status shouldBe OK
          page.title should include(PTEnrolmentOtherAccountMesages.title)
          page.getElementsByClass("govuk-heading-m").text().isEmpty
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
          saveRedirectUrlToCache
          saveAccountTypeToCache(accountType)
          stubAuthoriseSuccess()

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

    s"the session cache has a credential for PT enrolment that is the signed in account" should {
      s"render the error page" in new DataAndMockSetup {
        saveRedirectUrlToCache
        saveAccountTypeToCache()
        savePTEnrolmentCredentialToCache(Some(CREDENTIAL_ID))
        stubAuthoriseSuccess()
        stubUserGroupSearchSuccess(CREDENTIAL_ID, usersGroupSearchResponse)

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

    s"the session cache has no credentials with PT enrolment" should {
      s"render the error page" in new DataAndMockSetup {
        saveRedirectUrlToCache
        saveAccountTypeToCache()
        savePTEnrolmentCredentialToCache(None)
        stubAuthoriseSuccess()
        stubUserGroupSearchSuccess(CREDENTIAL_ID, usersGroupSearchResponse)

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
      "render the error page" in new DataAndMockSetup {
        stubAuthoriseSuccess()
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

    "users group search returns an error" should {
      "render the error page" in new DataAndMockSetup {
        saveRedirectUrlToCache
        saveAccountTypeToCache()
        savePTEnrolmentCredentialToCache()
        saveSAEnrolmentCredentialToCache(Some(CREDENTIAL_ID_3))
        stubAuthoriseSuccess()
        stubUserGroupSearchFailure(CREDENTIAL_ID)

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
      s"redirect to ${UrlPaths.unauthorizedPath}" in new DataAndMockSetup {
        stubUnAuthorised(hasNino = false)

        val res =
          buildRequest(urlPath)
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
      s"redirect to ${UrlPaths.unauthorizedPath}" in new DataAndMockSetup {
        stubUnAuthorised(hasCred = false)

        val res =
          buildRequest(urlPath)
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
      s"redirect to ${UrlPaths.unauthorizedPath}" in new DataAndMockSetup {
        stubUnAuthorised(unauthorisedError = Some(insufficientConfidenceLevel))

        val res =
          buildRequest(urlPath)
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
      s"redirect to login" in new DataAndMockSetup {
        stubUnAuthorised(unauthorisedError = Some(sessionNotFound))

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

  class DataAndMockSetup {

    stubPost(s"/write/.*", OK, """{"x":2}""")

    lazy val saveRedirectUrlToCache = await(
      save[String](sessionId, "redirectURL", UrlPaths.returnUrl)
    )
    def saveAccountTypeToCache(
      accountType: AccountTypes.Value = PT_ASSIGNED_TO_OTHER_USER
    ): CacheMap = await(
      save[AccountTypes.Value](sessionId, "ACCOUNT_TYPE", accountType)
    )
    def savePTEnrolmentCredentialToCache(
      optCredId: Option[String] = Some(CREDENTIAL_ID_2)
    ): CacheMap = await(
      save[UsersAssignedEnrolment](
        sessionId,
        USER_ASSIGNED_PT_ENROLMENT,
        UsersAssignedEnrolment(optCredId)
      )
    )

    def saveSAEnrolmentCredentialToCache(optCredId: Option[String]): CacheMap =
      await(
        save[UsersAssignedEnrolment](
          sessionId,
          USER_ASSIGNED_SA_ENROLMENT,
          UsersAssignedEnrolment(optCredId)
        )
      )

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
