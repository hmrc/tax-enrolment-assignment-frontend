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

import cats.data.EitherT
import play.api.http.Status.SEE_OTHER
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve.{Credentials, Retrieval, ~}
import uk.gov.hmrc.auth.core.{AffinityGroup, Enrolments}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.actions.RequestWithUserDetailsFromSession
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.{TaxEnrolmentAssignmentErrors, UnexpectedResponseFromIV, UnexpectedResponseFromTaxEnrolments}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.TestData._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.{TestFixture, UrlPaths}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.reporting.AuditEvent

import scala.concurrent.{ExecutionContext, Future}

class AccountCheckControllerSpec extends TestFixture {

  val teaSessionCache = new TestTeaSessionCache

  val controller = new AccountCheckController(
    mockSilentAssignmentService,
    mockThrottleAction,
    mockAuthAction,
    mockAccountCheckOrchestrator,
    mockAuditHandler,
    mcc,
    teaSessionCache,
    logger,
    errorHandler,
    errorView
  )

  "accountCheck" when {
    "a single credential exists for a given nino with no PT enrolment" should {
      s"silently assign the HMRC-PT Enrolment and redirect to users redirect url" in new TestHelper {
        mockAuthCall()
        mockAccountCheckSuccess(SINGLE_ACCOUNT)
        mockSilentEnrolSuccess
        mockAuditPTEnrolled(SINGLE_ACCOUNT, requestWithUserDetails())
        mockAccountShouldNotBeThrottled(SINGLE_ACCOUNT, NINO, noEnrolments.enrolments)

        val result = controller
          .accountCheck(testOnly.routes.TestOnlyController.successfulCall.url)
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(testOnly.routes.TestOnlyController.successfulCall.url)
      }

      "return an error page if there was an error assigning the enrolment" in new TestHelper {
        mockAuthCall()
        mockAccountCheckSuccess(SINGLE_ACCOUNT)
        mockSilentEnrolFailure
        mockAccountShouldNotBeThrottled(SINGLE_ACCOUNT, NINO, noEnrolments.enrolments)

        val result = controller
          .accountCheck(testOnly.routes.TestOnlyController.successfulCall.url)
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(result) shouldBe INTERNAL_SERVER_ERROR
        contentAsString(result) should include("enrolmentError.title")
      }
    }

    "a single credential exists for a given nino that is already enrolled for PT" should {
      s"redirect to ${UrlPaths.returnUrl}" in new TestHelper {
        mockAuthCall()
        mockAccountCheckSuccess(PT_ASSIGNED_TO_CURRENT_USER)
        mockAccountShouldNotBeThrottled(PT_ASSIGNED_TO_CURRENT_USER, NINO, noEnrolments.enrolments)

        val result = controller
          .accountCheck(testOnly.routes.TestOnlyController.successfulCall.url)
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(UrlPaths.returnUrl)
      }
    }

    "a PT enrolment exists on another users account" should {
      s"redirect to ${UrlPaths.ptOnOtherAccountPath}" in new TestHelper {
        mockAuthCall()
        mockAccountCheckSuccess(PT_ASSIGNED_TO_OTHER_USER)
        mockAccountShouldNotBeThrottled(PT_ASSIGNED_TO_OTHER_USER, NINO, noEnrolments.enrolments)

        val result = controller
          .accountCheck(testOnly.routes.TestOnlyController.successfulCall.url)
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(UrlPaths.ptOnOtherAccountPath)
      }
    }

    "multiple credential exists for a given nino and no enrolments exists" should {
      s"redirect to ${UrlPaths.enrolledPTNoSAOnAnyAccountPath}" in new TestHelper {
        mockAuthCall()
        mockAccountCheckSuccess(MULTIPLE_ACCOUNTS)
        mockSilentEnrolSuccess
        mockAccountShouldNotBeThrottled(MULTIPLE_ACCOUNTS, NINO, noEnrolments.enrolments)
        mockAuditPTEnrolled(MULTIPLE_ACCOUNTS, requestWithUserDetails())

        val result = controller
          .accountCheck(testOnly.routes.TestOnlyController.successfulCall.url)
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(
          UrlPaths.enrolledPTNoSAOnAnyAccountPath
        )
      }
    }

    "multiple credential exists for a given nino and current credential has SA enrolment" should {
      s"redirect to ${UrlPaths.enrolledPTNoSAOnAnyAccountPath}" in new TestHelper {
        mockAuthCallWithSA()
        mockAccountCheckSuccess(SA_ASSIGNED_TO_CURRENT_USER)
        mockSilentEnrolSuccess
        mockAccountShouldNotBeThrottled(SA_ASSIGNED_TO_CURRENT_USER, NINO, saEnrolmentOnly.enrolments)
        mockAuditPTEnrolled(SA_ASSIGNED_TO_CURRENT_USER, requestWithUserDetails(userDetailsWithSAEnrolment))

        val result = controller
          .accountCheck(testOnly.routes.TestOnlyController.successfulCall.url)
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(
          UrlPaths.enrolledPTNoSAOnAnyAccountPath
        )
      }
    }

    "multiple credential exists for a given nino and a non signed in account has SA enrolment" should {
      s"redirect ${UrlPaths.saOnOtherAccountInterruptPath}" when {
        "the PT enrolment has not already been assigned" in new TestHelper {
          mockAuthCall()
          mockAccountCheckSuccess(SA_ASSIGNED_TO_OTHER_USER)
          mockAccountShouldNotBeThrottled(SA_ASSIGNED_TO_OTHER_USER, NINO, noEnrolments.enrolments)

          val result = controller
            .accountCheck(testOnly.routes.TestOnlyController.successfulCall.url)
            .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some(
            UrlPaths.saOnOtherAccountInterruptPath
          )
        }
      }


      s"redirect ${UrlPaths.enrolledPTSAOnOtherAccountPath}" when {
        "the PT enrolment has not already been assigned" in new TestHelper {
          mockAuthCallWithPT()
          mockAccountCheckSuccess(SA_ASSIGNED_TO_OTHER_USER)
          mockAccountShouldNotBeThrottled(SA_ASSIGNED_TO_OTHER_USER, NINO, ptEnrolmentOnly.enrolments)

          val result = controller
            .accountCheck(testOnly.routes.TestOnlyController.successfulCall.url)
            .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some(
            UrlPaths.enrolledPTSAOnOtherAccountPath
          )
        }
      }
    }

    "a no credentials exists in IV for a given nino" should {
      "render the error page" in new TestHelper {
        mockAuthCall()
        mockGetAccountTypeFailure(UnexpectedResponseFromIV)

        val res = controller
          .accountCheck(testOnly.routes.TestOnlyController.successfulCall.url)
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(res) shouldBe INTERNAL_SERVER_ERROR
        contentAsString(res) should include("enrolmentError.title")
      }
    }

    "throttled" should {
      "redirect user to their redirect url" in new TestHelper {
        mockAuthCall()
        mockAccountShouldBeThrottled(SA_ASSIGNED_TO_OTHER_USER, NINO, noEnrolments.enrolments)
        mockAccountCheckSuccess(SA_ASSIGNED_TO_OTHER_USER)

        val res = controller
          .accountCheck(testOnly.routes.TestOnlyController.successfulCall.url)
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(res) shouldBe SEE_OTHER
        redirectLocation(res) shouldBe Some(
          testOnly.routes.TestOnlyController.successfulCall.url
        )
      }

    }
    "throttle returns error" should {
      s"return $INTERNAL_SERVER_ERROR" in new TestHelper {
        mockAuthCall()
        mockErrorFromThrottlingService(SA_ASSIGNED_TO_OTHER_USER, NINO, noEnrolments.enrolments)
        mockAccountCheckSuccess(SA_ASSIGNED_TO_OTHER_USER)

        val res = controller
          .accountCheck(testOnly.routes.TestOnlyController.successfulCall.url)
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(res) shouldBe INTERNAL_SERVER_ERROR
        contentAsString(res) should include("enrolmentError.title")
      }
    }
  }

  class TestHelper {
    def mockAuthCall() =
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

    def mockAuthCallWithSA() =
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
        .returning(Future.successful(retrievalResponse(enrolments = saEnrolmentOnly)))


    def mockAuthCallWithPT() =
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

    def mockGetAccountTypeFailure(error: TaxEnrolmentAssignmentErrors) = {
      (mockAccountCheckOrchestrator
        .getAccountType(
          _: ExecutionContext,
          _: HeaderCarrier,
          _: RequestWithUserDetailsFromSession[_]
        ))
        .expects(*, *, *)
        .returning(createInboundResultError(error))
    }

    def mockAccountCheckSuccess(accountType: AccountTypes.Value) = {
      (mockAccountCheckOrchestrator
        .getAccountType(
          _: ExecutionContext,
          _: HeaderCarrier,
          _: RequestWithUserDetailsFromSession[_]
        ))
        .expects(*, *, *)
        .returning(createInboundResult(accountType))
    }

    def mockSilentEnrolSuccess =
      (mockSilentAssignmentService
        .enrolUser()(
          _: RequestWithUserDetailsFromSession[_],
          _: HeaderCarrier,
          _: ExecutionContext
        ))
        .expects(*, *, *)
        .returning(
          EitherT.right[TaxEnrolmentAssignmentErrors](Future.successful(Unit))
        )

    def mockSilentEnrolFailure =
      (mockSilentAssignmentService
        .enrolUser()(
          _: RequestWithUserDetailsFromSession[_],
          _: HeaderCarrier,
          _: ExecutionContext
        ))
        .expects(*, *, *)
        .returning(
          createInboundResultError(UnexpectedResponseFromTaxEnrolments)
        )

    def mockAuditPTEnrolled(accountType: AccountTypes.Value, requestWithUserDetailsFromSession: RequestWithUserDetailsFromSession[_]) = {
      val expectedAudit = AuditEvent.auditSuccessfullyEnrolledPTWhenSANotOnOtherAccount(accountType)(requestWithUserDetailsFromSession)
      (mockAuditHandler
        .audit(_: AuditEvent)(_: HeaderCarrier))
        .expects(expectedAudit, *)
        .returning(Future.successful((): Unit))
        .once()
    }
  }
}
