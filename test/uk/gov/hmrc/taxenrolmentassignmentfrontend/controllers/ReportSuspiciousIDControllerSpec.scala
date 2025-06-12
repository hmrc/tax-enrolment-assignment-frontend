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

package uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers

import org.mockito.ArgumentMatchers.{any, eq => ameq}
import org.mockito.Mockito.{times, verify, when}
import play.api.Application
import play.api.inject.{Binding, bind}
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.BodyParsers
import play.api.test.Helpers.*
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.CacheMap
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes.{PT_ASSIGNED_TO_OTHER_USER, SA_ASSIGNED_TO_OTHER_USER}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.*
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.TestData.*
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.{ControllersBaseSpec, UrlPaths}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.AccountDetails
import uk.gov.hmrc.taxenrolmentassignmentfrontend.orchestrators.{AccountCheckOrchestrator, MultipleAccountsOrchestrator}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.reporting.{AuditEvent, AuditHandler}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.SessionKeys.{REPORTED_FRAUD, USER_ASSIGNED_SA_ENROLMENT, accountDetailsForCredential}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.TEASessionCache
import uk.gov.hmrc.taxenrolmentassignmentfrontend.services.SilentAssignmentService
import uk.gov.hmrc.taxenrolmentassignmentfrontend.views.html.ReportSuspiciousIDGateway

import scala.concurrent.{ExecutionContext, Future}

class ReportSuspiciousIDControllerSpec extends ControllersBaseSpec {

  lazy val mockSilentAssignmentService: SilentAssignmentService = mock[SilentAssignmentService]
  lazy val mockAccountCheckOrchestrator: AccountCheckOrchestrator = mock[AccountCheckOrchestrator]
  lazy val mockAuditHandler: AuditHandler = mock[AuditHandler]

  lazy val testBodyParser: BodyParsers.Default = mock[BodyParsers.Default]
  lazy val mockMultipleAccountsOrchestrator: MultipleAccountsOrchestrator = mock[MultipleAccountsOrchestrator]

  override lazy val overrides: Seq[Binding[TEASessionCache]] = Seq(
    bind[TEASessionCache].toInstance(mockTeaSessionCache)
  )

  override implicit lazy val app: Application = localGuiceApplicationBuilder()
    .overrides(
      bind[SilentAssignmentService].toInstance(mockSilentAssignmentService),
      bind[AccountCheckOrchestrator].toInstance(mockAccountCheckOrchestrator),
      bind[AuditHandler].toInstance(mockAuditHandler),
      bind[AuthConnector].toInstance(mockAuthConnector),
      bind[BodyParsers.Default].toInstance(testBodyParser),
      bind[MultipleAccountsOrchestrator].toInstance(mockMultipleAccountsOrchestrator)
    )
    .build()

  lazy val controller: ReportSuspiciousIDController = app.injector.instanceOf[ReportSuspiciousIDController]

  val view: ReportSuspiciousIDGateway = app.injector.instanceOf[ReportSuspiciousIDGateway]

  "viewNoSA" when {

    "a user has PT on another account" should {
      "render the ReportSuspiciousID page" in {

        when(mockAuthConnector.authorise(ameq(predicates), ameq(retrievals))(any[HeaderCarrier], any[ExecutionContext]))
          .thenReturn(Future.successful(retrievalResponse()))

        when(mockMultipleAccountsOrchestrator.checkValidAccountType(ameq(List(PT_ASSIGNED_TO_OTHER_USER)))(any()))
          .thenReturn(Right(PT_ASSIGNED_TO_OTHER_USER))

        when(mockMultipleAccountsOrchestrator.getPTCredentialDetails(any(), any(), any()))
          .thenReturn(
            createInboundResult(accountDetails)
          )
        mockGetDataFromCacheForActionSuccess(PT_ASSIGNED_TO_OTHER_USER)

        val auditEvent = AuditEvent(
          "ReportUnrecognisedAccount",
          "reporting-unrecognised-pt-account",
          Json
            .parse(
              s"""{"NINO":"$NINO","currentAccount":{"credentialId":"credId123","type":"PT_ASSIGNED_TO_OTHER_USER",
                 |"authProvider":"GovernmentGateway","email":"foobarwizz","affinityGroup":"Individual"},
                 |"reportedAccount":{"credentialId":"credId123","userId":"Ending with 6037","email":"email1@test.com",
                 |"lastSignedIn":"27 February 2022 at 12:00PM","mfaDetails":[{"factorType":"Text message",
                 |"factorValue":"Ending with 24321"}],"authProvider":"SCP"}}""".stripMargin
            )
            .as[JsObject]
        )

        when(mockAuditHandler.audit(ameq(auditEvent))(any[HeaderCarrier])).thenReturn(Future.successful((): Unit))

        val result = controller.viewNoSA
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(result) shouldBe OK
        contentAsString(result) should include(messages("ReportSuspiciousID.heading"))
        verify(mockAuditHandler, times(1)).audit(ameq(auditEvent))(any[HeaderCarrier])

      }
    }

    "the user does not have an account type of PT_ASSIGNED_TO_OTHER_USER" should {
      s"redirect to ${UrlPaths.accountCheckPath}" in {

        when(mockAuthConnector.authorise(ameq(predicates), ameq(retrievals))(any[HeaderCarrier], any[ExecutionContext]))
          .thenReturn(Future.successful(retrievalResponse()))

        when(mockMultipleAccountsOrchestrator.checkValidAccountType(ameq(List(PT_ASSIGNED_TO_OTHER_USER)))(any()))
          .thenReturn(Left(IncorrectUserType(UrlPaths.returnUrl, randomAccountType)))

        mockGetDataFromCacheForActionSuccess(PT_ASSIGNED_TO_OTHER_USER)

        val result = controller.viewNoSA
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(UrlPaths.accountCheckPath)
      }
    }

    "the current user has a no PT enrolment on other account but session says it is other account" should {
      "render the error page" in {
        when(mockAuthConnector.authorise(ameq(predicates), ameq(retrievals))(any[HeaderCarrier], any[ExecutionContext]))
          .thenReturn(Future.successful(retrievalResponse(enrolments = saEnrolmentOnly)))

        when(mockMultipleAccountsOrchestrator.checkValidAccountType(ameq(List(PT_ASSIGNED_TO_OTHER_USER)))(any()))
          .thenReturn(Right(PT_ASSIGNED_TO_OTHER_USER))

        when(mockMultipleAccountsOrchestrator.getPTCredentialDetails(any(), any(), any()))
          .thenReturn(
            createInboundResultError(NoPTEnrolmentWhenOneExpected)
          )

        mockGetDataFromCacheForActionSuccess(randomAccountType)

        val res = controller.viewNoSA
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(res) shouldBe INTERNAL_SERVER_ERROR
        contentAsString(res) should include(messages("enrolmentError.heading"))
      }
    }

  }

  "viewSA" when {

    "a user has SA on another account" should {
      "render the ReportSuspiciousID page" when {
        "the user hasn't already been assigned a PT enrolment" in {

          when(
            mockAuthConnector.authorise(ameq(predicates), ameq(retrievals))(any[HeaderCarrier], any[ExecutionContext])
          )
            .thenReturn(Future.successful(retrievalResponse()))

          when(mockMultipleAccountsOrchestrator.checkValidAccountType(ameq(List(SA_ASSIGNED_TO_OTHER_USER)))(any()))
            .thenReturn(Right(SA_ASSIGNED_TO_OTHER_USER))

          when(mockMultipleAccountsOrchestrator.getSACredentialDetails(any(), any(), any()))
            .thenReturn(createInboundResult(accountDetails))

          mockGetDataFromCacheForActionSuccess(SA_ASSIGNED_TO_OTHER_USER)

          val auditEvent = AuditEvent(
            "ReportUnrecognisedAccount",
            "reporting-unrecognised-sa-account",
            Json
              .parse(
                s"""{"NINO":"$NINO","currentAccount":{"credentialId":"credId123","type":"SA_ASSIGNED_TO_OTHER_USER",
                   |"authProvider":"GovernmentGateway","email":"foobarwizz","affinityGroup":"Individual"},
                   |"reportedAccount":{"credentialId":"credId123","userId":"Ending with 6037","email":"email1@test.com",
                   |"lastSignedIn":"27 February 2022 at 12:00PM","mfaDetails":[{"factorType":"Text message",
                   |"factorValue":"Ending with 24321"}],"authProvider":"SCP"}}""".stripMargin
              )
              .as[JsObject]
          )

          when(mockAuditHandler.audit(ameq(auditEvent))(any[HeaderCarrier])).thenReturn(Future.successful((): Unit))

          val result = controller.viewSA
            .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

          status(result) shouldBe OK
          contentAsString(result) should include(messages("ReportSuspiciousID.heading"))
          verify(mockAuditHandler, times(1)).audit(ameq(auditEvent))(any[HeaderCarrier])
        }

        "the user has already been assigned a PT enrolment" in {

          when(
            mockAuthConnector.authorise(ameq(predicates), ameq(retrievals))(any[HeaderCarrier], any[ExecutionContext])
          )
            .thenReturn(Future.successful(retrievalResponse(enrolments = ptEnrolmentOnly)))

          when(mockMultipleAccountsOrchestrator.checkValidAccountType(ameq(List(SA_ASSIGNED_TO_OTHER_USER)))(any()))
            .thenReturn(Right(SA_ASSIGNED_TO_OTHER_USER))

          when(mockMultipleAccountsOrchestrator.getSACredentialDetails(any(), any(), any()))
            .thenReturn(createInboundResult(accountDetails))

          mockGetDataFromCacheForActionSuccess(SA_ASSIGNED_TO_OTHER_USER)

          val result = controller.viewSA
            .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

          status(result) shouldBe OK
          contentAsString(result) should include(messages("ReportSuspiciousID.heading"))
        }
      }
    }
    s"the cache no redirectUrl" should {
      "render the error page" in {
        when(mockAuthConnector.authorise(ameq(predicates), ameq(retrievals))(any[HeaderCarrier], any[ExecutionContext]))
          .thenReturn(Future.successful(retrievalResponse()))

        mockGetDataFromCacheForActionNoRedirectUrl

        val res = controller.viewNoSA
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(res) shouldBe INTERNAL_SERVER_ERROR
        contentAsString(res) should include(messages("enrolmentError.heading"))
      }
    }

    s"the user does not have an account type of $SA_ASSIGNED_TO_OTHER_USER" should {
      s"redirect to ${UrlPaths.accountCheckPath}" in {
        when(mockAuthConnector.authorise(ameq(predicates), ameq(retrievals))(any[HeaderCarrier], any[ExecutionContext]))
          .thenReturn(Future.successful(retrievalResponse()))

        when(mockMultipleAccountsOrchestrator.checkValidAccountType(ameq(List(SA_ASSIGNED_TO_OTHER_USER)))(any()))
          .thenReturn(Left(IncorrectUserType(UrlPaths.returnUrl, randomAccountType)))

        mockGetDataFromCacheForActionSuccess(randomAccountType)

        val result = controller.viewSA
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(UrlPaths.accountCheckPath)
      }
    }

    "the current user has a no SA enrolment on other account but session says it is other account" should {
      "render the error page" in {
        when(mockAuthConnector.authorise(ameq(predicates), ameq(retrievals))(any[HeaderCarrier], any[ExecutionContext]))
          .thenReturn(Future.successful(retrievalResponse()))

        when(mockMultipleAccountsOrchestrator.checkValidAccountType(ameq(List(SA_ASSIGNED_TO_OTHER_USER)))(any()))
          .thenReturn(Right(SA_ASSIGNED_TO_OTHER_USER))

        when(mockMultipleAccountsOrchestrator.getSACredentialDetails(any(), any(), any()))
          .thenReturn(createInboundResultError(NoSAEnrolmentWhenOneExpected))

        mockGetDataFromCacheForActionSuccess(randomAccountType)

        val res = controller.viewSA
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(res) shouldBe INTERNAL_SERVER_ERROR
        contentAsString(res) should include(messages("enrolmentError.heading"))
      }
    }
  }

  "continue" when {
    "the user has SA assigned to another user and not already enrolled for PT" should {
//      s"enrol for PT and redirect to ${UrlPaths.enrolledPTSAOnOtherAccountPath}" in {
//        val additionalCacheData = Map(
//          USER_ASSIGNED_SA_ENROLMENT -> Json.toJson(UsersAssignedEnrolment1),
//          accountDetailsForCredential(CREDENTIAL_ID_1) -> Json.toJson(accountDetails)(
//            AccountDetails.mongoFormats(crypto.crypto)
//          )
//        )
//        val sessionData = generateBasicCacheData(SA_ASSIGNED_TO_OTHER_USER, UrlPaths.returnUrl) ++ additionalCacheData
//
//        when(mockAuthConnector.authorise(ameq(predicates), ameq(retrievals))(any[HeaderCarrier], any[ExecutionContext]))
//          .thenReturn(Future.successful(retrievalResponse()))
//
//        when(mockTeaSessionCache.save(REPORTED_FRAUD, true))
//          .thenReturn(Future(CacheMap(request.sessionID, sessionData)))
//
//        when(
//          mockMultipleAccountsOrchestrator
//            .checkValidAccountTypeAndEnrolForPT(ameq(SA_ASSIGNED_TO_OTHER_USER))(any(), any(), any())
//        )
//          .thenReturn(createInboundResult((): Unit))
//
//        val auditEvent = AuditEvent(
//          "SuccessfullyEnrolledPersonalTax",
//          "successfully-enrolled-personal-tax",
//          Json
//            .parse(
//              s"""{"NINO":"$NINO","currentAccount":{"credentialId":"credId123","type":"SA_ASSIGNED_TO_OTHER_USER",
//                 |"authProvider":"GovernmentGateway","email":"foobarwizz","affinityGroup":"Individual"},"saAccountCredentialId":"6102202884164541","reportedAccount":{"credentialId":"credId123","userId":"Ending with 6037","email":"email1@test.com","lastSignedIn":"2022-02-27T12:00:27Z","mfaDetails":[{"factorType":"Text message","factorValue":"Ending with 24321"}],"authProvider":"SCP"}}""".stripMargin
//            )
//            .as[JsObject]
//        )
//
//        when(mockAuditHandler.audit(ameq(auditEvent))(any[HeaderCarrier])).thenReturn(Future.successful((): Unit))
//
//        val res = controller.continue
//          .apply(buildFakeRequestWithSessionId("POST", "Not Used"))
//
//        status(res) shouldBe SEE_OTHER
//        redirectLocation(res) shouldBe Some(
//          UrlPaths.enrolledPTNoSAOnAnyAccountPath
//        )
//        verify(mockAuditHandler, times(1)).audit(ameq(auditEvent))(any[HeaderCarrier])
//      }
    }

    "the user has SA assigned to another user and has already been enrolled for PT" should {
      s"not enrol for PT again and redirect to ${UrlPaths.enrolledPTSAOnOtherAccountPath}" in {
        val additionalCacheData = Map(
          USER_ASSIGNED_SA_ENROLMENT                   -> Json.toJson(UsersAssignedEnrolment1),
          accountDetailsForCredential(CREDENTIAL_ID_1) -> Json.toJson(accountDetails)
        )
        val sessionData = generateBasicCacheData(SA_ASSIGNED_TO_OTHER_USER, UrlPaths.returnUrl) ++ additionalCacheData

        when(mockAuthConnector.authorise(ameq(predicates), ameq(retrievals))(any[HeaderCarrier], any[ExecutionContext]))
          .thenReturn(Future.successful(retrievalResponse(enrolments = ptEnrolmentOnly)))

        when(mockTeaSessionCache.save(ameq(REPORTED_FRAUD), ameq(true))(any(), any()))
          .thenReturn(Future(CacheMap(request.sessionID, sessionData)))

        when(
          mockMultipleAccountsOrchestrator
            .checkValidAccountTypeAndEnrolForPT(ameq(SA_ASSIGNED_TO_OTHER_USER))(any(), any(), any())
        )
          .thenReturn(createInboundResultError(UnexpectedPTEnrolment(SA_ASSIGNED_TO_OTHER_USER)))

        mockGetDataFromCacheForActionSuccess(SA_ASSIGNED_TO_OTHER_USER, UrlPaths.returnUrl, additionalCacheData)

        val res = controller.continue
          .apply(buildFakeRequestWithSessionId("POST", "Not Used"))

        status(res) shouldBe SEE_OTHER
        redirectLocation(res) shouldBe Some(
          UrlPaths.enrolledPTSAOnOtherAccountPath
        )
      }
    }

    s"the cache no redirectUrl" should {
      "render the error page" in {
        when(mockAuthConnector.authorise(ameq(predicates), ameq(retrievals))(any[HeaderCarrier], any[ExecutionContext]))
          .thenReturn(Future.successful(retrievalResponse()))
        mockGetDataFromCacheForActionNoRedirectUrl

        val res = controller.continue
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(res) shouldBe INTERNAL_SERVER_ERROR
        contentAsString(res) should include(messages("enrolmentError.heading"))
      }
    }

    "the user has not got SA assigned to another user" should {
      s"redirect to ${UrlPaths.accountCheckPath}" in {
        when(mockAuthConnector.authorise(ameq(predicates), ameq(retrievals))(any[HeaderCarrier], any[ExecutionContext]))
          .thenReturn(Future.successful(retrievalResponse()))

        when(mockTeaSessionCache.save(REPORTED_FRAUD, true))
          .thenReturn(Future(CacheMap(request.sessionID, Map())))

        when(
          mockMultipleAccountsOrchestrator
            .checkValidAccountTypeAndEnrolForPT(ameq(SA_ASSIGNED_TO_OTHER_USER))(any(), any(), any())
        )
          .thenReturn(createInboundResultError(IncorrectUserType(UrlPaths.returnUrl, randomAccountType)))

        mockGetDataFromCacheForActionSuccess(randomAccountType)

        val res = controller.continue
          .apply(buildFakeRequestWithSessionId("POST", "Not Used"))

        status(res) shouldBe SEE_OTHER
        redirectLocation(res) shouldBe
          Some(UrlPaths.accountCheckPath)
      }
    }

    "the user has SA assigned to another user but enrolment to PT is unsuccessful" should {
      "render the error view" in {
        when(mockAuthConnector.authorise(ameq(predicates), ameq(retrievals))(any[HeaderCarrier], any[ExecutionContext]))
          .thenReturn(Future.successful(retrievalResponse()))

        when(mockTeaSessionCache.save(ameq(REPORTED_FRAUD), ameq(true))(any(), any()))
          .thenReturn(Future(CacheMap(request.sessionID, Map())))

        when(
          mockMultipleAccountsOrchestrator
            .checkValidAccountTypeAndEnrolForPT(ameq(SA_ASSIGNED_TO_OTHER_USER))(any(), any(), any())
        )
          .thenReturn(createInboundResultError(UnexpectedResponseFromTaxEnrolments))

        mockGetDataFromCacheForActionSuccess(randomAccountType)

        val res = controller.continue
          .apply(buildFakeRequestWithSessionId("POST", "Not Used"))

        status(res) shouldBe INTERNAL_SERVER_ERROR
        contentAsString(res) should include(messages("enrolmentError.heading"))
      }
    }
  }
}
