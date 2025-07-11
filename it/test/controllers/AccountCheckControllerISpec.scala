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

import com.github.tomakehurst.wiremock.client.WireMock.{deleteRequestedFor, urlEqualTo, urlMatching}
import helpers.TestITData.*
import helpers.messages.*
import helpers.{IntegrationSpecBase, ItUrlPaths}
import play.api.http.Status
import play.api.http.Status.{BAD_REQUEST, INTERNAL_SERVER_ERROR, NON_AUTHORITATIVE_INFORMATION, NO_CONTENT, OK, SEE_OTHER}
import play.api.libs.json.Json
import play.api.mvc.{AnyContent, Request}
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, contentAsString, defaultAwaitTimeout, redirectLocation, route, status, writeableOf_AnyContentAsEmpty}
import uk.gov.hmrc.auth.core.{Enrolment, EnrolmentIdentifier}
import uk.gov.hmrc.crypto.{Decrypter, Encrypter}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes.{MULTIPLE_ACCOUNTS, SA_ASSIGNED_TO_CURRENT_USER, SINGLE_ACCOUNT}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.actions.*
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.*
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.enums.EnrolmentEnum.hmrcPTKey
import uk.gov.hmrc.taxenrolmentassignmentfrontend.reporting.AuditEvent
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.SessionKeys.*

import java.net.URLEncoder

class AccountCheckControllerISpec extends IntegrationSpecBase {

  lazy val urlPath: String              = accountCheckPath
  val newEnrolment: String => Enrolment = (nino: String) =>
    Enrolment(s"$hmrcPTKey", Seq(EnrolmentIdentifier("NINO", nino)), "Activated", None)

  def requestWithUserDetails(
    userDetails: UserDetailsFromSession = userDetailsNoEnrolments
  ): RequestWithUserDetailsFromSession[_] =
    RequestWithUserDetailsFromSession(
      FakeRequest().asInstanceOf[Request[AnyContent]],
      userDetails,
      sessionId
    )

  def requestWithMongoUserDetails(
    userDetails: UserDetailsFromSession = userDetailsNoEnrolments,
    accountType: AccountTypes.Value = AccountTypes.SINGLE_ACCOUNT,
    redirectUrl: String = "/redirect-url",
    credId: String = "someCredId",
    identityProvider: IdentityProviderType = SCP,
    sessionId: String = "sessionId"
  ): RequestWithUserDetailsFromSessionAndMongo[AnyContent] = {

    val accountDetails = AccountDetails(
      identityProviderType = identityProvider,
      credId = credId,
      userId = "userId123",
      email = None,
      lastLoginDate = None,
      mfaDetails = Seq.empty,
      hasSA = Some(false)
    )

    val sessionData = Map(
      ACCOUNT_TYPE                        -> Json.toJson(accountType),
      REDIRECT_URL                        -> Json.toJson(redirectUrl),
      accountDetailsForCredential(credId) -> Json.toJson(accountDetails)
    )

    val accountDetailsFromMongo = AccountDetailsFromMongo(
      accountType = accountType,
      redirectUrl = redirectUrl,
      sessionData = sessionData
    )

    RequestWithUserDetailsFromSessionAndMongo(
      request = FakeRequest().asInstanceOf[Request[AnyContent]],
      userDetails = userDetails,
      sessionID = sessionId,
      accountDetailsFromMongo = accountDetailsFromMongo(crypto.crypto)
    )
  }

  override def beforeEach(): Unit = {
    super.beforeEach()
    stubGetMatching("/users-groups-search/users/.*", Status.OK, usergroupsResponseJson().toString())
    stubGet(
      s"/users-groups-search/users/nino/$NINO/credIds",
      OK,
      userGroupSearchCredIdsResponseOneId
    )
    stubGet(
      s"/users-groups-search/users/$CREDENTIAL_ID",
      NON_AUTHORITATIVE_INFORMATION,
      usergroupsResponseJson().toString()
    )
  }

  s"redirect to {returnUrl}" when {
    "a user has one credential associated with their nino" that {
      "has a PT enrolment in the session" in {
        val authResponse = authoriseResponseJson(enrolments = ptEnrolmentOnly)
        stubAuthorizePost(OK, authResponse.toString())
        stubPost(s"/write/.*", OK, """{"x":2}""")
        stubGet(
          s"/enrolment-store-proxy/enrolment-store/enrolments/HMRC-PT~NINO~$NINO/users",
          OK,
          es0ResponseMatchingCred
        )
        stubPost(
          s"/enrolment-store-proxy/enrolment-store/enrolments",
          NO_CONTENT,
          ""
        )

        val request = FakeRequest(GET, urlPath)
          .withSession(xAuthToken)
        val result  = route(app, request).get

        status(result)             shouldBe SEE_OTHER
        redirectLocation(result).get should include(returnUrl)
      }

      "has PT enrolment in EACD but not the session" in {
        val authResponse = authoriseResponseJson()
        stubAuthorizePost(OK, authResponse.toString())
        stubPost(s"/write/.*", OK, """{"x":2}""")
        stubGet(
          s"/enrolment-store-proxy/enrolment-store/enrolments/HMRC-PT~NINO~$NINO/users",
          OK,
          es0ResponseMatchingCred
        )
        stubPost(
          s"/enrolment-store-proxy/enrolment-store/enrolments",
          NO_CONTENT,
          ""
        )
        stubPut(
          s"/tax-enrolments/service/HMRC-PT/enrolment",
          NO_CONTENT,
          ""
        )

        val request = FakeRequest(GET, urlPath)
          .withSession(xAuthToken)
        val result  = route(app, request).get

        status(result)             shouldBe SEE_OTHER
        redirectLocation(result).get should include(returnUrl)
      }
    }
  }

  s"silently enrol for PT, check for and deallocate enrolment from groups, and redirect to users redirect url" when {
    "the user is a single account holder with no PT enrolment in session or EACD" in {
      val authResponse = authoriseResponseJson()
      stubAuthorizePost(OK, authResponse.toString())
      stubPost(s"/write/.*", OK, """{"x":2}""")
      stubGet(
        s"/enrolment-store-proxy/enrolment-store/enrolments/HMRC-PT~NINO~$NINO/users",
        OK,
        es0ResponseNoRecordCred
      )
      stubGetWithQueryParam(
        "/identity-verification/nino",
        "nino",
        NINO.nino,
        OK,
        ivResponseSingleCredsJsonString
      )

      stubPost(
        s"/enrolment-store-proxy/enrolment-store/enrolments",
        NO_CONTENT,
        ""
      )

      stubPut(
        s"/tax-enrolments/service/HMRC-PT/enrolment",
        NO_CONTENT,
        ""
      )

      stubGet(
        s"/enrolment-store-proxy/enrolment-store/enrolments/HMRC-PT~NINO~$NINO/groups",
        NO_CONTENT,
        ""
      )

      val request = FakeRequest(GET, urlPath)
        .withSession(xAuthToken)
      val result  = route(app, request).get

      status(result)             shouldBe SEE_OTHER
      redirectLocation(result).get should include(returnUrl)

      val expectedAuditEvent = AuditEvent.auditSuccessfullyEnrolledPTWhenSANotOnOtherAccount(
        SINGLE_ACCOUNT,
        accountDetailsOverride = Some(accountDetails)
      )(requestWithMongoUserDetails(), messagesApi)
      verifyAuditEventSent(expectedAuditEvent)

    }

    "the user is a single account holder with an invalid PT enrolment in session or EACD" in {
      val authResponse = authoriseResponseJson(enrolments = mismatchPtEnrolmentOnly)
      stubAuthorizePost(OK, authResponse.toString())
      stubPost(s"/write/.*", OK, """{"x":2}""")
      stubGet(
        s"/enrolment-store-proxy/enrolment-store/enrolments/HMRC-PT~NINO~${NINO.nino}/users",
        OK,
        es0ResponseNoRecordCred
      )
      stubGetWithQueryParam(
        "/identity-verification/nino",
        "nino",
        NINO.nino,
        OK,
        ivResponseSingleCredsJsonString
      )

      stubPost(
        s"/enrolment-store-proxy/enrolment-store/enrolments",
        NO_CONTENT,
        ""
      )

      stubPut(
        s"/tax-enrolments/service/HMRC-PT/enrolment",
        NO_CONTENT,
        ""
      )

      stubDelete(
        s"/tax-enrolments/groups/$GROUP_ID/enrolments/HMRC-PT~NINO~${mismatchNino.nino}",
        Status.CREATED
      )

      stubGet(
        s"/enrolment-store-proxy/enrolment-store/enrolments/HMRC-PT~NINO~$NINO/groups",
        NO_CONTENT,
        ""
      )

      val request = FakeRequest(GET, urlPath)
        .withSession(xAuthToken)
      val result  = route(app, request).get

      status(result)             shouldBe SEE_OTHER
      redirectLocation(result).get should include(returnUrl)

      val expectedAuditEvent = AuditEvent.auditSuccessfullyEnrolledPTWhenSANotOnOtherAccount(
        SINGLE_ACCOUNT,
        accountDetailsOverride = Some(accountDetails)
      )(requestWithMongoUserDetails(), messagesApi)
      verifyAuditEventSent(expectedAuditEvent)
      server.verify(
        1,
        deleteRequestedFor(
          urlEqualTo(s"/tax-enrolments/groups/$GROUP_ID/enrolments/HMRC-PT~NINO~${mismatchNino.nino}")
        )
      )

    }
    "the HMRC-PT enrolment is not assigned to any users but assigned to multiple empty groups" in {

      val authResponse = authoriseResponseJson()
      stubAuthorizePost(OK, authResponse.toString())
      stubPost(s"/write/.*", OK, """{"x":2}""")
      stubGet(
        s"/enrolment-store-proxy/enrolment-store/enrolments/HMRC-PT~NINO~$NINO/users",
        OK,
        es0ResponseNoRecordCred
      )
      stubGetWithQueryParam(
        "/identity-verification/nino",
        "nino",
        NINO.nino,
        OK,
        ivResponseSingleCredsJsonString
      )

      stubPost(
        s"/enrolment-store-proxy/enrolment-store/enrolments",
        NO_CONTENT,
        ""
      )

      stubPut(
        s"/tax-enrolments/service/HMRC-PT/enrolment",
        NO_CONTENT,
        ""
      )

      stubGet(
        s"/enrolment-store-proxy/enrolment-store/enrolments/HMRC-PT~NINO~$NINO/groups",
        OK,
        es3GroupsResponseRecordCred
      )

      List("ABCDEFG123456", "QWERTYU12346", "POIUYTT09876").foreach { id =>
        stubDelete(
          s"/tax-enrolments/groups/$id/enrolments/HMRC-PT~NINO~$NINO",
          NO_CONTENT,
          es3GroupsResponseRecordCred
        )
      }

      val request = FakeRequest(GET, urlPath)
        .withSession(xAuthToken)
      val result  = route(app, request).get

      status(result)             shouldBe SEE_OTHER
      redirectLocation(result).get should include(returnUrl)
      List("ABCDEFG123456", "QWERTYU12346", "POIUYTT09876").foreach { id =>
        server.verify(
          1,
          deleteRequestedFor(
            urlEqualTo(s"/tax-enrolments/groups/$id/enrolments/HMRC-PT~NINO~$NINO")
          )
        )
      }
    }
  }

  s"redirect to ${ItUrlPaths.ptOnOtherAccountPath}" when {
    "a user has other credentials associated with their NINO" that {
      "includes one with a PT enrolment" in {
        stubAuthorizePost(OK, authoriseResponseJson().toString())
        stubPost(s"/write/.*", OK, """{"x":2}""")
        stubGet(
          s"/enrolment-store-proxy/enrolment-store/enrolments/HMRC-PT~NINO~$NINO/users",
          OK,
          es0ResponseNotMatchingCred
        )
        stubPost(
          s"/enrolment-store-proxy/enrolment-store/enrolments",
          NO_CONTENT,
          ""
        )

        val request = FakeRequest(GET, urlPath)
          .withSession(xAuthToken)
        val result  = route(app, request).get

        status(result)             shouldBe SEE_OTHER
        redirectLocation(result).get should include(ItUrlPaths.ptOnOtherAccountPath)
        recordExistsInMongo        shouldBe true
      }
    }
  }

  s"redirect to ${ItUrlPaths.saOnOtherAccountInterruptPath}" when {
    "the user has SA enrolment on an other account" in {
      val authResponse = authoriseResponseJson()
      stubAuthorizePost(OK, authResponse.toString())
      stubPost(s"/write/.*", OK, """{"x":2}""")
      stubGet(
        s"/enrolment-store-proxy/enrolment-store/enrolments/HMRC-PT~NINO~$NINO/users",
        OK,
        es0ResponseNoRecordCred
      )
      stubGetWithQueryParam(
        "/identity-verification/nino",
        "nino",
        NINO.nino,
        OK,
        ivResponseMultiCredsJsonString
      )
      stubGetMatching(
        s"/enrolment-store-proxy/enrolment-store/users/$CREDENTIAL_ID_3/enrolments?type=principal",
        NO_CONTENT,
        ""
      )
      stubGetMatching(
        s"/enrolment-store-proxy/enrolment-store/users/$CREDENTIAL_ID_4/enrolments?type=principal",
        NO_CONTENT,
        ""
      )

      stubPost(
        s"/enrolment-store-proxy/enrolment-store/enrolments",
        OK,
        eacdResponse
      )
      stubGet(
        s"/enrolment-store-proxy/enrolment-store/enrolments/IR-SA~UTR~$UTR/users",
        OK,
        es0ResponseNotMatchingCred
      )

      stubGet(
        s"/enrolment-store-proxy/enrolment-store/enrolments/HMRC-PT~NINO~$NINO/groups",
        NO_CONTENT,
        es0GroupsResponseNoRecordCred
      )

      val request = FakeRequest(GET, urlPath)
        .withSession(xAuthToken)
      val result  = route(app, request).get

      status(result)             shouldBe SEE_OTHER
      redirectLocation(result).get should include(ItUrlPaths.saOnOtherAccountInterruptPath)
      recordExistsInMongo        shouldBe true
    }
  }

  s"redirect to ${ItUrlPaths.enrolledPTWithSAOnAnyAccountPath}" when {
    "the user has no PT enrolments but current credential has SA enrolment in session" in {
      val authResponse = authoriseResponseJson(enrolments = saEnrolmentOnly)
      stubAuthorizePost(OK, authResponse.toString())
      stubPost(s"/write/.*", OK, """{"x":2}""")
      stubGet(
        s"/enrolment-store-proxy/enrolment-store/enrolments/HMRC-PT~NINO~$NINO/users",
        OK,
        es0ResponseNoRecordCred
      )
      stubGetWithQueryParam(
        "/identity-verification/nino",
        "nino",
        NINO.nino,
        OK,
        ivResponseMultiCredsJsonString
      )
      stubGetMatching(
        s"/enrolment-store-proxy/enrolment-store/users/$CREDENTIAL_ID_3/enrolments?type=principal",
        NO_CONTENT,
        ""
      )
      stubGetMatching(
        s"/enrolment-store-proxy/enrolment-store/users/$CREDENTIAL_ID_4/enrolments?type=principal",
        NO_CONTENT,
        ""
      )
      stubPost(
        s"/enrolment-store-proxy/enrolment-store/enrolments",
        NO_CONTENT,
        ""
      )

      stubPut(
        s"/tax-enrolments/service/HMRC-PT/enrolment",
        NO_CONTENT,
        ""
      )

      stubGet(
        s"/enrolment-store-proxy/enrolment-store/enrolments/HMRC-PT~NINO~$NINO/groups",
        NO_CONTENT,
        es0GroupsResponseNoRecordCred
      )

      val request = FakeRequest(GET, urlPath)
        .withSession(xAuthToken)
      val result  = route(app, request).get

      status(result)             shouldBe SEE_OTHER
      redirectLocation(result).get should include(ItUrlPaths.enrolledPTWithSAOnAnyAccountPath)
      recordExistsInMongo        shouldBe true

      val expectedAuditEvent = AuditEvent.auditSuccessfullyEnrolledPTWhenSANotOnOtherAccount(
        SA_ASSIGNED_TO_CURRENT_USER,
        accountDetailsOverride = Some(accountDetails)
      )(requestWithMongoUserDetails(userDetailsNoEnrolments.copy(hasSAEnrolment = true)), messagesApi)
      verifyAuditEventSent(expectedAuditEvent)
    }
  }

  s"redirect to ${ItUrlPaths.enrolledPTWithSAOnAnyAccountPath}" when {
    "the user has no PT enrolments but current credential has SA enrolment in EACD" in {
      val authResponse = authoriseResponseJson(enrolments = saEnrolmentOnly)
      stubAuthorizePost(OK, authResponse.toString())
      stubPost(s"/write/.*", OK, """{"x":2}""")
      stubGet(
        s"/enrolment-store-proxy/enrolment-store/enrolments/HMRC-PT~NINO~$NINO/users",
        OK,
        es0ResponseNoRecordCred
      )
      stubGetMatching(
        s"/enrolment-store-proxy/enrolment-store/users/$CREDENTIAL_ID_3/enrolments?type=principal",
        NO_CONTENT,
        ""
      )
      stubGetMatching(
        s"/enrolment-store-proxy/enrolment-store/users/$CREDENTIAL_ID_4/enrolments?type=principal",
        NO_CONTENT,
        ""
      )

      stubPost(
        s"/enrolment-store-proxy/enrolment-store/enrolments",
        OK,
        eacdResponse
      )
      stubGet(
        s"/enrolment-store-proxy/enrolment-store/enrolments/IR-SA~UTR~$UTR/users",
        OK,
        es0ResponseMatchingCred
      )

      stubPut(
        s"/tax-enrolments/service/HMRC-PT/enrolment",
        NO_CONTENT,
        ""
      )

      stubGet(
        s"/enrolment-store-proxy/enrolment-store/enrolments/HMRC-PT~NINO~$NINO/groups",
        OK,
        es3GroupsResponseRecordCred
      )

      val request = FakeRequest(GET, urlPath)
        .withSession(xAuthToken)
      val result  = route(app, request).get

      status(result)             shouldBe SEE_OTHER
      redirectLocation(result).get should include(ItUrlPaths.enrolledPTWithSAOnAnyAccountPath)
      recordExistsInMongo        shouldBe true

      val expectedAuditEvent = AuditEvent.auditSuccessfullyEnrolledPTWhenSANotOnOtherAccount(
        SA_ASSIGNED_TO_CURRENT_USER,
        accountDetailsOverride = Some(accountDetails)
      )(requestWithMongoUserDetails(), messagesApi)
      verifyAuditEventSent(expectedAuditEvent)
    }
  }

  s"redirect to ${ItUrlPaths.enrolledPTNoSAOnAnyAccountPath}" when {
    "the user has no PT or SA enrolments and has multiple accounts" in {
      val authResponse = authoriseResponseJson()
      stubAuthorizePost(OK, authResponse.toString())
      stubPost(s"/write/.*", OK, """{"x":2}""")
      stubGet(
        s"/enrolment-store-proxy/enrolment-store/enrolments/HMRC-PT~NINO~$NINO/users",
        OK,
        es0ResponseNoRecordCred
      )
      stubGetWithQueryParam(
        "/identity-verification/nino",
        "nino",
        NINO.nino,
        OK,
        ivResponseMultiCredsJsonString
      )
      stubGetMatching(
        s"/enrolment-store-proxy/enrolment-store/users/$CREDENTIAL_ID_3/enrolments?type=principal",
        NO_CONTENT,
        ""
      )
      stubGetMatching(
        s"/enrolment-store-proxy/enrolment-store/users/$CREDENTIAL_ID_4/enrolments?type=principal",
        NO_CONTENT,
        ""
      )

      stubPost(
        s"/enrolment-store-proxy/enrolment-store/enrolments",
        NO_CONTENT,
        ""
      )
      stubPut(
        s"/tax-enrolments/service/HMRC-PT/enrolment",
        NO_CONTENT,
        ""
      )

      stubGet(
        s"/enrolment-store-proxy/enrolment-store/enrolments/HMRC-PT~NINO~$NINO/groups",
        NO_CONTENT,
        es0GroupsResponseNoRecordCred
      )

      stubGet(
        s"/users-groups-search/users/nino/$NINO/credIds",
        OK,
        userGroupSearchCredIdsResponse
      )

      val request = FakeRequest(GET, urlPath)
        .withSession(xAuthToken)
      val result  = route(app, request).get

      status(result)             shouldBe SEE_OTHER
      redirectLocation(result).get should include(ItUrlPaths.enrolledPTNoSAOnAnyAccountPath)
      recordExistsInMongo        shouldBe true

      val expectedAuditEvent = AuditEvent.auditSuccessfullyEnrolledPTWhenSANotOnOtherAccount(
        MULTIPLE_ACCOUNTS,
        accountDetailsOverride = Some(accountDetails)
      )(requestWithMongoUserDetails(), messagesApi)
      verifyAuditEventSent(expectedAuditEvent)

    }
  }

  "redirect to the return url" when {
    "the user has no PT or SA enrolments and has only 1 account" in {
      val relativeUrl = "/redirect/url"
      val encodedUrl  = URLEncoder.encode(relativeUrl, "UTF-8")
      val urlPath     = s"/protect-tax-info?redirectUrl=$encodedUrl"

      val authResponse = authoriseResponseJson()
      stubAuthorizePost(OK, authResponse.toString())
      stubPost(s"/write/.*", OK, """{"x":2}""")
      stubGet(
        s"/enrolment-store-proxy/enrolment-store/enrolments/HMRC-PT~NINO~$NINO/users",
        OK,
        es0ResponseNoRecordCred
      )
      stubGetWithQueryParam(
        "/identity-verification/nino",
        "nino",
        NINO.nino,
        OK,
        ivResponseMultiCredsJsonString
      )
      stubGetMatching(
        s"/enrolment-store-proxy/enrolment-store/users/$CREDENTIAL_ID_3/enrolments?type=principal",
        NO_CONTENT,
        ""
      )
      stubGetMatching(
        s"/enrolment-store-proxy/enrolment-store/users/$CREDENTIAL_ID_4/enrolments?type=principal",
        NO_CONTENT,
        ""
      )

      stubPost(
        s"/enrolment-store-proxy/enrolment-store/enrolments",
        NO_CONTENT,
        ""
      )
      stubPut(
        s"/tax-enrolments/service/HMRC-PT/enrolment",
        NO_CONTENT,
        ""
      )

      stubGet(
        s"/enrolment-store-proxy/enrolment-store/enrolments/HMRC-PT~NINO~$NINO/groups",
        NO_CONTENT,
        es0GroupsResponseNoRecordCred
      )

      val request = FakeRequest(GET, urlPath)
        .withSession(xAuthToken)
      val result  = route(app, request).get

      status(result)             shouldBe SEE_OTHER
      redirectLocation(result).get should include(relativeUrl)
      recordExistsInMongo        shouldBe false

      val expectedAuditEvent = AuditEvent.auditSuccessfullyEnrolledPTWhenSANotOnOtherAccount(
        SINGLE_ACCOUNT,
        accountDetailsOverride = Some(accountDetails)
      )(requestWithMongoUserDetails(), messagesApi)
      verifyAuditEventSent(expectedAuditEvent)

    }
    s"the user has pt enrolment in session" should {
      "redirect to the return url" when {
        "the redirectUrl is a valid encoded relative url" in {
          val relativeUrl = "/redirect/url"
          val encodedUrl  = URLEncoder.encode(relativeUrl, "UTF-8")
          val urlPath     = s"/protect-tax-info?redirectUrl=$encodedUrl"

          val authResponse = authoriseResponseJson(enrolments = ptEnrolmentOnly)
          stubAuthorizePost(OK, authResponse.toString())
          stubPost(s"/write/.*", OK, """{"x":2}""")
          stubPost(
            s"/enrolment-store-proxy/enrolment-store/enrolments",
            NO_CONTENT,
            ""
          )
          stubGet(
            s"/enrolment-store-proxy/enrolment-store/enrolments/HMRC-PT~NINO~$NINO/users",
            OK,
            es0ResponseNoRecordCred
          )

          stubGet(
            s"/enrolment-store-proxy/enrolment-store/enrolments/HMRC-PT~NINO~$NINO/groups",
            NO_CONTENT,
            es0GroupsResponseNoRecordCred
          )

          val request = FakeRequest(GET, urlPath)
            .withSession(xAuthToken)
          val result  = route(app, request).get

          status(result)             shouldBe SEE_OTHER
          redirectLocation(result).get should include(relativeUrl)
          recordExistsInMongo        shouldBe false
        }

        "the redirectUrl is a valid relative url" in {
          val relativeUrl = "/redirect/url"
          val urlPath     = s"/protect-tax-info?redirectUrl=$relativeUrl"

          val authResponse = authoriseResponseJson(enrolments = ptEnrolmentOnly)
          stubAuthorizePost(OK, authResponse.toString())
          stubPost(s"/write/.*", OK, """{"x":2}""")
          stubPost(
            s"/enrolment-store-proxy/enrolment-store/enrolments",
            NO_CONTENT,
            ""
          )
          stubGet(
            s"/enrolment-store-proxy/enrolment-store/enrolments/HMRC-PT~NINO~$NINO/users",
            OK,
            es0ResponseNoRecordCred
          )
          stubGet(
            s"/enrolment-store-proxy/enrolment-store/enrolments/HMRC-PT~NINO~$NINO/groups",
            NO_CONTENT,
            es0GroupsResponseNoRecordCred
          )

          val request = FakeRequest(GET, urlPath)
            .withSession(xAuthToken)
          val result  = route(app, request).get

          status(result)             shouldBe SEE_OTHER
          redirectLocation(result).get should include(relativeUrl)
          recordExistsInMongo        shouldBe false
        }

        "the redirectUrl is a valid encoded absolute localhost url" in {
          val absoluteUrl = "http://localhost:1234/redirect/url"
          val encodedUrl  = URLEncoder.encode(absoluteUrl, "UTF-8")
          val urlPath     = s"/protect-tax-info?redirectUrl=$encodedUrl"

          val authResponse = authoriseResponseJson(enrolments = ptEnrolmentOnly)
          stubAuthorizePost(OK, authResponse.toString())
          stubPost(s"/write/.*", OK, """{"x":2}""")
          stubPost(
            s"/enrolment-store-proxy/enrolment-store/enrolments",
            OK,
            eacdResponse
          )
          stubGet(
            s"/enrolment-store-proxy/enrolment-store/enrolments/HMRC-PT~NINO~$NINO/users",
            OK,
            es0ResponseNoRecordCred
          )
          stubGet(
            s"/enrolment-store-proxy/enrolment-store/enrolments/IR-SA~UTR~$UTR/users",
            OK,
            es0ResponseNotMatchingCred
          )

          stubGet(
            s"/enrolment-store-proxy/enrolment-store/enrolments/HMRC-PT~NINO~$NINO/groups",
            NO_CONTENT,
            es0GroupsResponseNoRecordCred
          )

          val request = FakeRequest(GET, urlPath)
            .withSession(xAuthToken)
          val result  = route(app, request).get

          status(result)             shouldBe SEE_OTHER
          redirectLocation(result).get should include(absoluteUrl)
          recordExistsInMongo        shouldBe false
        }

        "the redirectUrl is a valid absolute localhost url" in {
          val absoluteUrl = "http://localhost:1234/redirect/url"
          val urlPath     = s"/protect-tax-info?redirectUrl=$absoluteUrl"

          val authResponse = authoriseResponseJson(enrolments = ptEnrolmentOnly)
          stubAuthorizePost(OK, authResponse.toString())
          stubPost(s"/write/.*", OK, """{"x":2}""")
          stubPost(
            s"/enrolment-store-proxy/enrolment-store/enrolments",
            OK,
            eacdResponse
          )
          stubGet(
            s"/enrolment-store-proxy/enrolment-store/enrolments/HMRC-PT~NINO~$NINO/users",
            OK,
            es0ResponseNoRecordCred
          )
          stubGet(
            s"/enrolment-store-proxy/enrolment-store/enrolments/IR-SA~UTR~$UTR/users",
            OK,
            es0ResponseNotMatchingCred
          )

          stubGet(
            s"/enrolment-store-proxy/enrolment-store/enrolments/HMRC-PT~NINO~$NINO/groups",
            NO_CONTENT,
            es0GroupsResponseNoRecordCred
          )

          val request = FakeRequest(GET, urlPath)
            .withSession(xAuthToken)
          val result  = route(app, request).get

          status(result)             shouldBe SEE_OTHER
          redirectLocation(result).get should include(absoluteUrl)
          recordExistsInMongo        shouldBe false
        }

        "the redirectUrl is a valid encoded absolute url with hostname www.tax.service.gov.uk" in {
          val absoluteUrl = "https://www.tax.service.gov.uk/redirect/url"
          val encodedUrl  = URLEncoder.encode(absoluteUrl, "UTF-8")
          val urlPath     = s"/protect-tax-info?redirectUrl=$encodedUrl"

          val authResponse = authoriseResponseJson(enrolments = ptEnrolmentOnly)
          stubAuthorizePost(OK, authResponse.toString())
          stubPost(s"/write/.*", OK, """{"x":2}""")
          stubPost(
            s"/enrolment-store-proxy/enrolment-store/enrolments",
            OK,
            eacdResponse
          )
          stubGet(
            s"/enrolment-store-proxy/enrolment-store/enrolments/HMRC-PT~NINO~$NINO/users",
            OK,
            es0ResponseNoRecordCred
          )
          stubGet(
            s"/enrolment-store-proxy/enrolment-store/enrolments/IR-SA~UTR~$UTR/users",
            OK,
            es0ResponseNotMatchingCred
          )
          stubGet(
            s"/enrolment-store-proxy/enrolment-store/enrolments/HMRC-PT~NINO~$NINO/groups",
            NO_CONTENT,
            es0GroupsResponseNoRecordCred
          )

          val request = FakeRequest(GET, urlPath)
            .withSession(xAuthToken)
          val result  = route(app, request).get

          status(result)             shouldBe SEE_OTHER
          redirectLocation(result).get should include(absoluteUrl)
          recordExistsInMongo        shouldBe false
        }

        "the redirectUrl is a valid absolute url with hostname www.tax.service.gov.uk" in {
          val absoluteUrl = "https://www.tax.service.gov.uk/redirect/url"
          val urlPath     = s"/protect-tax-info?redirectUrl=$absoluteUrl"

          val authResponse = authoriseResponseJson(enrolments = ptEnrolmentOnly)
          stubAuthorizePost(OK, authResponse.toString())
          stubPost(s"/write/.*", OK, """{"x":2}""")
          stubPost(
            s"/enrolment-store-proxy/enrolment-store/enrolments",
            OK,
            eacdResponse
          )
          stubGet(
            s"/enrolment-store-proxy/enrolment-store/enrolments/HMRC-PT~NINO~$NINO/users",
            OK,
            es0ResponseNoRecordCred
          )
          stubGet(
            s"/enrolment-store-proxy/enrolment-store/enrolments/IR-SA~UTR~$UTR/users",
            OK,
            es0ResponseNotMatchingCred
          )

          stubGet(
            s"/enrolment-store-proxy/enrolment-store/enrolments/HMRC-PT~NINO~$NINO/groups",
            OK,
            es0GroupsResponseNoRecordCred
          )

          val request = FakeRequest(GET, urlPath)
            .withSession(xAuthToken)
          val result  = route(app, request).get

          status(result)             shouldBe SEE_OTHER
          redirectLocation(result).get should include(absoluteUrl)
          recordExistsInMongo        shouldBe false
        }
      }

      "render the error page" when {
        "an invalid redirectUrl supplied" in {
          val urlPath = s"/protect-tax-info?redirectUrl=not-a-url"

          val authResponse = authoriseResponseJson(enrolments = ptEnrolmentOnly)
          stubAuthorizePost(OK, authResponse.toString())
          stubPost(s"/write/.*", OK, """{"x":2}""")

          val request = FakeRequest(GET, urlPath)
            .withSession(xAuthToken)
          val result  = route(app, request).get

          status(result)        shouldBe BAD_REQUEST
          contentAsString(result) should include(ErrorTemplateMessages.title)
          recordExistsInMongo   shouldBe false
        }

        "a non supported redirect host is supplied" in {
          val urlPath = s"/protect-tax-info?redirectUrl=https://notSupportedHost.com/test"

          val authResponse = authoriseResponseJson(enrolments = ptEnrolmentOnly)
          stubAuthorizePost(OK, authResponse.toString())
          stubPost(s"/write/.*", OK, """{"x":2}""")

          val request = FakeRequest(GET, urlPath)
            .withSession(xAuthToken)
          val result  = route(app, request).get

          status(result)        shouldBe BAD_REQUEST
          contentAsString(result) should include(ErrorTemplateMessages.title)
          recordExistsInMongo   shouldBe false
        }
      }
    }

    "the user has a session missing required element NINO" should {
      s"redirect to ${ItUrlPaths.unauthorizedPath}" in {
        val authResponse = authoriseResponseJson(optNino = None)
        stubAuthorizePost(OK, authResponse.toString())
        stubPost(s"/write/.*", OK, """{"x":2}""")

        val request = FakeRequest(GET, urlPath)
          .withSession(xAuthToken)
        val result  = route(app, request).get

        status(result)             shouldBe SEE_OTHER
        redirectLocation(result).get should include(ItUrlPaths.unauthorizedPath)
        recordExistsInMongo        shouldBe false
      }
    }

    "the user has a session missing required element Credentials" should {
      s"redirect to ${ItUrlPaths.unauthorizedPath}" in {
        val authResponse = authoriseResponseJson(optCreds = None)
        stubAuthorizePost(OK, authResponse.toString())
        stubPost(s"/write/.*", OK, """{"x":2}""")

        val request = FakeRequest(GET, urlPath)
          .withSession(xAuthToken)
        val result  = route(app, request).get

        status(result)             shouldBe SEE_OTHER
        redirectLocation(result).get should include(ItUrlPaths.unauthorizedPath)
        recordExistsInMongo        shouldBe false
      }
    }

    "the user has a insufficient confidence level" should {
      s"redirect to ${ItUrlPaths.unauthorizedPath}" in {
        stubAuthorizePostUnauthorised(insufficientConfidenceLevel)
        stubPost(s"/write/.*", OK, """{"x":2}""")

        val request = FakeRequest(GET, urlPath)
          .withSession(xAuthToken)
        val result  = route(app, request).get

        status(result)             shouldBe SEE_OTHER
        redirectLocation(result).get should include(ItUrlPaths.unauthorizedPath)
        recordExistsInMongo        shouldBe false
      }
    }

    "the user has no active session" should {
      s"redirect to login" in {
        stubAuthorizePostUnauthorised(sessionNotFound)
        stubPost(s"/write/.*", OK, """{"x":2}""")

        val request = FakeRequest(GET, urlPath)
          .withSession(xAuthToken)
        val result  = route(app, request).get

        status(result)             shouldBe SEE_OTHER
        redirectLocation(result).get should include("/bas-gateway/sign-in")
        recordExistsInMongo        shouldBe false
      }
    }

    "the user has a nino which mismatches their enrolment nino" should {
      val url =
        s"/tax-enrolments/groups/$GROUP_ID/enrolments/HMRC-PT~NINO~${mismatchNino.nino}"
      "return technical difficulty when enrolment deletion is failing" in {
        val authResponse = authoriseResponseJson(enrolments = mismatchPtEnrolmentOnly)
        stubAuthorizePost(OK, authResponse.toString())
        stubPost(s"/write/.*", OK, """{"x":2}""")
        stubDelete(url, Status.INTERNAL_SERVER_ERROR)

        val request = FakeRequest(GET, urlPath)
          .withSession(xAuthToken)
        val result  = route(app, request).get

        status(result) shouldBe INTERNAL_SERVER_ERROR
        server.verify(1, deleteRequestedFor(urlMatching(url)))
      }
    }
  }
}
