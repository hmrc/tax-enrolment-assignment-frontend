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
import play.api.inject.{Binding, bind}
import play.api.libs.json.{JsObject, JsString, Json}
import play.api.mvc.{AnyContent, BodyParsers}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.actions.DataRequest
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.BaseSpec
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.TestData._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.UserAnswers
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.JourneyCacheRepository
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.SessionKeys.{ACCOUNT_TYPE, REDIRECT_URL}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.services.{EACDService, SilentAssignmentService}

import scala.concurrent.{ExecutionContext, Future}

class AccountCheckOrchestratorSpec extends BaseSpec {

  lazy val mockSilentAssignmentService: SilentAssignmentService = mock[SilentAssignmentService]
  lazy val mockEacdService: EACDService = mock[EACDService]

  lazy val testBodyParser: BodyParsers.Default = mock[BodyParsers.Default]
  lazy val mockMultipleAccountsOrchestrator: MultipleAccountsOrchestrator = mock[MultipleAccountsOrchestrator]
  lazy val mockRepository: JourneyCacheRepository = mock[JourneyCacheRepository]

  override lazy val overrides: Seq[Binding[JourneyCacheRepository]] = Seq(
    bind[JourneyCacheRepository].toInstance(mockRepository)
  )

  override implicit lazy val app: Application = localGuiceApplicationBuilder()
    .overrides(
      bind[SilentAssignmentService].toInstance(mockSilentAssignmentService),
      bind[EACDService].toInstance(mockEacdService)
    )
    .build()

  val orchestrator: AccountCheckOrchestrator = app.injector.instanceOf[AccountCheckOrchestrator]

  s"getAccountType" when {
    "the accountType is available in the cache" should {
      "return the accountType" in {

        val data = Map(ACCOUNT_TYPE -> Json.toJson(SINGLE_OR_MULTIPLE_ACCOUNTS), REDIRECT_URL -> JsString("foo"))
        val userAnswers: UserAnswers = UserAnswers(
          request.sessionID,
          generateNino.nino,
          Json.toJson(data).as[JsObject]
        )

        (mockRepository
          .get(_: String, _: String))
          .expects(*, *)
          .returning(Future.successful(Some(userAnswers)))

        val res = orchestrator.getAccountType(Some("foo"))(implicitly, implicitly, request = requestWithUserDetails())
        whenReady(res.value) { result =>
          result shouldBe Right(SINGLE_OR_MULTIPLE_ACCOUNTS)
        }
      }
    }

    "a user has one credential associated with their nino" that {
      "has no PT enrolment in session or EACD" should {
        s"return SINGLE_ACCOUNT" in {

          (mockRepository
            .get(_: String, _: String))
            .expects(*, *)
            .returning(Future.successful(None))

          (mockEacdService
            .getUsersAssignedPTEnrolment(
              _: DataRequest[AnyContent],
              _: HeaderCarrier,
              _: ExecutionContext
            ))
            .expects(*, *, *)
            .returning(createInboundResult(UsersAssignedEnrolmentEmpty))

          (mockEacdService
            .getUsersAssignedSAEnrolment(
              _: DataRequest[AnyContent],
              _: HeaderCarrier,
              _: ExecutionContext
            ))
            .expects(*, *, *)
            .returning(createInboundResult(UsersAssignedEnrolmentEmpty))

          val data = Map(ACCOUNT_TYPE -> Json.toJson(SINGLE_OR_MULTIPLE_ACCOUNTS))

          val userAnswers: UserAnswers = UserAnswers(
            request.sessionID,
            generateNino.nino,
            Json.toJson(data).as[JsObject]
          )

          (mockRepository
            .set(_: UserAnswers))
            .expects(userAnswers)
            .returning(Future.successful(true))

          val res = orchestrator.getAccountType(None)(implicitly, implicitly, request = requestWithUserDetails())

          whenReady(res.value) { result =>
            result shouldBe Right(SINGLE_OR_MULTIPLE_ACCOUNTS)
          }
        }
      }

      "has a PT enrolment in the session" should {
        s"return PT_ASSIGNED_TO_CURRENT_USER" in {

          (mockRepository
            .get(_: String, _: String))
            .expects(*, *)
            .returning(Future.successful(None))

          val data = Map(ACCOUNT_TYPE -> Json.toJson(PT_ASSIGNED_TO_CURRENT_USER))

          val userAnswers: UserAnswers = UserAnswers(
            request.sessionID,
            generateNino.nino,
            Json.toJson(data).as[JsObject]
          )

          (mockRepository
            .set(_: UserAnswers))
            .expects(userAnswers)
            .returning(Future.successful(true))

          (mockEacdService
            .getUsersAssignedSAEnrolment(
              _: DataRequest[AnyContent],
              _: HeaderCarrier,
              _: ExecutionContext
            ))
            .expects(*, *, *)
            .returning(createInboundResult(UsersAssignedEnrolment1))

          (mockEacdService
            .getUsersAssignedPTEnrolment(
              _: DataRequest[AnyContent],
              _: HeaderCarrier,
              _: ExecutionContext
            ))
            .expects(*, *, *)
            .returning(createInboundResult(UsersAssignedEnrolmentCurrentCred))

          val res = orchestrator.getAccountType(None)(
            ec,
            hc,
            requestWithGivenSessionData(requestWithEnrolments(hmrcPt = true, irSa = false))
          )

          whenReady(res.value) { result =>
            result shouldBe Right(PT_ASSIGNED_TO_CURRENT_USER)
          }
        }
      }

      "has PT enrolment in EACD but not the session" should {
        s"return PT_ASSIGNED_TO_CURRENT_USER" in {

          (mockRepository
            .get(_: String, _: String))
            .expects(*, *)
            .returning(Future.successful(None))

          (mockEacdService
            .getUsersAssignedPTEnrolment(
              _: DataRequest[AnyContent],
              _: HeaderCarrier,
              _: ExecutionContext
            ))
            .expects(*, *, *)
            .returning(createInboundResult(UsersAssignedEnrolmentCurrentCred))

          (mockEacdService
            .getUsersAssignedSAEnrolment(
              _: DataRequest[AnyContent],
              _: HeaderCarrier,
              _: ExecutionContext
            ))
            .expects(*, *, *)
            .returning(createInboundResult(UsersAssignedEnrolment1))

          val data = Map(ACCOUNT_TYPE -> Json.toJson(PT_ASSIGNED_TO_CURRENT_USER))

          val userAnswers: UserAnswers = UserAnswers(
            request.sessionID,
            generateNino.nino,
            Json.toJson(data).as[JsObject]
          )

          (mockRepository
            .set(_: UserAnswers))
            .expects(userAnswers)
            .returning(Future.successful(true))

          val res = orchestrator.getAccountType(None)(
            implicitly,
            implicitly,
            requestWithGivenSessionData(requestWithEnrolments(hmrcPt = true, irSa = false))
          )

          whenReady(res.value) { result =>
            result shouldBe Right(PT_ASSIGNED_TO_CURRENT_USER)
          }
        }
      }
    }

    "a user has other none business credentials associated with their NINO" that {
      "includes one with a PT enrolment" should {
        "return PT_ASSIGNED_TO_OTHER_USER" in {
          (mockRepository
            .get(_: String, _: String))
            .expects(*, *)
            .returning(Future.successful(None))

          (mockEacdService
            .getUsersAssignedPTEnrolment(
              _: DataRequest[AnyContent],
              _: HeaderCarrier,
              _: ExecutionContext
            ))
            .expects(*, *, *)
            .returning(createInboundResult(UsersAssignedEnrolment1))

          (mockEacdService
            .getUsersAssignedSAEnrolment(
              _: DataRequest[AnyContent],
              _: HeaderCarrier,
              _: ExecutionContext
            ))
            .expects(*, *, *)
            .returning(createInboundResult(UsersAssignedEnrolment1))

          val data = Map(ACCOUNT_TYPE -> Json.toJson(PT_ASSIGNED_TO_OTHER_USER))

          val userAnswers: UserAnswers = UserAnswers(
            request.sessionID,
            generateNino.nino,
            Json.toJson(data).as[JsObject]
          )

          (mockRepository
            .set(_: UserAnswers))
            .expects(userAnswers)
            .returning(Future.successful(true))

          val res = orchestrator.getAccountType(None)(implicitly, implicitly, request = requestWithUserDetails())

          whenReady(res.value) { result =>
            result shouldBe Right(PT_ASSIGNED_TO_OTHER_USER)
          }
        }
      }

      "includes a credential (not signed in) with SA enrolment" should {
        "return SA_ASSIGNED_TO_OTHER_USER" in {
          (mockRepository
            .get(_: String, _: String))
            .expects(*, *)
            .returning(Future.successful(None))

          (mockEacdService
            .getUsersAssignedPTEnrolment(
              _: DataRequest[AnyContent],
              _: HeaderCarrier,
              _: ExecutionContext
            ))
            .expects(*, *, *)
            .returning(createInboundResult(UsersAssignedEnrolmentEmpty))

          (mockEacdService
            .getUsersAssignedSAEnrolment(
              _: DataRequest[AnyContent],
              _: HeaderCarrier,
              _: ExecutionContext
            ))
            .expects(*, *, *)
            .returning(createInboundResult(UsersAssignedEnrolment1))

          val data = Map(ACCOUNT_TYPE -> Json.toJson(SA_ASSIGNED_TO_OTHER_USER))

          val userAnswers: UserAnswers = UserAnswers(
            request.sessionID,
            generateNino.nino,
            Json.toJson(data).as[JsObject]
          )

          (mockRepository
            .set(_: UserAnswers))
            .expects(userAnswers)
            .returning(Future.successful(true))

          val res = orchestrator.getAccountType(None)(implicitly, implicitly, request = requestWithUserDetails())

          whenReady(res.value) { result =>
            result shouldBe Right(SA_ASSIGNED_TO_OTHER_USER)
          }
        }
      }

      "have no enrolments but the signed in credential has SA in request" should {
        "return SA_ASSIGNED_TO_CURRENT_USER" in {

          (mockRepository
            .get(_: String, _: String))
            .expects(*, *)
            .returning(Future.successful(None))

          (mockEacdService
            .getUsersAssignedPTEnrolment(
              _: DataRequest[AnyContent],
              _: HeaderCarrier,
              _: ExecutionContext
            ))
            .expects(*, *, *)
            .returning(createInboundResult(UsersAssignedEnrolmentEmpty))

          (mockEacdService
            .getUsersAssignedSAEnrolment(
              _: DataRequest[AnyContent],
              _: HeaderCarrier,
              _: ExecutionContext
            ))
            .expects(*, *, *)
            .returning(createInboundResult(UsersAssignedEnrolmentEmpty))

          val data = Map(ACCOUNT_TYPE -> Json.toJson(SA_ASSIGNED_TO_CURRENT_USER))

          val userAnswers: UserAnswers = UserAnswers(
            request.sessionID,
            generateNino.nino,
            Json.toJson(data).as[JsObject]
          )

          (mockRepository
            .set(_: UserAnswers))
            .expects(userAnswers)
            .returning(Future.successful(true))

          val res = orchestrator.getAccountType(None)(
            ec,
            hc,
            requestWithGivenSessionData(requestWithEnrolments(hmrcPt = false, irSa = true))
          )

          whenReady(res.value) { result =>
            result shouldBe Right(SA_ASSIGNED_TO_CURRENT_USER)
          }
        }
      }

      "have no enrolments but the signed in credential has SA in EACD" should {
        "return SA_ASSIGNED_TO_CURRENT_USER" in {
          (mockRepository
            .get(_: String, _: String))
            .expects(*, *)
            .returning(Future.successful(None))

          (mockEacdService
            .getUsersAssignedPTEnrolment(
              _: DataRequest[AnyContent],
              _: HeaderCarrier,
              _: ExecutionContext
            ))
            .expects(*, *, *)
            .returning(createInboundResult(UsersAssignedEnrolmentEmpty))

          (mockEacdService
            .getUsersAssignedSAEnrolment(
              _: DataRequest[AnyContent],
              _: HeaderCarrier,
              _: ExecutionContext
            ))
            .expects(*, *, *)
            .returning(createInboundResult(UsersAssignedEnrolmentCurrentCred))

          val data = Map(ACCOUNT_TYPE -> Json.toJson(SA_ASSIGNED_TO_CURRENT_USER))

          val userAnswers: UserAnswers = UserAnswers(
            request.sessionID,
            generateNino.nino,
            Json.toJson(data).as[JsObject]
          )

          (mockRepository
            .set(_: UserAnswers))
            .expects(userAnswers)
            .returning(Future.successful(true))

          val res = orchestrator.getAccountType(None)(
            ec,
            hc,
            requestWithGivenSessionData(requestWithEnrolments(hmrcPt = false, irSa = true))
          )

          whenReady(res.value) { result =>
            result shouldBe Right(SA_ASSIGNED_TO_CURRENT_USER)
          }
        }
      }

      "have no enrolments" should {
        s"return MULTIPLE_ACCOUNTS" in {
          (mockRepository
            .get(_: String, _: String))
            .expects(*, *)
            .returning(Future.successful(None))

          (mockEacdService
            .getUsersAssignedPTEnrolment(
              _: DataRequest[AnyContent],
              _: HeaderCarrier,
              _: ExecutionContext
            ))
            .expects(*, *, *)
            .returning(createInboundResult(UsersAssignedEnrolmentEmpty))

          (mockEacdService
            .getUsersAssignedSAEnrolment(
              _: DataRequest[AnyContent],
              _: HeaderCarrier,
              _: ExecutionContext
            ))
            .expects(*, *, *)
            .returning(createInboundResult(UsersAssignedEnrolmentEmpty))

          val data = Map(ACCOUNT_TYPE -> Json.toJson(SINGLE_OR_MULTIPLE_ACCOUNTS))

          val userAnswers: UserAnswers = UserAnswers(
            request.sessionID,
            generateNino.nino,
            Json.toJson(data).as[JsObject]
          )

          (mockRepository
            .set(_: UserAnswers))
            .expects(userAnswers)
            .returning(Future.successful(true))

          val res = orchestrator.getAccountType(None)(implicitly, implicitly, request = requestWithUserDetails())

          whenReady(res.value) { result =>
            result shouldBe Right(SINGLE_OR_MULTIPLE_ACCOUNTS)
          }
        }
      }

      "includes one with a SA enrolment" should {
        "return SA_ASSIGNED_TO_OTHER_USER" in {
          (mockRepository
            .get(_: String, _: String))
            .expects(*, *)
            .returning(Future.successful(None))

          (mockEacdService
            .getUsersAssignedPTEnrolment(
              _: DataRequest[AnyContent],
              _: HeaderCarrier,
              _: ExecutionContext
            ))
            .expects(*, *, *)
            .returning(createInboundResult(UsersAssignedEnrolmentEmpty))

          (mockEacdService
            .getUsersAssignedSAEnrolment(
              _: DataRequest[AnyContent],
              _: HeaderCarrier,
              _: ExecutionContext
            ))
            .expects(*, *, *)
            .returning(createInboundResult(UsersAssignedEnrolment1))

          val data = Map(ACCOUNT_TYPE -> Json.toJson(SA_ASSIGNED_TO_OTHER_USER))

          val userAnswers: UserAnswers = UserAnswers(
            request.sessionID,
            generateNino.nino,
            Json.toJson(data).as[JsObject]
          )

          (mockRepository
            .set(_: UserAnswers))
            .expects(userAnswers)
            .returning(Future.successful(true))

          val res = orchestrator.getAccountType(None)(implicitly, implicitly, request = requestWithUserDetails())

          whenReady(res.value) { result =>
            result shouldBe Right(SA_ASSIGNED_TO_OTHER_USER)
          }
        }
      }
    }
  }
}
