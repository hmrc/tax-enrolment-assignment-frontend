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

package uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers

import play.api.http.Status.OK
import play.api.libs.json.{Format, Json}
import play.api.mvc.AnyContent
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve.{Credentials, Retrieval, ~}
import uk.gov.hmrc.auth.core.{AffinityGroup, Enrolments}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes.{PT_ASSIGNED_TO_OTHER_USER, SA_ASSIGNED_TO_OTHER_USER}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.actions.{RequestWithUserDetailsFromSession, RequestWithUserDetailsFromSessionAndMongo}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.{IncorrectUserType, NoPTEnrolmentWhenOneExpected, NoSAEnrolmentWhenOneExpected, UnexpectedPTEnrolment, UnexpectedResponseFromTaxEnrolments}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.TestData._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.{TestFixture, ThrottleHelperSpec, UrlPaths}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.reporting.AuditEvent
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.SessionKeys.{REPORTED_FRAUD, USER_ASSIGNED_SA_ENROLMENT, accountDetailsForCredential}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.views.html.ReportSuspiciousID

import scala.concurrent.{ExecutionContext, Future}

class ReportSuspiciousIDControllerSpec extends TestFixture with ThrottleHelperSpec {

  val view: ReportSuspiciousID = app.injector.instanceOf[ReportSuspiciousID]
  val controller =
    new ReportSuspiciousIDController(
      mockAuthAction,
      mockAccountMongoDetailsAction,
      mockThrottleAction,
      mockTeaSessionCache,
      mockMultipleAccountsOrchestrator,
      mcc,
      view,
      logger,
      mockAuditHandler,
      errorHandler
    )

  "viewNoSA" when {

    specificThrottleTests(controller.viewNoSA())

    "a user has PT on another account" should {
      "render the ReportSuspiciousID page" in {

        (mockAuthConnector
          .authorise(
            _: Predicate,
            _: Retrieval[
              ((Option[String] ~ Option[Credentials]) ~ Enrolments) ~ Option[
                String
              ] ~ Option[AffinityGroup]
            ]
          )(_: HeaderCarrier, _: ExecutionContext))
          .expects(predicates, retrievals, *, *)
          .returning(Future.successful(retrievalResponse()))

        (mockMultipleAccountsOrchestrator
          .checkValidAccountType(_: List[AccountTypes.Value])(
            _: RequestWithUserDetailsFromSessionAndMongo[AnyContent]
          ))
          .expects(List(PT_ASSIGNED_TO_OTHER_USER), *)
          .returning(Right(PT_ASSIGNED_TO_OTHER_USER))

        (mockMultipleAccountsOrchestrator
          .getPTCredentialDetails(
            _: RequestWithUserDetailsFromSessionAndMongo[AnyContent],
            _: HeaderCarrier,
            _: ExecutionContext
          ))
          .expects(*, *, *)
          .returning(createInboundResult(accountDetails))
        mockGetDataFromCacheForActionSuccess(PT_ASSIGNED_TO_OTHER_USER)
        mockAccountShouldNotBeThrottled(PT_ASSIGNED_TO_OTHER_USER, NINO, noEnrolments.enrolments)

        val auditEvent = AuditEvent.auditReportSuspiciousPTAccount(
          accountDetails.copy(lastLoginDate = "27 common.month2 2022 common.dateToTime 12:00 PM")
        )(requestWithAccountType(PT_ASSIGNED_TO_OTHER_USER), stubbedMessagesApi)
        (mockAuditHandler
          .audit(_: AuditEvent)(_: HeaderCarrier))
          .expects(auditEvent, *)
          .returning(Future.successful((): Unit))
          .once()

        val result = controller
          .viewNoSA()
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(result) shouldBe OK
        contentAsString(result) should include("ReportSuspiciousID.heading")
      }
    }

    "the user does not have an account type of PT_ASSIGNED_TO_OTHER_USER" should {
      s"redirect to ${UrlPaths.accountCheckPath}" in {
        (mockAuthConnector
          .authorise(
            _: Predicate,
            _: Retrieval[
              ((Option[String] ~ Option[Credentials]) ~ Enrolments) ~ Option[
                String
              ] ~ Option[AffinityGroup]
            ]
          )(_: HeaderCarrier, _: ExecutionContext))
          .expects(predicates, retrievals, *, *)
          .returning(Future.successful(retrievalResponse()))

        (mockMultipleAccountsOrchestrator
          .checkValidAccountType(_: List[AccountTypes.Value])(
            _: RequestWithUserDetailsFromSessionAndMongo[AnyContent]
          ))
          .expects(List(PT_ASSIGNED_TO_OTHER_USER), *)
          .returning(
            Left(IncorrectUserType(UrlPaths.returnUrl, randomAccountType))
          )
        mockGetDataFromCacheForActionSuccess(PT_ASSIGNED_TO_OTHER_USER
        )
        mockAccountShouldNotBeThrottled(PT_ASSIGNED_TO_OTHER_USER, NINO, noEnrolments.enrolments)

        val result = controller
          .viewNoSA()
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(UrlPaths.accountCheckPath)
      }
    }

    "the current user has a no PT enrolment on other account but session says it is other account" should {
      "render the error page" in {
        (mockAuthConnector
          .authorise(
            _: Predicate,
            _: Retrieval[
              ((Option[String] ~ Option[Credentials]) ~ Enrolments) ~ Option[
                String
              ] ~ Option[AffinityGroup]
            ]
          )(_: HeaderCarrier, _: ExecutionContext))
          .expects(predicates, retrievals, *, *)
          .returning(
            Future.successful(retrievalResponse(enrolments = saEnrolmentOnly))
          )

        (mockMultipleAccountsOrchestrator
          .checkValidAccountType(_: List[AccountTypes.Value])(
            _: RequestWithUserDetailsFromSessionAndMongo[AnyContent]
          ))
          .expects(List(PT_ASSIGNED_TO_OTHER_USER), *)
          .returning(Right(PT_ASSIGNED_TO_OTHER_USER))

        (mockMultipleAccountsOrchestrator
          .getPTCredentialDetails(
            _: RequestWithUserDetailsFromSessionAndMongo[AnyContent],
            _: HeaderCarrier,
            _: ExecutionContext
          ))
          .expects(*, *, *)
          .returning(createInboundResultError(NoPTEnrolmentWhenOneExpected))
        mockGetDataFromCacheForActionSuccess(randomAccountType)
        mockAccountShouldNotBeThrottled(randomAccountType, NINO, saEnrolmentOnly.enrolments)

        val res = controller
          .viewNoSA()
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(res) shouldBe INTERNAL_SERVER_ERROR
        contentAsString(res) should include("enrolmentError.heading")
      }
    }

  }

  "viewSA" when {

    specificThrottleTests(controller.viewSA())

    "a user has SA on another account" should {
      "render the ReportSuspiciousID page" when {
        "the user hasn't already been assigned a PT enrolment" in {
          (mockAuthConnector
            .authorise(
              _: Predicate,
              _: Retrieval[
                ((Option[String] ~ Option[Credentials]) ~ Enrolments) ~ Option[
                  String
                ] ~ Option[AffinityGroup]
              ]
            )(_: HeaderCarrier, _: ExecutionContext))
            .expects(predicates, retrievals, *, *)
            .returning(Future.successful(retrievalResponse()))

          (mockMultipleAccountsOrchestrator
            .checkValidAccountType(_: List[AccountTypes.Value])(
              _: RequestWithUserDetailsFromSessionAndMongo[AnyContent]
            ))
            .expects(List(SA_ASSIGNED_TO_OTHER_USER), *)
            .returning(Right(SA_ASSIGNED_TO_OTHER_USER))

          (mockMultipleAccountsOrchestrator
            .getSACredentialDetails(
              _: RequestWithUserDetailsFromSessionAndMongo[AnyContent],
              _: HeaderCarrier,
              _: ExecutionContext
            ))
            .expects(*, *, *)
            .returning(createInboundResult(accountDetails))
          mockGetDataFromCacheForActionSuccess(SA_ASSIGNED_TO_OTHER_USER)
          mockAccountShouldNotBeThrottled(SA_ASSIGNED_TO_OTHER_USER, NINO, noEnrolments.enrolments)

          val auditEvent = AuditEvent.auditReportSuspiciousSAAccount(
            accountDetails.copy(lastLoginDate = "27 common.month2 2022 common.dateToTime 12:00 PM")
          )(requestWithAccountType(SA_ASSIGNED_TO_OTHER_USER), stubbedMessagesApi)
          (mockAuditHandler
            .audit(_: AuditEvent)(_: HeaderCarrier))
            .expects(auditEvent, *)
            .returning(Future.successful((): Unit))
            .once()

          val result = controller
            .viewSA()
            .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

          status(result) shouldBe OK
          contentAsString(result) should include("ReportSuspiciousID.heading")
        }

        "the user has already been assigned a PT enrolment" in {
          (mockAuthConnector
            .authorise(
              _: Predicate,
              _: Retrieval[
                ((Option[String] ~ Option[Credentials]) ~ Enrolments) ~ Option[
                  String
                ] ~ Option[AffinityGroup]
              ]
            )(_: HeaderCarrier, _: ExecutionContext))
            .expects(predicates, retrievals, *, *)
            .returning(Future.successful(retrievalResponse(enrolments = ptEnrolmentOnly)))

          (mockMultipleAccountsOrchestrator
            .checkValidAccountType(_: List[AccountTypes.Value])(
              _: RequestWithUserDetailsFromSessionAndMongo[AnyContent]
            ))
            .expects(List(SA_ASSIGNED_TO_OTHER_USER), *)
            .returning(Right(SA_ASSIGNED_TO_OTHER_USER))

          (mockMultipleAccountsOrchestrator
            .getSACredentialDetails(
              _: RequestWithUserDetailsFromSessionAndMongo[AnyContent],
              _: HeaderCarrier,
              _: ExecutionContext
            ))
            .expects(*, *, *)
            .returning(createInboundResult(accountDetails))
          mockGetDataFromCacheForActionSuccess(SA_ASSIGNED_TO_OTHER_USER)
          mockAccountShouldNotBeThrottled(SA_ASSIGNED_TO_OTHER_USER, NINO, ptEnrolmentOnly.enrolments)

          val result = controller
            .viewSA()
            .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

          status(result) shouldBe OK
          contentAsString(result) should include("ReportSuspiciousID.heading")
        }
      }
    }
    s"the cache no redirectUrl" should {
      "render the error page" in {
        (mockAuthConnector
          .authorise(
            _: Predicate,
            _: Retrieval[
              ((Option[String] ~ Option[Credentials]) ~ Enrolments) ~ Option[
                String
              ] ~ Option[AffinityGroup]
            ]
          )(_: HeaderCarrier, _: ExecutionContext))
          .expects(predicates, retrievals, *, *)
          .returning(Future.successful(retrievalResponse()))
        mockGetDataFromCacheForActionNoRedirectUrl

        val res = controller
          .viewNoSA()
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(res) shouldBe INTERNAL_SERVER_ERROR
        contentAsString(res) should include("enrolmentError.heading")
      }
    }

    s"the user does not have an account type of $SA_ASSIGNED_TO_OTHER_USER" should {
      s"redirect to ${UrlPaths.accountCheckPath}" in {
        (mockAuthConnector
          .authorise(
            _: Predicate,
            _: Retrieval[
              ((Option[String] ~ Option[Credentials]) ~ Enrolments) ~ Option[
                String
              ] ~ Option[AffinityGroup]
            ]
          )(_: HeaderCarrier, _: ExecutionContext))
          .expects(predicates, retrievals, *, *)
          .returning(Future.successful(retrievalResponse()))

        (mockMultipleAccountsOrchestrator
          .checkValidAccountType(_: List[AccountTypes.Value])(
            _: RequestWithUserDetailsFromSessionAndMongo[AnyContent]
          ))
          .expects(List(SA_ASSIGNED_TO_OTHER_USER), *)
          .returning(
            Left(IncorrectUserType(UrlPaths.returnUrl, randomAccountType))
          )
        mockGetDataFromCacheForActionSuccess(randomAccountType)
        mockAccountShouldNotBeThrottled(randomAccountType, NINO, noEnrolments.enrolments)

        val result = controller
          .viewSA()
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(UrlPaths.accountCheckPath)
      }
    }

    "the current user has a no SA enrolment on other account but session says it is other account" should {
      "render the error page" in {
        (mockAuthConnector
          .authorise(
            _: Predicate,
            _: Retrieval[
              ((Option[String] ~ Option[Credentials]) ~ Enrolments) ~ Option[
                String
              ] ~ Option[AffinityGroup]
            ]
          )(_: HeaderCarrier, _: ExecutionContext))
          .expects(predicates, retrievals, *, *)
          .returning(Future.successful(retrievalResponse()))

        (mockMultipleAccountsOrchestrator
          .checkValidAccountType(_: List[AccountTypes.Value])(
            _: RequestWithUserDetailsFromSessionAndMongo[AnyContent]
          ))
          .expects(List(SA_ASSIGNED_TO_OTHER_USER), *)
          .returning(Right(SA_ASSIGNED_TO_OTHER_USER))

        (mockMultipleAccountsOrchestrator
          .getSACredentialDetails(
            _: RequestWithUserDetailsFromSessionAndMongo[AnyContent],
            _: HeaderCarrier,
            _: ExecutionContext
          ))
          .expects(*, *, *)
          .returning(createInboundResultError(NoSAEnrolmentWhenOneExpected))
        mockGetDataFromCacheForActionSuccess(randomAccountType)
        mockAccountShouldNotBeThrottled(randomAccountType, NINO, noEnrolments.enrolments)

        val res = controller
          .viewSA()
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(res) shouldBe INTERNAL_SERVER_ERROR
        contentAsString(res) should include("enrolmentError.heading")
      }
    }
  }

  "continue" when {

    specificThrottleTests(controller.continue())

    "the user has SA assigned to another user and not already enrolled for PT" should {
      s"enrol for PT and redirect to ${UrlPaths.enrolledPTSAOnOtherAccountPath}" in {
        val additionalCacheData = Map(USER_ASSIGNED_SA_ENROLMENT -> Json.toJson(UsersAssignedEnrolment1),
          accountDetailsForCredential(CREDENTIAL_ID_1) -> Json.toJson(accountDetails))
        val sessionData = generateBasicCacheData(SA_ASSIGNED_TO_OTHER_USER, UrlPaths.returnUrl) ++ additionalCacheData
        (mockAuthConnector
          .authorise(
            _: Predicate,
            _: Retrieval[
              ((Option[String] ~ Option[Credentials]) ~ Enrolments) ~ Option[
                String
              ] ~ Option[AffinityGroup]
            ]
          )(_: HeaderCarrier, _: ExecutionContext))
          .expects(predicates, retrievals, *, *)
          .returning(Future.successful(retrievalResponse()))

        (mockTeaSessionCache
          .save(_: String, _: Boolean)(
            _: RequestWithUserDetailsFromSession[AnyContent],
            _: Format[Boolean]
          ))
          .expects(REPORTED_FRAUD, true, *, *)
          .returning(Future(CacheMap(request.sessionID, sessionData)))

        (mockMultipleAccountsOrchestrator
          .checkValidAccountTypeAndEnrolForPT(_: AccountTypes.Value)(
            _: RequestWithUserDetailsFromSessionAndMongo[AnyContent],
            _: HeaderCarrier,
            _: ExecutionContext
          ))
          .expects(SA_ASSIGNED_TO_OTHER_USER, *, *, *)
          .returning(createInboundResult((): Unit))
        mockGetDataFromCacheForActionSuccess(SA_ASSIGNED_TO_OTHER_USER, UrlPaths.returnUrl, additionalCacheData)
        mockAccountShouldNotBeThrottled(SA_ASSIGNED_TO_OTHER_USER, NINO, noEnrolments.enrolments)
        val auditEvent = AuditEvent.auditSuccessfullyEnrolledPTWhenSAOnOtherAccount(true
        )(requestWithAccountType(SA_ASSIGNED_TO_OTHER_USER, UrlPaths.returnUrl, additionalCacheData = additionalCacheData),
          stubbedMessagesApi)
        (mockAuditHandler
          .audit(_: AuditEvent)(_: HeaderCarrier))
          .expects(auditEvent, *)
          .returning(Future.successful((): Unit))
          .once()
        val res = controller
          .continue()
          .apply(buildFakeRequestWithSessionId("POST", "Not Used"))

        status(res) shouldBe SEE_OTHER
        redirectLocation(res) shouldBe Some(
          UrlPaths.enrolledPTNoSAOnAnyAccountPath
        )
      }
    }

    "the user has SA assigned to another user and has already been enrolled for PT" should {
      s"not enrol for PT again and redirect to ${UrlPaths.enrolledPTSAOnOtherAccountPath}" in {
        val additionalCacheData = Map(USER_ASSIGNED_SA_ENROLMENT -> Json.toJson(UsersAssignedEnrolment1),
          accountDetailsForCredential(CREDENTIAL_ID_1) -> Json.toJson(accountDetails))
        val sessionData = generateBasicCacheData(SA_ASSIGNED_TO_OTHER_USER, UrlPaths.returnUrl) ++ additionalCacheData
        (mockAuthConnector
          .authorise(
            _: Predicate,
            _: Retrieval[
              ((Option[String] ~ Option[Credentials]) ~ Enrolments) ~ Option[
                String
              ] ~ Option[AffinityGroup]
            ]
          )(_: HeaderCarrier, _: ExecutionContext))
          .expects(predicates, retrievals, *, *)
          .returning(Future.successful(retrievalResponse(enrolments = ptEnrolmentOnly)))

        (mockTeaSessionCache
          .save(_: String, _: Boolean)(
            _: RequestWithUserDetailsFromSession[AnyContent],
            _: Format[Boolean]
          ))
          .expects(REPORTED_FRAUD, true, *, *)
          .returning(Future(CacheMap(request.sessionID, sessionData)))

        (mockMultipleAccountsOrchestrator
          .checkValidAccountTypeAndEnrolForPT(_: AccountTypes.Value)(
            _: RequestWithUserDetailsFromSessionAndMongo[AnyContent],
            _: HeaderCarrier,
            _: ExecutionContext
          ))
          .expects(SA_ASSIGNED_TO_OTHER_USER, *, *, *)
          .returning(createInboundResultError(UnexpectedPTEnrolment(SA_ASSIGNED_TO_OTHER_USER)))
        mockGetDataFromCacheForActionSuccess(SA_ASSIGNED_TO_OTHER_USER, UrlPaths.returnUrl, additionalCacheData)
        mockAccountShouldNotBeThrottled(SA_ASSIGNED_TO_OTHER_USER, NINO, ptEnrolmentOnly.enrolments)

        val res = controller
          .continue()
          .apply(buildFakeRequestWithSessionId("POST", "Not Used"))

        status(res) shouldBe SEE_OTHER
        redirectLocation(res) shouldBe Some(
          UrlPaths.enrolledPTSAOnOtherAccountPath
        )
      }
    }

    s"the cache no redirectUrl" should {
      "render the error page" in {
        (mockAuthConnector
          .authorise(
            _: Predicate,
            _: Retrieval[
              ((Option[String] ~ Option[Credentials]) ~ Enrolments) ~ Option[
                String
              ] ~ Option[AffinityGroup]
            ]
          )(_: HeaderCarrier, _: ExecutionContext))
          .expects(predicates, retrievals, *, *)
          .returning(Future.successful(retrievalResponse()))
        mockGetDataFromCacheForActionNoRedirectUrl

        val res = controller
          .continue()
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(res) shouldBe INTERNAL_SERVER_ERROR
        contentAsString(res) should include("enrolmentError.heading")
      }
    }

    "the user has not got SA assigned to another user" should {
      s"redirect to ${UrlPaths.accountCheckPath}" in {
        (mockAuthConnector
          .authorise(
            _: Predicate,
            _: Retrieval[
              ((Option[String] ~ Option[Credentials]) ~ Enrolments) ~ Option[
                String
              ] ~ Option[AffinityGroup]
            ]
          )(_: HeaderCarrier, _: ExecutionContext))
          .expects(predicates, retrievals, *, *)
          .returning(Future.successful(retrievalResponse()))

        (mockTeaSessionCache
          .save(_: String, _: Boolean)(
            _: RequestWithUserDetailsFromSession[AnyContent],
            _: Format[Boolean]
          ))
          .expects(REPORTED_FRAUD, true, *, *)
          .returning(Future(CacheMap(request.sessionID, Map())))

        (mockMultipleAccountsOrchestrator
          .checkValidAccountTypeAndEnrolForPT(_: AccountTypes.Value)(
            _: RequestWithUserDetailsFromSessionAndMongo[AnyContent],
            _: HeaderCarrier,
            _: ExecutionContext
          ))
          .expects(SA_ASSIGNED_TO_OTHER_USER, *, *, *)
          .returning(
            createInboundResultError(IncorrectUserType(UrlPaths.returnUrl, randomAccountType))
          )
        mockGetDataFromCacheForActionSuccess(randomAccountType)
        mockAccountShouldNotBeThrottled(randomAccountType, NINO, noEnrolments.enrolments)

        val res = controller
          .continue()
          .apply(buildFakeRequestWithSessionId("POST", "Not Used"))

        status(res) shouldBe SEE_OTHER
        redirectLocation(res) shouldBe
          Some(UrlPaths.accountCheckPath)
      }
    }

    "the user has SA assigned to another user but enrolment to PT is unsuccessful" should {
      "render the error view" in {
        (mockAuthConnector
          .authorise(
            _: Predicate,
            _: Retrieval[
              ((Option[String] ~ Option[Credentials]) ~ Enrolments) ~ Option[
                String
              ] ~ Option[AffinityGroup]
            ]
          )(_: HeaderCarrier, _: ExecutionContext))
          .expects(predicates, retrievals, *, *)
          .returning(Future.successful(retrievalResponse()))

        (mockTeaSessionCache
          .save(_: String, _: Boolean)(
            _: RequestWithUserDetailsFromSession[AnyContent],
            _: Format[Boolean]
          ))
          .expects(REPORTED_FRAUD, true, *, *)
          .returning(Future(CacheMap(request.sessionID, Map())))

        (mockMultipleAccountsOrchestrator
          .checkValidAccountTypeAndEnrolForPT(_: AccountTypes.Value)(
            _: RequestWithUserDetailsFromSessionAndMongo[AnyContent],
            _: HeaderCarrier,
            _: ExecutionContext
          ))
          .expects(SA_ASSIGNED_TO_OTHER_USER, *, *, *)
          .returning(
            createInboundResultError(UnexpectedResponseFromTaxEnrolments)
          )
        mockGetDataFromCacheForActionSuccess(randomAccountType)
        mockAccountShouldNotBeThrottled(randomAccountType, NINO, noEnrolments.enrolments)

        val res = controller
          .continue()
          .apply(buildFakeRequestWithSessionId("POST", "Not Used"))

        status(res) shouldBe INTERNAL_SERVER_ERROR
        contentAsString(res) should include("enrolmentError.heading")
      }
    }
  }
}
