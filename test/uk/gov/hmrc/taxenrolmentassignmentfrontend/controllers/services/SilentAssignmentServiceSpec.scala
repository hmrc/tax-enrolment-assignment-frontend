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

import org.scalatest.concurrent.ScalaFutures
import play.api.libs.json.Format
import play.api.mvc.AnyContent
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.auth.RequestWithUserDetails
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.helpers.TestData._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.helpers.TestFixture
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.UnexpectedResponseFromEACD
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.{
  AccountDetails,
  IVNinoStoreEntry,
  UserEnrolmentsListResponse
}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.SessionKeys.OTHER_VALID_PTA_ACCOUNTS
import uk.gov.hmrc.taxenrolmentassignmentfrontend.services.SilentAssignmentService

import scala.concurrent.{ExecutionContext, Future}

class SilentAssignmentServiceSpec extends TestFixture with ScalaFutures {

  val service = new SilentAssignmentService(
    mockIVConnector,
    mockTaxEnrolmentsConnector,
    mockEacdConnector,
    mockTeaSessionCache
  )

  val businessEnrolmentResponse: UserEnrolmentsListResponse =
    UserEnrolmentsListResponse(Seq(userEnrolmentIRPAYE))
  val irsaResponse: UserEnrolmentsListResponse = UserEnrolmentsListResponse(
    Seq(userEnrolmentIRSA)
  )

  "getOtherAccountsWithPTAAccess" when {
    "a record is in the cache" that {
      "has no other accounts" should {
        "return a empty list and not call IV" in {
          (mockTeaSessionCache
            .getEntry(_: String)(
              _: RequestWithUserDetails[AnyContent],
              _: Format[Seq[IVNinoStoreEntry]]
            ))
            .expects(OTHER_VALID_PTA_ACCOUNTS, *, *)
            .returning(Future.successful(Some(Seq.empty)))

          val res = service.getOtherAccountsWithPTAAccess

          whenReady(res.value) { result =>
            result shouldBe Right(Seq.empty)
          }
        }
      }

      "has other PTA accessible accounts" should {
        "return the list of other accounts" in {
          val otherValidPTAAccounts = Seq(ivNinoStoreEntry2, ivNinoStoreEntry3)
          (mockTeaSessionCache
            .getEntry(_: String)(
              _: RequestWithUserDetails[AnyContent],
              _: Format[Seq[IVNinoStoreEntry]]
            ))
            .expects(OTHER_VALID_PTA_ACCOUNTS, *, *)
            .returning(Future.successful(Some(otherValidPTAAccounts)))

          val res = service.getOtherAccountsWithPTAAccess

          whenReady(res.value) { result =>
            result shouldBe Right(otherValidPTAAccounts)
          }
        }
      }
    }

    "a record is not in the cache" should {
      "save to cache and return an empty list" when {
        "all CL200 accounts have a business enrolment" in {
          (mockTeaSessionCache
            .getEntry(_: String)(
              _: RequestWithUserDetails[AnyContent],
              _: Format[Seq[IVNinoStoreEntry]]
            ))
            .expects(OTHER_VALID_PTA_ACCOUNTS, *, *)
            .returning(Future.successful(None))
          (mockIVConnector
            .getCredentialsWithNino(_: String)(
              _: ExecutionContext,
              _: HeaderCarrier
            ))
            .expects(NINO, *, *)
            .returning(createInboundResult(multiIVCreds))
          (mockEacdConnector
            .queryEnrolmentsAssignedToUser(_: String)(
              _: HeaderCarrier,
              _: ExecutionContext
            ))
            .expects("8316291481001919", *, *)
            .returning(createInboundResult(Some(businessEnrolmentResponse)))

          (mockEacdConnector
            .queryEnrolmentsAssignedToUser(_: String)(
              _: HeaderCarrier,
              _: ExecutionContext
            ))
            .expects("0493831301037584", *, *)
            .returning(createInboundResult(Some(businessEnrolmentResponse)))

          (mockEacdConnector
            .queryEnrolmentsAssignedToUser(_: String)(
              _: HeaderCarrier,
              _: ExecutionContext
            ))
            .expects("2884521810163541", *, *)
            .returning(createInboundResult(Some(businessEnrolmentResponse)))

          (mockTeaSessionCache
            .save(_: String, _: Seq[IVNinoStoreEntry])(
              _: RequestWithUserDetails[AnyContent],
              _: Format[Seq[IVNinoStoreEntry]]
            ))
            .expects(OTHER_VALID_PTA_ACCOUNTS, Seq.empty, *, *)
            .returning(Future(CacheMap(request.sessionID, Map())))

          val res = service.getOtherAccountsWithPTAAccess

          whenReady(res.value) { result =>
            result shouldBe Right(Seq.empty)
          }
        }

        "there are more than 10 CL200 accounts" in {
          (mockTeaSessionCache
            .getEntry(_: String)(
              _: RequestWithUserDetails[AnyContent],
              _: Format[Seq[IVNinoStoreEntry]]
            ))
            .expects(OTHER_VALID_PTA_ACCOUNTS, *, *)
            .returning(Future.successful(None))

          (mockIVConnector
            .getCredentialsWithNino(_: String)(
              _: ExecutionContext,
              _: HeaderCarrier
            ))
            .expects(NINO, *, *)
            .returning(createInboundResult(multiCL200IVCreds))

          (mockTeaSessionCache
            .save(_: String, _: Seq[IVNinoStoreEntry])(
              _: RequestWithUserDetails[AnyContent],
              _: Format[Seq[IVNinoStoreEntry]]
            ))
            .expects(OTHER_VALID_PTA_ACCOUNTS, Seq.empty, *, *)
            .returning(Future(CacheMap(request.sessionID, Map())))

          val res = service.getOtherAccountsWithPTAAccess

          whenReady(res.value) { result =>
            result shouldBe Right(Seq.empty)
          }
        }
      }

    }
  }

  "getValidPtaAccounts" should {
    "return an empty list" when {

      //TODO is this incorrect handling? Should we not ignore the errors and retry
      "if the calls to EACD respond with an error" in {
        (mockEacdConnector
          .queryEnrolmentsAssignedToUser(_: String)(
            _: HeaderCarrier,
            _: ExecutionContext
          ))
          .expects("8316291481001919", *, *)
          .returning(createInboundResultError(UnexpectedResponseFromEACD))

        (mockEacdConnector
          .queryEnrolmentsAssignedToUser(_: String)(
            _: HeaderCarrier,
            _: ExecutionContext
          ))
          .expects("0493831301037584", *, *)
          .returning(createInboundResultError(UnexpectedResponseFromEACD))

        (mockEacdConnector
          .queryEnrolmentsAssignedToUser(_: String)(
            _: HeaderCarrier,
            _: ExecutionContext
          ))
          .expects("2884521810163541", *, *)
          .returning(createInboundResultError(UnexpectedResponseFromEACD))

        val result = service.getValidPtaAccounts(multiIVCreds)(hc, ec)
        await(result) shouldBe Seq(None, None, None)
      }

      "return a populated list" when {
        val validPtaList = Seq(
          Some(ivNinoStoreEntry2),
          Some(ivNinoStoreEntry3),
          Some(ivNinoStoreEntry4)
        )

        "the accounts have no enrolments" in {
          (mockEacdConnector
            .queryEnrolmentsAssignedToUser(_: String)(
              _: HeaderCarrier,
              _: ExecutionContext
            ))
            .expects("8316291481001919", *, *)
            .returning(createInboundResult(None))

          (mockEacdConnector
            .queryEnrolmentsAssignedToUser(_: String)(
              _: HeaderCarrier,
              _: ExecutionContext
            ))
            .expects("0493831301037584", *, *)
            .returning(createInboundResult(None))

          (mockEacdConnector
            .queryEnrolmentsAssignedToUser(_: String)(
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
            .queryEnrolmentsAssignedToUser(_: String)(
              _: HeaderCarrier,
              _: ExecutionContext
            ))
            .expects("8316291481001919", *, *)
            .returning(createInboundResult(Some(irsaResponse)))

          (mockEacdConnector
            .queryEnrolmentsAssignedToUser(_: String)(
              _: HeaderCarrier,
              _: ExecutionContext
            ))
            .expects("0493831301037584", *, *)
            .returning(createInboundResult(Some(irsaResponse)))

          (mockEacdConnector
            .queryEnrolmentsAssignedToUser(_: String)(
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
