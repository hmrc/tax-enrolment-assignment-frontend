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

package uk.gov.hmrc.taxenrolmentassignmentfrontend.orchestrators

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import play.api.libs.json.Format
import play.api.mvc.AnyContent
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes.{MULTIPLE_ACCOUNTS, PT_ASSIGNED_TO_CURRENT_USER, PT_ASSIGNED_TO_OTHER_USER, SA_ASSIGNED_TO_CURRENT_USER, SA_ASSIGNED_TO_OTHER_USER, SINGLE_ACCOUNT}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.actions.RequestWithUserDetailsFromSession
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.TestData._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.TestFixture
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.IVNinoStoreEntry
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.SessionKeys.ACCOUNT_TYPE

import scala.concurrent.{ExecutionContext, Future}

class AccountCheckOrchestratorSpec extends TestFixture with ScalaFutures {

  implicit val defaultPatience = PatienceConfig(
    timeout = Span(TIME_OUT, Seconds),
    interval = Span(INTERVAL, Millis)
  )

  val orchestrator = new AccountCheckOrchestrator(
    mockEacdService,
    mockSilentAssignmentService,
    logger,
    mockTeaSessionCache
  )

  s"getAccountType" when {
    "the accountType is available in the cache" should {
      "return the accountType" in {
        (mockTeaSessionCache
          .getEntry(_: String)(
            _: RequestWithUserDetailsFromSession[AnyContent],
            _: Format[AccountTypes.Value]
          ))
          .expects("ACCOUNT_TYPE", *, *)
          .returning(Future.successful(Some(SINGLE_ACCOUNT)))

        val res = orchestrator.getAccountType
        whenReady(res.value) { result =>
          result shouldBe Right(SINGLE_ACCOUNT)
        }
      }
    }

    "a user has one credential associated with their nino" that {
      "has no PT enrolment in session or EACD" should {
        s"return SINGLE_ACCOUNT" in {
          (mockTeaSessionCache
            .getEntry(_: String)(
              _: RequestWithUserDetailsFromSession[AnyContent],
              _: Format[AccountTypes.Value]
            ))
            .expects("ACCOUNT_TYPE", *, *)
            .returning(Future.successful(None))

          (mockEacdService
            .getUsersAssignedPTEnrolment(
              _: RequestWithUserDetailsFromSession[AnyContent],
              _: HeaderCarrier,
              _: ExecutionContext
            ))
            .expects(*, *, *)
            .returning(createInboundResult(UsersAssignedEnrolmentEmpty))

          (mockSilentAssignmentService
            .getOtherAccountsWithPTAAccess(
              _: RequestWithUserDetailsFromSession[AnyContent],
              _: HeaderCarrier,
              _: ExecutionContext
            ))
            .expects(*, *, *)
            .returning(createInboundResult(Seq.empty[IVNinoStoreEntry]))

          (mockTeaSessionCache
            .save(_: String, _: AccountTypes.Value)(
              _: RequestWithUserDetailsFromSession[AnyContent],
              _: Format[AccountTypes.Value]
            ))
            .expects(ACCOUNT_TYPE, SINGLE_ACCOUNT, *, *)
            .returning(Future(CacheMap(request.sessionID, Map())))

          val res = orchestrator.getAccountType

          whenReady(res.value) { result =>
            result shouldBe Right(SINGLE_ACCOUNT)
          }
        }
      }

      "has a PT enrolment in the session" should {
        s"return PT_ASSIGNED_TO_CURRENT_USER" in {
          (mockTeaSessionCache
            .getEntry(_: String)(
              _: RequestWithUserDetailsFromSession[AnyContent],
              _: Format[AccountTypes.Value]
            ))
            .expects("ACCOUNT_TYPE", *, *)
            .returning(Future.successful(None))

          val res = orchestrator.getAccountType(
            ec,
            hc,
            request.copy(userDetails = userDetailsWithPTEnrolment)
          )

          (mockTeaSessionCache
            .save(_: String, _: AccountTypes.Value)(
              _: RequestWithUserDetailsFromSession[AnyContent],
              _: Format[AccountTypes.Value]
            ))
            .expects(ACCOUNT_TYPE, PT_ASSIGNED_TO_CURRENT_USER, *, *)
            .returning(Future(CacheMap(request.sessionID, Map())))

          whenReady(res.value) { result =>
            result shouldBe Right(PT_ASSIGNED_TO_CURRENT_USER)
          }
        }
      }

      "has PT enrolment in EACD but not the session" should {
        s"return PT_ASSIGNED_TO_CURRENT_USER" in {
          (mockTeaSessionCache
            .getEntry(_: String)(
              _: RequestWithUserDetailsFromSession[AnyContent],
              _: Format[AccountTypes.Value]
            ))
            .expects("ACCOUNT_TYPE", *, *)
            .returning(Future.successful(None))

          (mockEacdService
            .getUsersAssignedPTEnrolment(
              _: RequestWithUserDetailsFromSession[AnyContent],
              _: HeaderCarrier,
              _: ExecutionContext
            ))
            .expects(*, *, *)
            .returning(createInboundResult(UsersAssignedEnrolmentCurrentCred))

          (mockTeaSessionCache
            .save(_: String, _: AccountTypes.Value)(
              _: RequestWithUserDetailsFromSession[AnyContent],
              _: Format[AccountTypes.Value]
            ))
            .expects(ACCOUNT_TYPE, PT_ASSIGNED_TO_CURRENT_USER, *, *)
            .returning(Future(CacheMap(request.sessionID, Map())))
          val res = orchestrator.getAccountType

          whenReady(res.value) { result =>
            result shouldBe Right(PT_ASSIGNED_TO_CURRENT_USER)
          }
        }
      }
    }

    "a user has other none business credentials associated with their NINO" that {
      "includes one with a PT enrolment" should {
        "return PT_ASSIGNED_TO_OTHER_USER" in {
          (mockTeaSessionCache
            .getEntry(_: String)(
              _: RequestWithUserDetailsFromSession[AnyContent],
              _: Format[AccountTypes.Value]
            ))
            .expects("ACCOUNT_TYPE", *, *)
            .returning(Future.successful(None))

          (mockEacdService
            .getUsersAssignedPTEnrolment(
              _: RequestWithUserDetailsFromSession[AnyContent],
              _: HeaderCarrier,
              _: ExecutionContext
            ))
            .expects(*, *, *)
            .returning(createInboundResult(UsersAssignedEnrolment1))

          (mockTeaSessionCache
            .save(_: String, _: AccountTypes.Value)(
              _: RequestWithUserDetailsFromSession[AnyContent],
              _: Format[AccountTypes.Value]
            ))
            .expects(ACCOUNT_TYPE, PT_ASSIGNED_TO_OTHER_USER, *, *)
            .returning(Future(CacheMap(request.sessionID, Map())))

          val res = orchestrator.getAccountType

          whenReady(res.value) { result =>
            result shouldBe Right(PT_ASSIGNED_TO_OTHER_USER)
          }
        }
      }

      "includes a credential (not signed in) with SA enrolment" should {
        "return SA_ASSIGNED_TO_OTHER_USER" in {
          (mockTeaSessionCache
            .getEntry(_: String)(
              _: RequestWithUserDetailsFromSession[AnyContent],
              _: Format[AccountTypes.Value]
            ))
            .expects("ACCOUNT_TYPE", *, *)
            .returning(Future.successful(None))

          (mockEacdService
            .getUsersAssignedPTEnrolment(
              _: RequestWithUserDetailsFromSession[AnyContent],
              _: HeaderCarrier,
              _: ExecutionContext
            ))
            .expects(*, *, *)
            .returning(createInboundResult(UsersAssignedEnrolmentEmpty))

          (mockSilentAssignmentService
            .getOtherAccountsWithPTAAccess(
              _: RequestWithUserDetailsFromSession[AnyContent],
              _: HeaderCarrier,
              _: ExecutionContext
            ))
            .expects(*, *, *)
            .returning(createInboundResult(multiIVCreds))

          (mockEacdService
            .getUsersAssignedSAEnrolment(
              _: RequestWithUserDetailsFromSession[AnyContent],
              _: HeaderCarrier,
              _: ExecutionContext
            ))
            .expects(*, *, *)
            .returning(createInboundResult(UsersAssignedEnrolment1))

          (mockTeaSessionCache
            .save(_: String, _: AccountTypes.Value)(
              _: RequestWithUserDetailsFromSession[AnyContent],
              _: Format[AccountTypes.Value]
            ))
            .expects(ACCOUNT_TYPE, SA_ASSIGNED_TO_OTHER_USER, *, *)
            .returning(Future(CacheMap(request.sessionID, Map())))

          val res = orchestrator.getAccountType

          whenReady(res.value) { result =>
            result shouldBe Right(SA_ASSIGNED_TO_OTHER_USER)
          }
        }
      }

      "have no enrolments but the signed in credential has SA in request" should {
        "return SA_ASSIGNED_TO_CURRENT_USER" in {
          (mockTeaSessionCache
            .getEntry(_: String)(
              _: RequestWithUserDetailsFromSession[AnyContent],
              _: Format[AccountTypes.Value]
            ))
            .expects("ACCOUNT_TYPE", *, *)
            .returning(Future.successful(None))

          (mockEacdService
            .getUsersAssignedPTEnrolment(
              _: RequestWithUserDetailsFromSession[AnyContent],
              _: HeaderCarrier,
              _: ExecutionContext
            ))
            .expects(*, *, *)
            .returning(createInboundResult(UsersAssignedEnrolmentEmpty))

          (mockSilentAssignmentService
            .getOtherAccountsWithPTAAccess(
              _: RequestWithUserDetailsFromSession[AnyContent],
              _: HeaderCarrier,
              _: ExecutionContext
            ))
            .expects(*, *, *)
            .returning(createInboundResult(multiIVCreds))

          (mockTeaSessionCache
            .save(_: String, _: AccountTypes.Value)(
              _: RequestWithUserDetailsFromSession[AnyContent],
              _: Format[AccountTypes.Value]
            ))
            .expects(ACCOUNT_TYPE, SA_ASSIGNED_TO_CURRENT_USER, *, *)
            .returning(Future(CacheMap(request.sessionID, Map())))

          val res = orchestrator.getAccountType(
            ec,
            hc,
            request.copy(userDetails = userDetailsWithSAEnrolment)
          )

          whenReady(res.value) { result =>
            result shouldBe Right(SA_ASSIGNED_TO_CURRENT_USER)
          }
        }
      }

      "have no enrolments but the signed in credential has SA in EACD" should {
        "return SA_ASSIGNED_TO_CURRENT_USER" in {
          (mockTeaSessionCache
            .getEntry(_: String)(
              _: RequestWithUserDetailsFromSession[AnyContent],
              _: Format[AccountTypes.Value]
            ))
            .expects("ACCOUNT_TYPE", *, *)
            .returning(Future.successful(None))

          (mockEacdService
            .getUsersAssignedPTEnrolment(
              _: RequestWithUserDetailsFromSession[AnyContent],
              _: HeaderCarrier,
              _: ExecutionContext
            ))
            .expects(*, *, *)
            .returning(createInboundResult(UsersAssignedEnrolmentEmpty))

          (mockSilentAssignmentService
            .getOtherAccountsWithPTAAccess(
              _: RequestWithUserDetailsFromSession[AnyContent],
              _: HeaderCarrier,
              _: ExecutionContext
            ))
            .expects(*, *, *)
            .returning(createInboundResult(multiIVCreds))

          (mockEacdService
            .getUsersAssignedSAEnrolment(
              _: RequestWithUserDetailsFromSession[AnyContent],
              _: HeaderCarrier,
              _: ExecutionContext
            ))
            .expects(*, *, *)
            .returning(createInboundResult(UsersAssignedEnrolmentCurrentCred))

          (mockTeaSessionCache
            .save(_: String, _: AccountTypes.Value)(
              _: RequestWithUserDetailsFromSession[AnyContent],
              _: Format[AccountTypes.Value]
            ))
            .expects(ACCOUNT_TYPE, SA_ASSIGNED_TO_CURRENT_USER, *, *)
            .returning(Future(CacheMap(request.sessionID, Map())))

          val res = orchestrator.getAccountType

          whenReady(res.value) { result =>
            result shouldBe Right(SA_ASSIGNED_TO_CURRENT_USER)
          }
        }
      }

      "have no enrolments" should {
        s"return MULTIPLE_ACCOUNTS" in {
          (mockTeaSessionCache
            .getEntry(_: String)(
              _: RequestWithUserDetailsFromSession[AnyContent],
              _: Format[AccountTypes.Value]
            ))
            .expects("ACCOUNT_TYPE", *, *)
            .returning(Future.successful(None))

          (mockEacdService
            .getUsersAssignedPTEnrolment(
              _: RequestWithUserDetailsFromSession[AnyContent],
              _: HeaderCarrier,
              _: ExecutionContext
            ))
            .expects(*, *, *)
            .returning(createInboundResult(UsersAssignedEnrolmentEmpty))

          (mockSilentAssignmentService
            .getOtherAccountsWithPTAAccess(
              _: RequestWithUserDetailsFromSession[AnyContent],
              _: HeaderCarrier,
              _: ExecutionContext
            ))
            .expects(*, *, *)
            .returning(createInboundResult(multiIVCreds))

          (mockEacdService
            .getUsersAssignedSAEnrolment(
              _: RequestWithUserDetailsFromSession[AnyContent],
              _: HeaderCarrier,
              _: ExecutionContext
            ))
            .expects(*, *, *)
            .returning(createInboundResult(UsersAssignedEnrolmentEmpty))

          (mockTeaSessionCache
            .save(_: String, _: AccountTypes.Value)(
              _: RequestWithUserDetailsFromSession[AnyContent],
              _: Format[AccountTypes.Value]
            ))
            .expects(ACCOUNT_TYPE, MULTIPLE_ACCOUNTS, *, *)
            .returning(Future(CacheMap(request.sessionID, Map())))

          val res = orchestrator.getAccountType

          whenReady(res.value) { result =>
            result shouldBe Right(MULTIPLE_ACCOUNTS)
          }
        }
      }

      "includes one with a SA enrolment" should {
        "return SA_ASSIGNED_TO_OTHER_USER" in {
          (mockTeaSessionCache
            .getEntry(_: String)(
              _: RequestWithUserDetailsFromSession[AnyContent],
              _: Format[AccountTypes.Value]
            ))
            .expects("ACCOUNT_TYPE", *, *)
            .returning(Future.successful(None))

          (mockEacdService
            .getUsersAssignedPTEnrolment(
              _: RequestWithUserDetailsFromSession[AnyContent],
              _: HeaderCarrier,
              _: ExecutionContext
            ))
            .expects(*, *, *)
            .returning(createInboundResult(UsersAssignedEnrolmentEmpty))

          (mockSilentAssignmentService
            .getOtherAccountsWithPTAAccess(
              _: RequestWithUserDetailsFromSession[AnyContent],
              _: HeaderCarrier,
              _: ExecutionContext
            ))
            .expects(*, *, *)
            .returning(createInboundResult(multiIVCreds))

          (mockEacdService
            .getUsersAssignedSAEnrolment(
              _: RequestWithUserDetailsFromSession[AnyContent],
              _: HeaderCarrier,
              _: ExecutionContext
            ))
            .expects(*, *, *)
            .returning(createInboundResult(UsersAssignedEnrolment1))

          (mockTeaSessionCache
            .save(_: String, _: AccountTypes.Value)(
              _: RequestWithUserDetailsFromSession[AnyContent],
              _: Format[AccountTypes.Value]
            ))
            .expects(ACCOUNT_TYPE, SA_ASSIGNED_TO_OTHER_USER, *, *)
            .returning(Future(CacheMap(request.sessionID, Map())))

          val res = orchestrator.getAccountType

          whenReady(res.value) { result =>
            result shouldBe Right(SA_ASSIGNED_TO_OTHER_USER)
          }
        }
      }
    }
  }
}
