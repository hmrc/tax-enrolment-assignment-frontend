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

package uk.gov.hmrc.taxenrolmentassignmentfrontend.services

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import play.api.libs.json.Format
import play.api.mvc.AnyContent
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.auth.RequestWithUserDetails
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.TestData._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.UnexpectedResponseFromEACD
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.TestFixture
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.{IVNinoStoreEntry, UserEnrolmentsListResponse}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.SessionKeys.OTHER_VALID_PTA_ACCOUNTS
import uk.gov.hmrc.taxenrolmentassignmentfrontend.services.SilentAssignmentService

import scala.concurrent.{ExecutionContext, Future}

class SilentAssignmentServiceSpec extends TestFixture with ScalaFutures {

  implicit val defaultPatience = PatienceConfig(
    timeout = Span(TIME_OUT, Seconds),
    interval = Span(INTERVAL, Millis)
  )

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
        "return a empty list and not call IV" in new TestHelper {
          mockGetCacheOtherValdPTAAccounts(Some(Seq.empty[IVNinoStoreEntry]))

          val res = service.getOtherAccountsWithPTAAccess

          whenReady(res.value) { result =>
            result shouldBe Right(Seq.empty[IVNinoStoreEntry])
          }
        }
      }

      "has other PTA accessible accounts" should {
        "return the list of other accounts" in new TestHelper {
          val otherValidPTAAccounts = Seq(ivNinoStoreEntry2, ivNinoStoreEntry3)
          mockGetCacheOtherValdPTAAccounts(Some(otherValidPTAAccounts))

          val res = service.getOtherAccountsWithPTAAccess

          whenReady(res.value) { result =>
            result shouldBe Right(otherValidPTAAccounts)
          }
        }
      }
    }

    "a record is not in the cache" should {
      "save to cache and return an empty list" when {
        "all CL200 accounts have a business enrolment" in new TestHelper {
          mockGetCacheOtherValdPTAAccounts(None)
          mockIVCall(multiIVCreds)
          mockEACDGetEnrolments(
            "8316291481001919",
            Some(businessEnrolmentResponse)
          )
          mockEACDGetEnrolments(
            "0493831301037584",
            Some(businessEnrolmentResponse)
          )
          mockEACDGetEnrolments(
            "2884521810163541",
            Some(businessEnrolmentResponse)
          ).returning(createInboundResult(Some(businessEnrolmentResponse)))

          mockSaveCacheOtherValidPTAAccounts(Seq.empty[IVNinoStoreEntry])

          val res = service.getOtherAccountsWithPTAAccess

          whenReady(res.value) { result =>
            result shouldBe Right(Seq.empty[IVNinoStoreEntry])
          }
        }

        "there are more than 10 CL200 accounts" in new TestHelper {
          mockGetCacheOtherValdPTAAccounts(None)
          mockIVCall(multiCL200IVCreds)
          mockSaveCacheOtherValidPTAAccounts(Seq.empty[IVNinoStoreEntry])

          val res = service.getOtherAccountsWithPTAAccess

          whenReady(res.value) { result =>
            result shouldBe Right(Seq.empty[IVNinoStoreEntry])
          }
        }

        "IV only returns signed in credential" in new TestHelper {
          mockGetCacheOtherValdPTAAccounts(None)
          mockIVCall(List(ivNinoStoreEntryCurrent))
          mockSaveCacheOtherValidPTAAccounts(Seq.empty[IVNinoStoreEntry])

          val res = service.getOtherAccountsWithPTAAccess

          whenReady(res.value) { result =>
            result shouldBe Right(Seq.empty[IVNinoStoreEntry])
          }
        }

        //TODO is this incorrect handling? Should we not ignore the errors and retry
        "the calls to EACD respond with an error" in new TestHelper {
          mockGetCacheOtherValdPTAAccounts(None)
          mockIVCall(multiIVCreds)
          mockEACDGetEnrolmentsFailure("8316291481001919")
          mockEACDGetEnrolmentsFailure("0493831301037584")
          mockEACDGetEnrolmentsFailure("2884521810163541")
          mockSaveCacheOtherValidPTAAccounts(Seq.empty[IVNinoStoreEntry])

          val res = service.getOtherAccountsWithPTAAccess

          whenReady(res.value) { result =>
            result shouldBe Right(Seq.empty[IVNinoStoreEntry])
          }
        }
      }

      "save to cache and return the other PTA valid credentials" when {
        val validPtaList =
          Seq(ivNinoStoreEntry2, ivNinoStoreEntry3, ivNinoStoreEntry4)

        "the accounts have no enrolments" in new TestHelper {
          mockGetCacheOtherValdPTAAccounts(None)
          mockIVCall(multiIVCreds)
          mockEACDGetEnrolments("8316291481001919", None)
          mockEACDGetEnrolments("0493831301037584", None)
          mockEACDGetEnrolments("2884521810163541", None)
          mockSaveCacheOtherValidPTAAccounts(validPtaList)

          val res = service.getOtherAccountsWithPTAAccess

          whenReady(res.value) { result =>
            result shouldBe Right(validPtaList)
          }
        }

        "the accounts have no business enrolments" in new TestHelper {
          mockGetCacheOtherValdPTAAccounts(None)
          mockIVCall(multiIVCreds)
          mockEACDGetEnrolments("8316291481001919", Some(irsaResponse))
          mockEACDGetEnrolments("0493831301037584", Some(irsaResponse))
          mockEACDGetEnrolments("2884521810163541", Some(irsaResponse))
          mockSaveCacheOtherValidPTAAccounts(validPtaList)

          val res = service.getOtherAccountsWithPTAAccess

          whenReady(res.value) { result =>
            result shouldBe Right(validPtaList)
          }
        }
      }
    }
  }

  class TestHelper {

    def mockGetCacheOtherValdPTAAccounts(resp: Option[Seq[IVNinoStoreEntry]]) =
      (mockTeaSessionCache
        .getEntry(_: String)(
          _: RequestWithUserDetails[AnyContent],
          _: Format[Seq[IVNinoStoreEntry]]
        ))
        .expects(OTHER_VALID_PTA_ACCOUNTS, *, *)
        .returning(Future.successful(resp))
        .once()

    def mockIVCall(resp: List[IVNinoStoreEntry]) =
      (mockIVConnector
        .getCredentialsWithNino(_: String)(
          _: ExecutionContext,
          _: HeaderCarrier
        ))
        .expects(NINO, *, *)
        .returning(createInboundResult(resp))
        .once()

    def mockEACDGetEnrolments(credId: String,
                              resp: Option[UserEnrolmentsListResponse]) =
      (mockEacdConnector
        .queryEnrolmentsAssignedToUser(_: String)(
          _: HeaderCarrier,
          _: ExecutionContext
        ))
        .expects(credId, *, *)
        .returning(createInboundResult(resp))
        .once()

    def mockEACDGetEnrolmentsFailure(credId: String) =
      (mockEacdConnector
        .queryEnrolmentsAssignedToUser(_: String)(
          _: HeaderCarrier,
          _: ExecutionContext
        ))
        .expects(credId, *, *)
        .returning(createInboundResultError(UnexpectedResponseFromEACD))
        .once()

    def mockSaveCacheOtherValidPTAAccounts(
      otherValidPtaAccounts: Seq[IVNinoStoreEntry]
    ) =
      (mockTeaSessionCache
        .save(_: String, _: Seq[IVNinoStoreEntry])(
          _: RequestWithUserDetails[AnyContent],
          _: Format[Seq[IVNinoStoreEntry]]
        ))
        .expects(OTHER_VALID_PTA_ACCOUNTS, otherValidPtaAccounts, *, *)
        .returning(Future(CacheMap(request.sessionID, Map())))
        .once()
  }
}