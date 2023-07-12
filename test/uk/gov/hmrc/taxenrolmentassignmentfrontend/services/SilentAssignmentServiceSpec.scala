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

package uk.gov.hmrc.taxenrolmentassignmentfrontend.services

import play.api.Application
import play.api.inject.bind
import play.api.libs.json.Format
import play.api.mvc.AnyContent
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.taxenrolmentassignmentfrontend.connectors.{EACDConnector, IVConnector, TaxEnrolmentsConnector}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.actions.RequestWithUserDetailsFromSession
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.UnexpectedResponseFromEACD
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.BaseSpec
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.TestData._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.{IVNinoStoreEntry, UserEnrolmentsListResponse}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.SessionKeys.HAS_OTHER_VALID_PTA_ACCOUNTS
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.TEASessionCache

import scala.concurrent.{ExecutionContext, Future}

class SilentAssignmentServiceSpec extends BaseSpec {

  lazy val mockIVConnector = mock[IVConnector]
  lazy val mockTaxEnrolmentsConnector = mock[TaxEnrolmentsConnector]
  lazy val mockEacdConnector = mock[EACDConnector]
  lazy val mockTeaSessionCache = mock[TEASessionCache]

  override lazy val overrides = Seq(
    bind[TEASessionCache].toInstance(mockTeaSessionCache)
  )

  override implicit lazy val app: Application = localGuiceApplicationBuilder()
    .overrides(
      bind[IVConnector].toInstance(mockIVConnector),
      bind[TaxEnrolmentsConnector].toInstance(mockTaxEnrolmentsConnector),
      bind[EACDConnector].toInstance(mockEacdConnector)
    )
    .build()

  val service = app.injector.instanceOf[SilentAssignmentService]

  val businessEnrolmentResponse: UserEnrolmentsListResponse =
    UserEnrolmentsListResponse(Seq(userEnrolmentIRPAYE))
  val irsaResponse: UserEnrolmentsListResponse = UserEnrolmentsListResponse(
    Seq(userEnrolmentIRSA)
  )

  "getOtherAccountsWithPTAAccess" should {
    "save to cache and return false" when {
      "all CL200 accounts have a business enrolment" in new TestHelper {
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

        mockSaveCacheOtherValidPTAAccounts(false)

        val res = service.hasOtherAccountsWithPTAAccess

        whenReady(res.value) { result =>
          result shouldBe Right(false)
        }
      }

      "there are more than 10 CL200 accounts that are all business accounts" in new TestHelper {
        mockIVCall(multiCL200IVCreds)
        multiCL200IVCreds
          .take(10)
          .map(ivEntry =>
            mockEACDGetEnrolments(
              ivEntry.credId,
              Some(businessEnrolmentResponse)
            )
          )

        mockSaveCacheOtherValidPTAAccounts(false)

        val res = service.hasOtherAccountsWithPTAAccess

        whenReady(res.value) { result =>
          result shouldBe Right(false)
        }
      }

      "IV only returns signed in credential" in new TestHelper {
        mockIVCall(List(ivNinoStoreEntryCurrent))
        mockSaveCacheOtherValidPTAAccounts(false)

        val res = service.hasOtherAccountsWithPTAAccess

        whenReady(res.value) { result =>
          result shouldBe Right(false)
        }
      }

      "the calls to EACD respond with an error" in new TestHelper {
        mockIVCall(multiIVCreds)
        mockEACDGetEnrolmentsFailure("8316291481001919")
        mockEACDGetEnrolmentsFailure("0493831301037584")
        mockEACDGetEnrolmentsFailure("2884521810163541")
        mockSaveCacheOtherValidPTAAccounts(false)

        val res = service.hasOtherAccountsWithPTAAccess

        whenReady(res.value) { result =>
          result shouldBe Right(false)
        }
      }
    }

    "save to cache and return true" when {

      "the accounts have no enrolments" in new TestHelper {
        mockIVCall(multiIVCreds)
        mockEACDGetEnrolments("8316291481001919", None)
        mockSaveCacheOtherValidPTAAccounts(true)

        val res = service.hasOtherAccountsWithPTAAccess

        whenReady(res.value) { result =>
          result shouldBe Right(true)
        }
      }

      "the accounts have no business enrolments" in new TestHelper {
        mockIVCall(multiIVCreds)
        mockEACDGetEnrolments("8316291481001919", Some(irsaResponse))
        mockSaveCacheOtherValidPTAAccounts(true)

        val res = service.hasOtherAccountsWithPTAAccess

        whenReady(res.value) { result =>
          result shouldBe Right(true)
        }
      }

      "there is more than 10 accounts and the 10th has no enrolments" in new TestHelper {
        mockIVCall(multiCL200IVCreds)
        def createMocksForEACD(ivCreds: List[IVNinoStoreEntry], count: Int = 10): Unit =
          if (count == 1) {
            mockEACDGetEnrolments(ivCreds.head.credId, None)
          } else {
            mockEACDGetEnrolments(ivCreds.head.credId, Some(businessEnrolmentResponse))
            createMocksForEACD(ivCreds.tail, count - 1)
          }
        createMocksForEACD(multiCL200IVCreds)
        mockSaveCacheOtherValidPTAAccounts(true)

        val res = service.hasOtherAccountsWithPTAAccess

        whenReady(res.value) { result =>
          result shouldBe Right(true)
        }
      }

      "there is more than 10 accounts and the 10th has no business enrolments" in new TestHelper {
        mockIVCall(multiCL200IVCreds)
        def createMocksForEACD(ivCreds: List[IVNinoStoreEntry], count: Int = 10): Unit =
          if (count == 1) {
            mockEACDGetEnrolments(ivCreds.head.credId, Some(irsaResponse))
          } else {
            mockEACDGetEnrolments(ivCreds.head.credId, Some(businessEnrolmentResponse))
            createMocksForEACD(ivCreds.tail, count - 1)
          }
        createMocksForEACD(multiCL200IVCreds)
        mockSaveCacheOtherValidPTAAccounts(true)

        val res = service.hasOtherAccountsWithPTAAccess

        whenReady(res.value) { result =>
          result shouldBe Right(true)
        }
      }
    }
  }

  class TestHelper {

    def mockIVCall(resp: List[IVNinoStoreEntry]) =
      (mockIVConnector
        .getCredentialsWithNino(_: String)(
          _: ExecutionContext,
          _: HeaderCarrier
        ))
        .expects(NINO, *, *)
        .returning(createInboundResult(resp))
        .once()

    def mockEACDGetEnrolments(credId: String, resp: Option[UserEnrolmentsListResponse]) =
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
      hasOtherValidPtaAccounts: Boolean
    ) =
      (mockTeaSessionCache
        .save(_: String, _: Boolean)(
          _: RequestWithUserDetailsFromSession[AnyContent],
          _: Format[Boolean]
        ))
        .expects(HAS_OTHER_VALID_PTA_ACCOUNTS, hasOtherValidPtaAccounts, *, *)
        .returning(Future(CacheMap(request.sessionID, Map())))
        .once()
  }
}
