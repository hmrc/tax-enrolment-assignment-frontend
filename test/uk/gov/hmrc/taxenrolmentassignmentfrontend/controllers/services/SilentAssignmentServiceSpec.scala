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

package uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.services

import play.api.test.Helpers.{await, defaultAwaitTimeout}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.helpers.TestData._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.helpers.TestFixture
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.UnexpectedResponseFromEACD
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.UserEnrolmentsListResponse
import uk.gov.hmrc.taxenrolmentassignmentfrontend.services.SilentAssignmentService

import scala.concurrent.ExecutionContext

class SilentAssignmentServiceSpec extends TestFixture {

  val service = new SilentAssignmentService(mockTaxEnrolmentsConnector, mockEacdConnector)

  val businessEnrolmentResponse: UserEnrolmentsListResponse = UserEnrolmentsListResponse(Seq(userEnrolmentIRPAYE))
  val irsaResponse: UserEnrolmentsListResponse = UserEnrolmentsListResponse(Seq(userEnrolmentIRSA))

  "getValidPtaAccounts" should {
    "return an empty list" when {
      "all the CL200 accounts have a business enrolment" in {
        (mockEacdConnector
          .queryEnrolmentsAssignedToUser(
            _: String
          )(
            _: HeaderCarrier,
            _: ExecutionContext
          ))
          .expects("8316291481001919", *, *)
          .returning(createInboundResult(Some(businessEnrolmentResponse)))

        (mockEacdConnector
          .queryEnrolmentsAssignedToUser(
            _: String
          )(
            _: HeaderCarrier,
            _: ExecutionContext
          ))
          .expects("0493831301037584", *, *)
          .returning(createInboundResult(Some(businessEnrolmentResponse)))

        (mockEacdConnector
          .queryEnrolmentsAssignedToUser(
            _: String
          )(
            _: HeaderCarrier,
            _: ExecutionContext
          ))
          .expects("2884521810163541", *, *)
          .returning(createInboundResult(Some(businessEnrolmentResponse)))

        val result = service.getValidPtaAccounts(multiIVCreds)(hc, ec)
        await(result) shouldBe Seq(None, None, None)
      }

      "there are more than 10 CL200 accounts" in {
        val result = service.getValidPtaAccounts(multiCL200IVCreds)(hc, ec)
        await(result) shouldBe Seq(None)
      }

      //TODO is this incorrect handling? Should we not ignore the errors and retry
      "if the calls to EACD respond with an error" in {
        (mockEacdConnector
          .queryEnrolmentsAssignedToUser(
            _: String
          )(
            _: HeaderCarrier,
            _: ExecutionContext
          ))
          .expects("8316291481001919", *, *)
          .returning(createInboundResultError(UnexpectedResponseFromEACD))

        (mockEacdConnector
          .queryEnrolmentsAssignedToUser(
            _: String
          )(
            _: HeaderCarrier,
            _: ExecutionContext
          ))
          .expects("0493831301037584", *, *)
          .returning(createInboundResultError(UnexpectedResponseFromEACD))

        (mockEacdConnector
          .queryEnrolmentsAssignedToUser(
            _: String
          )(
            _: HeaderCarrier,
            _: ExecutionContext
          ))
          .expects("2884521810163541", *, *)
          .returning(createInboundResultError(UnexpectedResponseFromEACD))

        val result = service.getValidPtaAccounts(multiIVCreds)(hc, ec)
        await(result) shouldBe Seq(None, None, None)
      }

      "return a populated list" when {
        val validPtaList = Seq(Some(ivNinoStoreEntry2), Some(ivNinoStoreEntry3), Some(ivNinoStoreEntry4))

        "the accounts have no enrolments" in {
          (mockEacdConnector
            .queryEnrolmentsAssignedToUser(
              _: String
            )(
              _: HeaderCarrier,
              _: ExecutionContext
            ))
            .expects("8316291481001919", *, *)
            .returning(createInboundResult(None))

          (mockEacdConnector
            .queryEnrolmentsAssignedToUser(
              _: String
            )(
              _: HeaderCarrier,
              _: ExecutionContext
            ))
            .expects("0493831301037584", *, *)
            .returning(createInboundResult(None))

          (mockEacdConnector
            .queryEnrolmentsAssignedToUser(
              _: String
            )(
              _: HeaderCarrier,
              _: ExecutionContext
            ))
            .expects("2884521810163541", *, *)
            .returning(createInboundResult(None))

          val result = service.getValidPtaAccounts(multiIVCreds)(hc, ec)
          await(result) shouldBe validPtaList

        }

        "the accounts have no business enrolments" in {
          (mockEacdConnector
            .queryEnrolmentsAssignedToUser(
              _: String
            )(
              _: HeaderCarrier,
              _: ExecutionContext
            ))
            .expects("8316291481001919", *, *)
            .returning(createInboundResult(Some(irsaResponse)))

          (mockEacdConnector
            .queryEnrolmentsAssignedToUser(
              _: String
            )(
              _: HeaderCarrier,
              _: ExecutionContext
            ))
            .expects("0493831301037584", *, *)
            .returning(createInboundResult(Some(irsaResponse)))

          (mockEacdConnector
            .queryEnrolmentsAssignedToUser(
              _: String
            )(
              _: HeaderCarrier,
              _: ExecutionContext
            ))
            .expects("2884521810163541", *, *)
            .returning(createInboundResult(Some(irsaResponse)))

          val result = service.getValidPtaAccounts(multiIVCreds)(hc, ec)
          await(result) shouldBe validPtaList
        }
      }
    }
  }
}
