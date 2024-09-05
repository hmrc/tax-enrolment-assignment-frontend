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
import org.mockito.ArgumentMatchers.{any, anyString, eq => ameq}
import org.mockito.MockitoSugar.{mock, times, verify, when}
import org.mockito.stubbing.ScalaOngoingStubbing
import org.scalatest.OneInstancePerTest
import play.api.Application
import play.api.http.Status.SEE_OTHER
import play.api.i18n.MessagesApi
import play.api.inject.{Binding, bind}
import play.api.mvc.{BodyParsers, Result}
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.retrieve.{Credentials, ~}
import uk.gov.hmrc.auth.core.{AffinityGroup, AuthConnector, Enrolments}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.binders.RedirectUrl
import uk.gov.hmrc.service.TEAFResult
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.actions.DataRequest
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.{TaxEnrolmentAssignmentErrors, UnexpectedResponseFromIV, UnexpectedResponseFromTaxEnrolments}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.TestData._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.{BaseSpec, UrlPaths}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.UserAnswers
import uk.gov.hmrc.taxenrolmentassignmentfrontend.orchestrators.AccountCheckOrchestrator
import uk.gov.hmrc.taxenrolmentassignmentfrontend.reporting.{AuditEvent, AuditHandler}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.JourneyCacheRepository
import uk.gov.hmrc.taxenrolmentassignmentfrontend.services._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.utils.HmrcPTEnrolment

import scala.concurrent.{ExecutionContext, Future}

class AccountCheckControllerSpec extends BaseSpec with OneInstancePerTest {

  lazy val mockSilentAssignmentService: SilentAssignmentService = mock[SilentAssignmentService]
  lazy val mockAccountCheckOrchestrator: AccountCheckOrchestrator = mock[AccountCheckOrchestrator]
  lazy val mockAuditHandler: AuditHandler = mock[AuditHandler]

  lazy val mockAuthConnector: AuthConnector = mock[AuthConnector]
  lazy val testBodyParser: BodyParsers.Default = mock[BodyParsers.Default]
  override val mockJourneyCacheRepository: JourneyCacheRepository = mock[JourneyCacheRepository]

  lazy val mockHmrcPTEnrolment: HmrcPTEnrolment = mock[HmrcPTEnrolment]

  override lazy val overrides: Seq[Binding[JourneyCacheRepository]] = Seq(
    bind[JourneyCacheRepository].toInstance(mockJourneyCacheRepository)
  )

  override implicit lazy val app: Application = localGuiceApplicationBuilder()
    .overrides(
      bind[SilentAssignmentService].toInstance(mockSilentAssignmentService),
      bind[AccountCheckOrchestrator].toInstance(mockAccountCheckOrchestrator),
      bind[AuditHandler].toInstance(mockAuditHandler),
      bind[AuthConnector].toInstance(mockAuthConnector),
      bind[BodyParsers.Default].toInstance(testBodyParser),
      bind[HmrcPTEnrolment].toInstance(mockHmrcPTEnrolment)
    )
    .build()

  override def beforeEach(): Unit = {
    super.beforeEach()
    when(mockHmrcPTEnrolment.findAndDeleteWrongPTEnrolment(any(), any(), any())(any(), any()))
      .thenReturn(EitherT.rightT(()))
  }

  override def afterEach(): Unit = {
    super.afterEach()
    verify(mockHmrcPTEnrolment, times(1)).findAndDeleteWrongPTEnrolment(any(), any(), any())(any(), any())

  }

  lazy val controller: AccountCheckController = app.injector.instanceOf[AccountCheckController]

  val returnUrlValue = "/redirect/url"
  lazy val returnUrl: RedirectUrl = RedirectUrl.apply(returnUrlValue)
  val nino: Nino = generateNino

  "accountCheck" when {

    "a single credential exists for a given nino with no PT enrolment" should {
      s"silently assign the HMRC-PT Enrolment and redirect to users redirect url" when {
        "the user has not been assigned the enrolment already" in new TestHelper {
          mockAuthCall()

          val mockUserAnswers: UserAnswers = UserAnswers("FAKE_SESSION_ID", nino.nino)

          when(mockJourneyCacheRepository.get(any(), any()))
            .thenReturn(Future.successful(Some(mockUserAnswers)))

          mockAccountCheckSuccess(SINGLE_OR_MULTIPLE_ACCOUNTS)
          mockSilentEnrolSuccess
          mockAuditPTEnrolledWhen(SINGLE_OR_MULTIPLE_ACCOUNTS, requestWithUserDetails(), messagesApi)

          val result: Future[Result] = controller
            .accountCheck(returnUrl)
            .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some("/protect-tax-info/enrol-pt/enrolment-success-no-sa")
          verify(mockJourneyCacheRepository, times(0)).clear(any(), any())
          mockAuditPTEnrolledVerify(SINGLE_OR_MULTIPLE_ACCOUNTS, requestWithUserDetails(), messagesApi)

        }
      }

      s"not silently assign the HMRC-PT Enrolment and redirect to users redirect url" when {
        "the user has been assigned the enrolment already" in new TestHelper {
          mockAuthCallWithPT()
          val mockUserAnswers: UserAnswers = UserAnswers("FAKE_SESSION_ID", nino.nino)
          when(mockJourneyCacheRepository.get(any(), any()))
            .thenReturn(Future.successful(Some(mockUserAnswers)))
          mockAccountCheckSuccess(PT_ASSIGNED_TO_CURRENT_USER)

          // removeRecord is called twice.
          // - Once in enrolForPTIfRequired
          // - Once in handleNoneThrottledUsers
          when(mockJourneyCacheRepository.clear(anyString(), anyString())) thenReturn Future.successful(true)

          val result: Future[Result] = controller
            .accountCheck(returnUrl)
            .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some(returnUrlValue)
          verify(mockJourneyCacheRepository, times(2)).clear(anyString(), anyString())
        }
      }

      "return an error page if there was an error assigning the enrolment" in new TestHelper {
        mockAuthCall()
        val mockUserAnswers: UserAnswers = UserAnswers("FAKE_SESSION_ID", nino.nino)
        when(mockJourneyCacheRepository.get(any(), any()))
          .thenReturn(Future.successful(Some(mockUserAnswers)))

        mockAccountCheckSuccess(SINGLE_OR_MULTIPLE_ACCOUNTS)
        mockSilentEnrolFailure

        val result: Future[Result] = controller
          .accountCheck(returnUrl)
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(result) shouldBe INTERNAL_SERVER_ERROR
        contentAsString(result) should include(messages("enrolmentError.heading"))
      }

    }

    "a single credential exists for a given nino that is already enrolled for PT" should {
      s"redirect to ${UrlPaths.returnUrl}" in new TestHelper {
        mockAuthCall()
        val mockUserAnswers: UserAnswers = UserAnswers("FAKE_SESSION_ID", nino.nino)
        when(mockJourneyCacheRepository.get(any(), any()))
          .thenReturn(Future.successful(Some(mockUserAnswers)))

        mockAccountCheckSuccess(PT_ASSIGNED_TO_CURRENT_USER)
        when(mockJourneyCacheRepository.clear(anyString(), anyString())) thenReturn Future.successful(true)

        val result: Future[Result] = controller
          .accountCheck(returnUrl)
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(UrlPaths.returnUrl)
        verify(mockJourneyCacheRepository, times(1)).clear(anyString(), anyString())

      }
    }

    "a PT enrolment exists on another users account" should {
      s"redirect to ${UrlPaths.ptOnOtherAccountPath}" in new TestHelper {
        mockAuthCall()
        val mockUserAnswers: UserAnswers = UserAnswers("FAKE_SESSION_ID", nino.nino)
        when(mockJourneyCacheRepository.get(any(), any()))
          .thenReturn(Future.successful(Some(mockUserAnswers)))

        mockAccountCheckSuccess(PT_ASSIGNED_TO_OTHER_USER)

        val result: Future[Result] = controller
          .accountCheck(returnUrl)
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some("/protect-tax-info/no-pt-enrolment")
      }
    }

    "multiple credential exists for a given nino and no enrolments exists" should {
      s"enrol for PT and redirect to ${UrlPaths.enrolledPTNoSAOnAnyAccountPath}" when {
        "the user has not already been assigned the PT enrolment" in new TestHelper {
          mockAuthCall()
          val mockUserAnswers: UserAnswers = UserAnswers("FAKE_SESSION_ID", nino.nino)
          when(mockJourneyCacheRepository.get(any(), any()))
            .thenReturn(Future.successful(Some(mockUserAnswers)))

          mockAccountCheckSuccess(SINGLE_OR_MULTIPLE_ACCOUNTS)
          mockSilentEnrolSuccess
          mockAuditPTEnrolledWhen(SINGLE_OR_MULTIPLE_ACCOUNTS, requestWithUserDetails(), messagesApi)

          val result: Future[Result] = controller
            .accountCheck(returnUrl)
            .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some(
            "/protect-tax-info/enrol-pt/enrolment-success-no-sa"
          )
          mockAuditPTEnrolledVerify(SINGLE_OR_MULTIPLE_ACCOUNTS, requestWithUserDetails(), messagesApi)

        }
      }

      s"not enrol for PT and redirect to ${UrlPaths.enrolledPTNoSAOnAnyAccountPath}" when {
        "the user has already been assigned the PT enrolment" in new TestHelper {
          mockAuthCallWithPT()
          val mockUserAnswers: UserAnswers = UserAnswers("FAKE_SESSION_ID", nino.nino)
          when(mockJourneyCacheRepository.get(any(), any()))
            .thenReturn(Future.successful(Some(mockUserAnswers)))

          mockAccountCheckSuccess(SINGLE_OR_MULTIPLE_ACCOUNTS)
          when(mockJourneyCacheRepository.clear(anyString(), anyString())) thenReturn Future.successful(true)

          val result: Future[Result] = controller
            .accountCheck(returnUrl)
            .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some(
            "/protect-tax-info/enrol-pt/enrolment-success-no-sa"
          )

          verify(mockJourneyCacheRepository, times(1)).clear(anyString(), anyString())
        }
      }
    }

    "multiple credential exists for a given nino and current credential has SA enrolment" should {
      s"enrol for PT and redirect to ${UrlPaths.enrolledPTWithSAAccountPath}" when {
        "the current user hasn't already been assigned a PT enrolment" in new TestHelper {
          mockAuthCallWithSA()
          val mockUserAnswers: UserAnswers = UserAnswers("FAKE_SESSION_ID", nino.nino)
          when(mockJourneyCacheRepository.get(any(), any()))
            .thenReturn(Future.successful(Some(mockUserAnswers)))

          mockAccountCheckSuccess(SA_ASSIGNED_TO_CURRENT_USER)
          mockSilentEnrolSuccess
          mockAuditPTEnrolledWhen(
            SA_ASSIGNED_TO_CURRENT_USER,
            requestWithUserDetails(userDetailsWithSAEnrolment),
            messagesApi
          )

          val result: Future[Result] = controller
            .accountCheck(returnUrl)
            .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some(
            "/protect-tax-info/enrol-pt/enrolment-success-sa-user-id"
          )

          mockAuditPTEnrolledVerify(
            SA_ASSIGNED_TO_CURRENT_USER,
            requestWithUserDetails(userDetailsWithSAEnrolment),
            messagesApi
          )
        }
      }
    }

    "multiple credential exists for a given nino and a non signed in account has SA enrolment" should {
      s"redirect ${UrlPaths.saOnOtherAccountInterruptPath}" when {
        "the PT enrolment has not already been assigned" in new TestHelper {
          mockAuthCall()
          val mockUserAnswers: UserAnswers = UserAnswers("FAKE_SESSION_ID", nino.nino)
          when(mockJourneyCacheRepository.get(any(), any()))
            .thenReturn(Future.successful(Some(mockUserAnswers)))

          mockAccountCheckSuccess(SA_ASSIGNED_TO_OTHER_USER)

          val result: Future[Result] = controller
            .accountCheck(returnUrl)
            .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some(
            "/protect-tax-info/enrol-pt/more-than-one-user-id"
          )

          verify(mockJourneyCacheRepository, times(0)).clear(anyString(), anyString())
        }
      }

      s"redirect ${UrlPaths.enrolledPTSAOnOtherAccountPath}" when {
        "the PT enrolment has not already been assigned" in new TestHelper {
          mockAuthCallWithPT()
          val mockUserAnswers: UserAnswers = UserAnswers("FAKE_SESSION_ID", nino.nino)
          when(mockJourneyCacheRepository.get(any(), any()))
            .thenReturn(Future.successful(Some(mockUserAnswers)))
          mockAccountCheckSuccess(SA_ASSIGNED_TO_OTHER_USER)

          when(mockJourneyCacheRepository.clear(anyString(), anyString())) thenReturn Future.successful(true)

          val result: Future[Result] = controller
            .accountCheck(returnUrl)
            .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some(
            "/protect-tax-info/enrol-pt/choose-two-user-ids"
          )
          verify(mockJourneyCacheRepository, times(1)).clear(anyString(), anyString())
          verify(mockJourneyCacheRepository, times(1)).clear(anyString(), anyString())
        }
      }
    }

    "a no credentials exists in IV for a given nino" should {
      "render the error page" in new TestHelper {
        mockAuthCall()
        val mockUserAnswers: UserAnswers = UserAnswers("FAKE_SESSION_ID", nino.nino)
        when(mockJourneyCacheRepository.get(any(), any()))
          .thenReturn(Future.successful(Some(mockUserAnswers)))

        mockGetAccountTypeFailure(UnexpectedResponseFromIV)

        val res: Future[Result] = controller
          .accountCheck(returnUrl)
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(res) shouldBe INTERNAL_SERVER_ERROR
        contentAsString(res) should include(messages("enrolmentError.heading"))
      }
    }
  }

  class TestHelper {
    def mockAuthCall(): ScalaOngoingStubbing[Future[
      Option[String] ~ Option[Credentials] ~ Enrolments ~ Option[String] ~ Option[AffinityGroup] ~ Option[String]
    ]] =
      when(mockAuthConnector.authorise(ameq(predicates), ameq(retrievals))(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future.successful(retrievalResponse()))

    def mockAuthCallWithSA(): ScalaOngoingStubbing[Future[
      Option[String] ~ Option[Credentials] ~ Enrolments ~ Option[String] ~ Option[AffinityGroup] ~ Option[String]
    ]] =
      when(mockAuthConnector.authorise(ameq(predicates), ameq(retrievals))(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future.successful(retrievalResponse(enrolments = saEnrolmentOnly)))

    def mockAuthCallWithPT(hasSA: Boolean = false): ScalaOngoingStubbing[Future[
      Option[String] ~ Option[Credentials] ~ Enrolments ~ Option[String] ~ Option[AffinityGroup] ~ Option[String]
    ]] = {
      val enrolments = if (hasSA) saAndptEnrolments else ptEnrolmentOnly
      when(mockAuthConnector.authorise(ameq(predicates), ameq(retrievals))(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future.successful(retrievalResponse(enrolments = enrolments)))
    }

    def mockGetAccountTypeFailure(
      error: TaxEnrolmentAssignmentErrors
    ): ScalaOngoingStubbing[EitherT[Future, TaxEnrolmentAssignmentErrors, AccountTypes.Value]] =
      when(mockAccountCheckOrchestrator.getAccountType(any())(any(), any(), any()))
        .thenReturn(createInboundResultError(error))

    def mockAccountCheckSuccess(
      accountType: AccountTypes.Value
    ): ScalaOngoingStubbing[EitherT[Future, TaxEnrolmentAssignmentErrors, AccountTypes.Value]] =
      when(mockAccountCheckOrchestrator.getAccountType(any())(any(), any(), any()))
        .thenReturn(createInboundResult(accountType))

    def mockSilentEnrolSuccess: ScalaOngoingStubbing[TEAFResult[Unit]] =
      when(mockSilentAssignmentService.enrolUser()(any(), any(), any()))
        .thenReturn(EitherT.right[TaxEnrolmentAssignmentErrors](Future.successful(())))

    def mockSilentEnrolFailure: ScalaOngoingStubbing[TEAFResult[Unit]] =
      when(mockSilentAssignmentService.enrolUser()(any(), any(), any()))
        .thenReturn(createInboundResultError(UnexpectedResponseFromTaxEnrolments))

    def mockAuditPTEnrolledWhen(
      accountType: AccountTypes.Value,
      requestWithUserDetailsFromSession: DataRequest[_],
      messagesApi: MessagesApi
    ): ScalaOngoingStubbing[Future[Unit]] = {
      val expectedAudit = AuditEvent.auditSuccessfullyEnrolledPTWhenSANotOnOtherAccount(accountType)(
        requestWithUserDetailsFromSession,
        messagesApi
      )
      when(mockAuditHandler.audit(ameq(expectedAudit))(any[HeaderCarrier])).thenReturn(Future.successful((): Unit))
    }

    def mockAuditPTEnrolledVerify(
      accountType: AccountTypes.Value,
      requestWithUserDetailsFromSession: DataRequest[_],
      messagesApi: MessagesApi
    ): Future[Unit] = {
      val expectedAudit = AuditEvent.auditSuccessfullyEnrolledPTWhenSANotOnOtherAccount(accountType)(
        requestWithUserDetailsFromSession,
        messagesApi
      )
      verify(mockAuditHandler, times(1)).audit(ameq(expectedAudit))(any[HeaderCarrier])
    }
  }
}
