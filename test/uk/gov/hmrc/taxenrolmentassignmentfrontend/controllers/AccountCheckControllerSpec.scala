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

package uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers

import cats.data.EitherT
import org.scalamock.handlers.{CallHandler1, CallHandler4, CallHandler5}
import org.scalatest.OneInstancePerTest
import play.api.{Application, Configuration}
import play.api.http.Status.SEE_OTHER
import play.api.i18n.{Lang, Messages, MessagesApi, MessagesImpl}
import play.api.inject.bind
import play.api.libs.json.{Format, JsString}
import play.api.mvc.BodyParsers
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve.{Credentials, Retrieval, ~}
import uk.gov.hmrc.auth.core.{AffinityGroup, AuthConnector, Enrolment, Enrolments}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.play.bootstrap.binders.RedirectUrl
import uk.gov.hmrc.service.TEAFResult
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.config.AppConfig
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.actions.{AuthAction, RequestWithUserDetailsFromSession, ThrottleAction}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.helpers.ErrorHandler
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.{TaxEnrolmentAssignmentErrors, UnexpectedError, UnexpectedResponseFromIV, UnexpectedResponseFromTaxEnrolments}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.TestData._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.{BaseSpec, TestFixture, UrlPaths}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.logging.EventLoggerService
import uk.gov.hmrc.taxenrolmentassignmentfrontend.orchestrators.AccountCheckOrchestrator
import uk.gov.hmrc.taxenrolmentassignmentfrontend.reporting.{AuditEvent, AuditHandler}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.{CascadeUpsert, SessionKeys, TEASessionCache}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.services.{SilentAssignmentService, ThrottleApplied, ThrottleDoesNotApply, ThrottleResult, ThrottlingService}

import scala.concurrent.{ExecutionContext, Future}

class AccountCheckControllerSpec extends BaseSpec with OneInstancePerTest {

  def mockAccountShouldNotBeThrottled(accountTypes: AccountTypes.Value, nino: String, enrolments: Set[Enrolment]): CallHandler5[AccountTypes.Value, String, Set[Enrolment], ExecutionContext, HeaderCarrier, TEAFResult[ThrottleResult]] = {
    (mockThrottlingService.throttle(_: AccountTypes.Value, _: String, _: Set[Enrolment])(_: ExecutionContext, _: HeaderCarrier))
      .expects(
        accountTypes,
        nino,
        enrolments, *, *
      )
      .returning(createInboundResult(ThrottleDoesNotApply))
      .once()
  }

  def mockAccountShouldBeThrottled(accountTypes: AccountTypes.Value, nino: String, enrolments: Set[Enrolment]): CallHandler5[AccountTypes.Value, String, Set[Enrolment], ExecutionContext, HeaderCarrier, TEAFResult[ThrottleResult]] = {
    (mockThrottlingService.throttle(_: AccountTypes.Value, _: String, _: Set[Enrolment])(_: ExecutionContext, _: HeaderCarrier))
      .expects(
        accountTypes,
        nino,
        enrolments, *, *
      )
      .returning(createInboundResult(ThrottleApplied))
      .once()
  }

  def mockDeleteDataFromCache: CallHandler1[RequestWithUserDetailsFromSession[_], Future[Boolean]] = {
    (mockTeaSessionCache.removeRecord(_: RequestWithUserDetailsFromSession[_]))
      .expects(*)
      .returning(Future.successful(true))
      .once()
  }

  def mockSaveDataToCache: CallHandler4[String, String, RequestWithUserDetailsFromSession[_], Format[String], Future[CacheMap]] = {
    (mockTeaSessionCache.save[String](_: String, _: String)(_: RequestWithUserDetailsFromSession[_], _: Format[String]))
      .expects(*, *, *, *)
      .returning(Future.successful(CacheMap("FAKE_SESSION_ID", Map.empty)))
      .once()
  }

  def mockErrorFromThrottlingService(
                                      accountTypes: AccountTypes.Value,
                                      nino: String,
                                      enrolments: Set[Enrolment]
                                    ): CallHandler5[AccountTypes.Value, String, Set[Enrolment], ExecutionContext, HeaderCarrier, TEAFResult[ThrottleResult]] = {
    (mockThrottlingService.throttle(_: AccountTypes.Value, _: String, _: Set[Enrolment])(_: ExecutionContext, _: HeaderCarrier))
      .expects(accountTypes, nino, enrolments, *, *)
      .returning(createInboundResultError(UnexpectedError))
      .once()
  }

  lazy val mockSilentAssignmentService = mock[SilentAssignmentService]
  lazy val mockAccountCheckOrchestrator = mock[AccountCheckOrchestrator]
  lazy val mockAuditHandler = mock[AuditHandler]
  lazy val mockThrottlingService = mock[ThrottlingService]

  lazy val mockAuthConnector = mock[AuthConnector]
  lazy val testBodyParser: BodyParsers.Default = mock[BodyParsers.Default]
  lazy val mockTeaSessionCache = mock[TEASessionCache]

  override lazy val overrides = Seq(
    bind[TEASessionCache].toInstance(mockTeaSessionCache)
  )

  override implicit lazy val app: Application = localGuiceApplicationBuilder()
    .overrides(
      bind[SilentAssignmentService].toInstance(mockSilentAssignmentService),
      bind[AccountCheckOrchestrator].toInstance(mockAccountCheckOrchestrator),
      bind[AuditHandler].toInstance(mockAuditHandler),
      bind[ThrottlingService].toInstance(mockThrottlingService),
      bind[AuthConnector].toInstance(mockAuthConnector),
      bind[BodyParsers.Default].toInstance(testBodyParser)
    )
    .build()

  lazy val controller = app.injector.instanceOf[AccountCheckController]

  lazy val returnUrl = RedirectUrl.apply(testOnly.routes.TestOnlyController.successfulCall.url)

  "accountCheck" when {

    "a single credential exists for a given nino with no PT enrolment" should {
      s"silently assign the HMRC-PT Enrolment and redirect to users redirect url" when {
        "the user has not been assigned the enrolment already" in new TestHelper {
          mockAuthCall()
          mockSaveDataToCache
          mockAccountCheckSuccess(SINGLE_ACCOUNT)
          mockSilentEnrolSuccess
          mockAuditPTEnrolled(SINGLE_ACCOUNT, requestWithUserDetails(), messagesApi)
          mockAccountShouldNotBeThrottled(SINGLE_ACCOUNT, NINO, noEnrolments.enrolments)

          // removeRecord is called twice.
          // - Once in enrolForPTIfRequired
          // - Once in handleNoneThrottledUsers
          (mockTeaSessionCache.removeRecord(_: RequestWithUserDetailsFromSession[_]))
            .expects(*)
            .returning(Future.successful(true))
            .twice()

          val result = controller
            .accountCheck(returnUrl)
            .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some(testOnly.routes.TestOnlyController.successfulCall.url)
        }
      }

      s"not silently assign the HMRC-PT Enrolment and redirect to users redirect url" when {
        "the user has been assigned the enrolment already" in new TestHelper {
          mockAuthCallWithPT()
          mockSaveDataToCache
          mockAccountCheckSuccess(SINGLE_ACCOUNT)
          mockAccountShouldNotBeThrottled(SINGLE_ACCOUNT, NINO, ptEnrolmentOnly.enrolments)

          // removeRecord is called twice.
          // - Once in enrolForPTIfRequired
          // - Once in handleNoneThrottledUsers
          (mockTeaSessionCache.removeRecord(_: RequestWithUserDetailsFromSession[_]))
            .expects(*)
            .returning(Future.successful(true))
            .twice()

          val result = controller
            .accountCheck(returnUrl)
            .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some(testOnly.routes.TestOnlyController.successfulCall.url)
        }
      }

      "return an error page if there was an error assigning the enrolment" in new TestHelper {
        mockAuthCall()
        mockSaveDataToCache
        mockAccountCheckSuccess(SINGLE_ACCOUNT)
        mockSilentEnrolFailure
        mockAccountShouldNotBeThrottled(SINGLE_ACCOUNT, NINO, noEnrolments.enrolments)

        val result = controller
          .accountCheck(returnUrl)
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(result) shouldBe INTERNAL_SERVER_ERROR
        contentAsString(result) should include(messages("enrolmentError.heading"))
      }

    }

    "a single credential exists for a given nino that is already enrolled for PT" should {
      s"redirect to ${UrlPaths.returnUrl}" in new TestHelper {
        mockAuthCall()
        mockSaveDataToCache
        mockAccountCheckSuccess(PT_ASSIGNED_TO_CURRENT_USER)
        mockAccountShouldNotBeThrottled(PT_ASSIGNED_TO_CURRENT_USER, NINO, noEnrolments.enrolments)
        mockDeleteDataFromCache

        val result = controller
          .accountCheck(returnUrl)
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(UrlPaths.returnUrl)
      }
    }

    "a PT enrolment exists on another users account" should {
      s"redirect to ${UrlPaths.ptOnOtherAccountPath}" in new TestHelper {
        mockAuthCall()
        mockSaveDataToCache
        mockAccountCheckSuccess(PT_ASSIGNED_TO_OTHER_USER)
        mockAccountShouldNotBeThrottled(PT_ASSIGNED_TO_OTHER_USER, NINO, noEnrolments.enrolments)

        val result = controller
          .accountCheck(returnUrl)
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(UrlPaths.ptOnOtherAccountPath)
      }
    }

    "multiple credential exists for a given nino and no enrolments exists" should {
      s"enrol for PT and redirect to ${UrlPaths.enrolledPTNoSAOnAnyAccountPath}" when {
        "the user has not already been assigned the PT enrolment" in new TestHelper {
          mockAuthCall()
          mockSaveDataToCache
          mockAccountCheckSuccess(MULTIPLE_ACCOUNTS)
          mockSilentEnrolSuccess
          mockAccountShouldNotBeThrottled(MULTIPLE_ACCOUNTS, NINO, noEnrolments.enrolments)
          mockAuditPTEnrolled(MULTIPLE_ACCOUNTS, requestWithUserDetails(), messagesApi)

          val result = controller
            .accountCheck(returnUrl)
            .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some(
            UrlPaths.enrolledPTNoSAOnAnyAccountPath
          )
        }
      }

      s"not enrol for PT and redirect to ${UrlPaths.enrolledPTNoSAOnAnyAccountPath}" when {
        "the user has already been assigned the PT enrolment" in new TestHelper {
          mockAuthCallWithPT()
          mockSaveDataToCache
          mockAccountCheckSuccess(MULTIPLE_ACCOUNTS)
          mockAccountShouldNotBeThrottled(MULTIPLE_ACCOUNTS, NINO, ptEnrolmentOnly.enrolments)
          mockDeleteDataFromCache

          val result = controller
            .accountCheck(returnUrl)
            .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some(
            UrlPaths.enrolledPTNoSAOnAnyAccountPath
          )
        }
      }
    }

    "multiple credential exists for a given nino and current credential has SA enrolment" should {
      s"enrol for PT and redirect to ${UrlPaths.enrolledPTWithSAAccountPath}" when {
        "the current user hasn't already been assigned a PT enrolment" in new TestHelper {
          mockAuthCallWithSA()
          mockSaveDataToCache
          mockAccountCheckSuccess(SA_ASSIGNED_TO_CURRENT_USER)
          mockSilentEnrolSuccess
          mockAccountShouldNotBeThrottled(SA_ASSIGNED_TO_CURRENT_USER, NINO, saEnrolmentOnly.enrolments)
          mockAuditPTEnrolled(SA_ASSIGNED_TO_CURRENT_USER, requestWithUserDetails(userDetailsWithSAEnrolment), messagesApi)

          val result = controller
            .accountCheck(returnUrl)
            .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some(
            UrlPaths.enrolledPTWithSAAccountPath
          )
        }
      }

      s"not enrol for PT and redirect to ${UrlPaths.enrolledPTWithSAAccountPath}" when {
        "the current user has already been assigned a PT enrolment" in new TestHelper {
          mockAuthCallWithPT(true)
          mockSaveDataToCache
          mockAccountCheckSuccess(SA_ASSIGNED_TO_CURRENT_USER)
          mockAccountShouldNotBeThrottled(SA_ASSIGNED_TO_CURRENT_USER, NINO, saAndptEnrolments.enrolments)
          mockDeleteDataFromCache

          val result = controller
            .accountCheck(returnUrl)
            .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some(
            UrlPaths.enrolledPTWithSAAccountPath
          )
        }
      }
    }

    "multiple credential exists for a given nino and a non signed in account has SA enrolment" should {
      s"redirect ${UrlPaths.saOnOtherAccountInterruptPath}" when {
        "the PT enrolment has not already been assigned" in new TestHelper {
          mockAuthCall()
          mockSaveDataToCache
          mockAccountCheckSuccess(SA_ASSIGNED_TO_OTHER_USER)
          mockAccountShouldNotBeThrottled(SA_ASSIGNED_TO_OTHER_USER, NINO, noEnrolments.enrolments)

          (mockTeaSessionCache.removeRecord(_: RequestWithUserDetailsFromSession[_]))
            .expects(*)
            .never()

          val result = controller
            .accountCheck(returnUrl)
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
          mockSaveDataToCache
          mockAccountCheckSuccess(SA_ASSIGNED_TO_OTHER_USER)
          mockAccountShouldNotBeThrottled(SA_ASSIGNED_TO_OTHER_USER, NINO, ptEnrolmentOnly.enrolments)
          mockDeleteDataFromCache

          val result = controller
            .accountCheck(returnUrl)
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
        mockSaveDataToCache
        mockGetAccountTypeFailure(UnexpectedResponseFromIV)

        val res = controller
          .accountCheck(returnUrl)
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(res) shouldBe INTERNAL_SERVER_ERROR
        contentAsString(res) should include(messages("enrolmentError.heading"))
      }
    }

    "throttled" should {
      "redirect user to their redirect url" in new TestHelper {
        mockAuthCall()
        mockSaveDataToCache
        mockAccountShouldBeThrottled(SA_ASSIGNED_TO_OTHER_USER, NINO, noEnrolments.enrolments)
        mockAccountCheckSuccess(SA_ASSIGNED_TO_OTHER_USER)
        mockDeleteDataFromCache
        val res = controller
          .accountCheck(returnUrl)
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
        mockSaveDataToCache
        mockErrorFromThrottlingService(SA_ASSIGNED_TO_OTHER_USER, NINO, noEnrolments.enrolments)
        mockAccountCheckSuccess(SA_ASSIGNED_TO_OTHER_USER)

        val res = controller
          .accountCheck(returnUrl)
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(res) shouldBe INTERNAL_SERVER_ERROR
        contentAsString(res) should include(messages("enrolmentError.heading"))
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
            ] ~ Option[AffinityGroup] ~ Option[String]
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
            ] ~ Option[AffinityGroup] ~ Option[String]
          ]
        )(_: HeaderCarrier, _: ExecutionContext))
        .expects(predicates, retrievals, *, *)
        .returning(Future.successful(retrievalResponse(enrolments = saEnrolmentOnly)))


    def mockAuthCallWithPT(hasSA: Boolean = false) = {
      val enrolments = if(hasSA) saAndptEnrolments else ptEnrolmentOnly
      (mockAuthConnector
        .authorise(
          _: Predicate,
          _: Retrieval[
            ((Option[String] ~ Option[Credentials]) ~ Enrolments) ~ Option[
              String
            ] ~ Option[AffinityGroup] ~ Option[String]
          ]
        )(_: HeaderCarrier, _: ExecutionContext))
        .expects(predicates, retrievals, *, *)
        .returning(Future.successful(retrievalResponse(enrolments = enrolments)))
    }

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
          EitherT.right[TaxEnrolmentAssignmentErrors](Future.successful(()))
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

    def mockAuditPTEnrolled(accountType: AccountTypes.Value,
                            requestWithUserDetailsFromSession: RequestWithUserDetailsFromSession[_],
                            messagesApi: MessagesApi) = {
      val expectedAudit = AuditEvent.auditSuccessfullyEnrolledPTWhenSANotOnOtherAccount(accountType)(
        requestWithUserDetailsFromSession, messagesApi)
      (mockAuditHandler
        .audit(_: AuditEvent)(_: HeaderCarrier))
        .expects(expectedAudit, *)
        .returning(Future.successful((): Unit))
        .once()
    }
  }
}
