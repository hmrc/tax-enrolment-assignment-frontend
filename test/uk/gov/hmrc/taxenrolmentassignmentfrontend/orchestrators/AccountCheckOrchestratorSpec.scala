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

package uk.gov.hmrc.taxenrolmentassignmentfrontend.orchestrators

import play.api.Application
import play.api.inject.bind
import play.api.libs.json.Format
import play.api.mvc.{AnyContent, BodyParsers}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.actions.RequestWithUserDetailsFromSession
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.BaseSpec
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.TestData._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.SessionKeys.ACCOUNT_TYPE
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.TEASessionCache
import uk.gov.hmrc.taxenrolmentassignmentfrontend.services.{EACDService, SilentAssignmentService}

import scala.concurrent.{ExecutionContext, Future}

class AccountCheckOrchestratorSpec extends BaseSpec {

  def generateBasicCacheMap(accountType: AccountTypes.Value, redirectUrl: String = "foo") =
    CacheMap("id", generateBasicCacheData(accountType, redirectUrl))

  lazy val mockSilentAssignmentService = mock[SilentAssignmentService]
  lazy val mockEacdService = mock[EACDService]
  lazy val mockTeaSessionCache = mock[TEASessionCache]

  lazy val testBodyParser: BodyParsers.Default = mock[BodyParsers.Default]
  lazy val mockMultipleAccountsOrchestrator = mock[MultipleAccountsOrchestrator]

  override lazy val overrides = Seq(
    bind[TEASessionCache].toInstance(mockTeaSessionCache)
  )

  override implicit lazy val app: Application = localGuiceApplicationBuilder()
    .overrides(
      bind[SilentAssignmentService].toInstance(mockSilentAssignmentService),
      bind[EACDService].toInstance(mockEacdService)
    )
    .build()

  val orchestrator = app.injector.instanceOf[AccountCheckOrchestrator]

  s"getAccountType" when {
    "the accountType is available in the cache" should {
      "return the accountType" in {
        (mockTeaSessionCache
          .fetch()(
            _: RequestWithUserDetailsFromSession[AnyContent]
          ))
          .expects(*)
          .returning(Future.successful(Some(generateBasicCacheMap(SINGLE_ACCOUNT))))

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
            .fetch()(
              _: RequestWithUserDetailsFromSession[AnyContent]
            ))
            .expects(*)
            .returning(Future.successful(None))

          (mockEacdService
            .getUsersAssignedPTEnrolment(
              _: RequestWithUserDetailsFromSession[AnyContent],
              _: HeaderCarrier,
              _: ExecutionContext
            ))
            .expects(*, *, *)
            .returning(createInboundResult(UsersAssignedEnrolmentEmpty))

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

      "has a PT enrolment in the session" should {
        s"return PT_ASSIGNED_TO_CURRENT_USER" in {
          (mockTeaSessionCache
            .fetch()(
              _: RequestWithUserDetailsFromSession[AnyContent]
            ))
            .expects(*)
            .returning(Future.successful(None))

          (mockTeaSessionCache
            .save(_: String, _: AccountTypes.Value)(
              _: RequestWithUserDetailsFromSession[AnyContent],
              _: Format[AccountTypes.Value]
            ))
            .expects(ACCOUNT_TYPE, PT_ASSIGNED_TO_CURRENT_USER, *, *)
            .returning(Future(CacheMap(request.sessionID, Map())))

          (mockEacdService
            .getUsersAssignedSAEnrolment(
              _: RequestWithUserDetailsFromSession[AnyContent],
              _: HeaderCarrier,
              _: ExecutionContext
            ))
            .expects(*, *, *)
            .returning(createInboundResult(UsersAssignedEnrolment1))

          (mockEacdService
            .getUsersAssignedPTEnrolment(
              _: RequestWithUserDetailsFromSession[AnyContent],
              _: HeaderCarrier,
              _: ExecutionContext
            ))
            .expects(*, *, *)
            .returning(createInboundResult(UsersAssignedEnrolmentCurrentCred))

          val res = orchestrator.getAccountType(
            ec,
            hc,
            requestWithEnrolments(true, false)
          )

          whenReady(res.value) { result =>
            result shouldBe Right(PT_ASSIGNED_TO_CURRENT_USER)
          }
        }
      }

      "has PT enrolment in EACD but not the session" should {
        s"return PT_ASSIGNED_TO_CURRENT_USER" in {
          (mockTeaSessionCache
            .fetch()(
              _: RequestWithUserDetailsFromSession[AnyContent]
            ))
            .expects(*)
            .returning(Future.successful(None))

          (mockEacdService
            .getUsersAssignedPTEnrolment(
              _: RequestWithUserDetailsFromSession[AnyContent],
              _: HeaderCarrier,
              _: ExecutionContext
            ))
            .expects(*, *, *)
            .returning(createInboundResult(UsersAssignedEnrolmentCurrentCred))

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
            .expects(ACCOUNT_TYPE, PT_ASSIGNED_TO_CURRENT_USER, *, *)
            .returning(Future(CacheMap(request.sessionID, Map())))
          val res = orchestrator.getAccountType(implicitly, implicitly, requestWithEnrolments(true, false))

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
            .fetch()(
              _: RequestWithUserDetailsFromSession[AnyContent]
            ))
            .expects(*)
            .returning(Future.successful(None))

          (mockEacdService
            .getUsersAssignedPTEnrolment(
              _: RequestWithUserDetailsFromSession[AnyContent],
              _: HeaderCarrier,
              _: ExecutionContext
            ))
            .expects(*, *, *)
            .returning(createInboundResult(UsersAssignedEnrolment1))

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
            .fetch()(
              _: RequestWithUserDetailsFromSession[AnyContent]
            ))
            .expects(*)
            .returning(Future.successful(None))

          (mockEacdService
            .getUsersAssignedPTEnrolment(
              _: RequestWithUserDetailsFromSession[AnyContent],
              _: HeaderCarrier,
              _: ExecutionContext
            ))
            .expects(*, *, *)
            .returning(createInboundResult(UsersAssignedEnrolmentEmpty))

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
            .fetch()(
              _: RequestWithUserDetailsFromSession[AnyContent]
            ))
            .expects(*)
            .returning(Future.successful(None))

          (mockEacdService
            .getUsersAssignedPTEnrolment(
              _: RequestWithUserDetailsFromSession[AnyContent],
              _: HeaderCarrier,
              _: ExecutionContext
            ))
            .expects(*, *, *)
            .returning(createInboundResult(UsersAssignedEnrolmentEmpty))

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
            .expects(ACCOUNT_TYPE, SA_ASSIGNED_TO_CURRENT_USER, *, *)
            .returning(Future(CacheMap(request.sessionID, Map())))

          val res = orchestrator.getAccountType(
            ec,
            hc,
            requestWithEnrolments(false, true)
          )

          whenReady(res.value) { result =>
            result shouldBe Right(SA_ASSIGNED_TO_CURRENT_USER)
          }
        }
      }

      "have no enrolments but the signed in credential has SA in EACD" should {
        "return SA_ASSIGNED_TO_CURRENT_USER" in {
          (mockTeaSessionCache
            .fetch()(
              _: RequestWithUserDetailsFromSession[AnyContent]
            ))
            .expects(*)
            .returning(Future.successful(None))

          (mockEacdService
            .getUsersAssignedPTEnrolment(
              _: RequestWithUserDetailsFromSession[AnyContent],
              _: HeaderCarrier,
              _: ExecutionContext
            ))
            .expects(*, *, *)
            .returning(createInboundResult(UsersAssignedEnrolmentEmpty))

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

          val res = orchestrator.getAccountType(implicitly, implicitly, requestWithEnrolments(false, true))

          whenReady(res.value) { result =>
            result shouldBe Right(SA_ASSIGNED_TO_CURRENT_USER)
          }
        }
      }

      "have no enrolments" should {
        s"return MULTIPLE_ACCOUNTS" in {
          (mockTeaSessionCache
            .fetch()(
              _: RequestWithUserDetailsFromSession[AnyContent]
            ))
            .expects(*)
            .returning(Future.successful(None))

          (mockEacdService
            .getUsersAssignedPTEnrolment(
              _: RequestWithUserDetailsFromSession[AnyContent],
              _: HeaderCarrier,
              _: ExecutionContext
            ))
            .expects(*, *, *)
            .returning(createInboundResult(UsersAssignedEnrolmentEmpty))

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
            .fetch()(
              _: RequestWithUserDetailsFromSession[AnyContent]
            ))
            .expects(*)
            .returning(Future.successful(None))

          (mockEacdService
            .getUsersAssignedPTEnrolment(
              _: RequestWithUserDetailsFromSession[AnyContent],
              _: HeaderCarrier,
              _: ExecutionContext
            ))
            .expects(*, *, *)
            .returning(createInboundResult(UsersAssignedEnrolmentEmpty))

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
