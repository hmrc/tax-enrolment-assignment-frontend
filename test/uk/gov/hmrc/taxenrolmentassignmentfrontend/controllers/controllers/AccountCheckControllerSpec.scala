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

package uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.controllers

import cats.data.EitherT
import play.api.http.Status.{INTERNAL_SERVER_ERROR, OK, SEE_OTHER}
import play.api.mvc.AnyContent
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.Enrolments
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve.{Credentials, Retrieval, ~}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.auth.RequestWithUserDetails
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.helpers.TestData._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.helpers.TestFixture
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.{
  AccountCheckController,
  testOnly
}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.{
  TaxEnrolmentAssignmentErrors,
  UnexpectedResponseFromIV,
  UnexpectedResponseFromTaxEnrolments
}

import scala.concurrent.{ExecutionContext, Future}

class AccountCheckControllerSpec extends TestFixture {

  val teaSessionCache = new TestTeaSessionCache

  val controller = new AccountCheckController(
    mockAccountCheckOrchestrator,
    mockSilentAssignmentService,
    mockAuthAction,
    testAppConfig,
    mcc,
    teaSessionCache,
    logger,
    errorView
  )

  "accountCheck" when {
    "a single credential exists for a given nino with no PT enrolment" should {
      "silently assign the HMRC-PT Enrolment and redirect to PTA" in new TestHelper {
        mockAuthCall()
        mockAccountCheckSuccess(SINGLE_ACCOUNT)
        mockSilentEnrolSuccess

        val result = controller
          .accountCheck(testOnly.routes.TestOnlyController.successfulCall.url)
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(
          "http://localhost:9232/personal-account"
        )
      }

      "return an error page if there was an error assigning the enrolment" in new TestHelper {
        mockAuthCall()
        mockAccountCheckSuccess(SINGLE_ACCOUNT)
        mockSilentEnrolFailure

        val result = controller
          .accountCheck(testOnly.routes.TestOnlyController.successfulCall.url)
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(result) shouldBe OK
        contentAsString(result) should include("enrolmentError.title")
      }
    }

    "a single credential exists for a given nino that is already enrolled for PT" should {
      "redirect to the return url" in new TestHelper {
        mockAuthCall()
        mockAccountCheckSuccess(PT_ASSIGNED_TO_CURRENT_USER)

        val result = controller
          .accountCheck(testOnly.routes.TestOnlyController.successfulCall.url)
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(
          "/tax-enrolment-assignment-frontend/test-only/successful"
        )
      }
    }

    "a PT enrolment exists on another users account" should {
      "redirect to the PT Enrolment on another account" in new TestHelper {
        mockAuthCall()
        mockAccountCheckSuccess(PT_ASSIGNED_TO_OTHER_USER)

        val result = controller
          .accountCheck(testOnly.routes.TestOnlyController.successfulCall.url)
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(
          "/tax-enrolment-assignment-frontend/pt-enrolment-other-account"
        )
      }
    }

    "multiple credential exists for a given nino and no enrolments exists" should {
      "redirect to the landing page" in new TestHelper {
        mockAuthCall()
        mockAccountCheckSuccess(MULTIPLE_ACCOUNTS)
        mockSilentEnrolSuccess

        val result = controller
          .accountCheck(testOnly.routes.TestOnlyController.successfulCall.url)
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(
          "/tax-enrolment-assignment-frontend/enrol-pt/introduction"
        )
      }
    }

    "multiple credential exists for a given nino and current credential has SA enrolment" should {
      "redirect to the landing page" in new TestHelper {
        mockAuthCall()
        mockAccountCheckSuccess(SA_ASSIGNED_TO_CURRENT_USER)
        mockSilentEnrolSuccess

        val result = controller
          .accountCheck(testOnly.routes.TestOnlyController.successfulCall.url)
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(
          "/tax-enrolment-assignment-frontend/enrol-pt/introduction"
        )
      }
    }

    "multiple credential exists for a given nino and a non signed in account has SA enrolment" should {
      "return OK" in new TestHelper {
        mockAuthCall()
        mockAccountCheckSuccess(SA_ASSIGNED_TO_OTHER_USER)

        val result = controller
          .accountCheck(testOnly.routes.TestOnlyController.successfulCall.url)
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(result) shouldBe OK
        contentAsString(result) should include("SA on other account")
      }
    }

    "a no credentials exists in IV for a given nino" should {
      "return InternalServerError" in new TestHelper {
        mockAuthCall()
        mockAccountCheckFailure(UnexpectedResponseFromIV)

        val result = controller
          .accountCheck(testOnly.routes.TestOnlyController.successfulCall.url)
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(result) shouldBe INTERNAL_SERVER_ERROR
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
            ]
          ]
        )(_: HeaderCarrier, _: ExecutionContext))
        .expects(predicates, retrievals, *, *)
        .returning(Future.successful(retrievalResponse()))

    def mockAccountCheckFailure(error: TaxEnrolmentAssignmentErrors) = {
      (mockAccountCheckOrchestrator
        .getAccountType(
          _: ExecutionContext,
          _: HeaderCarrier,
          _: RequestWithUserDetails[AnyContent]
        ))
        .expects(*, *, *)
        .returning(createInboundResultError(error))
    }

    def mockAccountCheckSuccess(accountType: AccountTypes.Value) = {
      (mockAccountCheckOrchestrator
        .getAccountType(
          _: ExecutionContext,
          _: HeaderCarrier,
          _: RequestWithUserDetails[AnyContent]
        ))
        .expects(*, *, *)
        .returning(createInboundResult(accountType))
    }

    def mockSilentEnrolSuccess =
      (mockSilentAssignmentService
        .enrolUser()(
          _: RequestWithUserDetails[AnyContent],
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
          _: RequestWithUserDetails[AnyContent],
          _: HeaderCarrier,
          _: ExecutionContext
        ))
        .expects(*, *, *)
        .returning(
          createInboundResultError(UnexpectedResponseFromTaxEnrolments)
        )
  }
}
