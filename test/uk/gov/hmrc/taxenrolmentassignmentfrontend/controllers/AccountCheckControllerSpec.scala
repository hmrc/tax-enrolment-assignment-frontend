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
import org.mockito.ArgumentMatchers.{any, eq => ameq}
import org.mockito.Mockito.{reset, times, verify, when}
import org.mockito.stubbing.OngoingStubbing
import org.scalatest.OneInstancePerTest
import play.api.Application
import play.api.i18n.MessagesApi
import play.api.inject.{Binding, bind}
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.{BodyParsers, Result}
import play.api.test.Helpers.*
import uk.gov.hmrc.auth.core.retrieve.{Credentials, ~}
import uk.gov.hmrc.auth.core.{AffinityGroup, AuthConnector, Enrolments}
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}
import uk.gov.hmrc.play.bootstrap.binders.RedirectUrl
import uk.gov.hmrc.service.TEAFResult
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes.*
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.actions.RequestWithUserDetailsFromSession
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.{TaxEnrolmentAssignmentErrors, UnexpectedResponseFromIV, UnexpectedResponseFromTaxEnrolments}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.TestData.*
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.{BaseSpec, UrlPaths}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.CacheMap
import uk.gov.hmrc.taxenrolmentassignmentfrontend.orchestrators.AccountCheckOrchestrator
import uk.gov.hmrc.taxenrolmentassignmentfrontend.reporting.{AuditEvent, AuditHandler}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.TEASessionCache
import uk.gov.hmrc.taxenrolmentassignmentfrontend.services.*
import uk.gov.hmrc.taxenrolmentassignmentfrontend.utils.HmrcPTEnrolment

import scala.concurrent.{ExecutionContext, Future}

class AccountCheckControllerSpec extends BaseSpec with OneInstancePerTest {

  lazy val mockSilentAssignmentService: SilentAssignmentService   = mock[SilentAssignmentService]
  lazy val mockAccountCheckOrchestrator: AccountCheckOrchestrator = mock[AccountCheckOrchestrator]
  lazy val mockAuditHandler: AuditHandler                         = mock[AuditHandler]

  lazy val mockAuthConnector: AuthConnector     = mock[AuthConnector]
  lazy val testBodyParser: BodyParsers.Default  = mock[BodyParsers.Default]
  lazy val mockTeaSessionCache: TEASessionCache = mock[TEASessionCache]
  lazy val mockHmrcPTEnrolment: HmrcPTEnrolment = mock[HmrcPTEnrolment]

  override lazy val overrides: Seq[Binding[TEASessionCache]] = Seq(
    bind[TEASessionCache].toInstance(mockTeaSessionCache)
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
      .thenReturn(EitherT.rightT[Future, UpstreamErrorResponse](()))
  }

  override def afterEach(): Unit = {
    super.afterEach()
    reset(mockTeaSessionCache)
    verify(mockHmrcPTEnrolment, times(1)).findAndDeleteWrongPTEnrolment(any(), any(), any())(any(), any())

  }

  lazy val controller: AccountCheckController = app.injector.instanceOf[AccountCheckController]

  val returnUrlValue              = "/redirect/url"
  lazy val returnUrl: RedirectUrl = RedirectUrl.apply(returnUrlValue)

  "accountCheck" when {

    "a single credential exists for a given nino with no PT enrolment" should {
      s"silently assign the HMRC-PT Enrolment and redirect to users redirect url" when {
        "the user has not been assigned the enrolment already" in new TestHelper {
          mockAuthCall()
          when(mockTeaSessionCache.removeRecord(any())).thenReturn(Future.successful(true))
          when(mockTeaSessionCache.save(any(), any())(any(), any()))
            .thenReturn(Future.successful(CacheMap("FAKE_SESSION_ID", Map.empty)))
          mockAccountCheckSuccess(SINGLE_ACCOUNT)
          mockSilentEnrolSuccess
          mockAuditPTEnrolledWhen(SINGLE_ACCOUNT, requestWithUserDetails(), messagesApi)

          val result: Future[Result] = controller
            .accountCheck(returnUrl)
            .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

          status(result)           shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some("/redirect/url")
          verify(mockTeaSessionCache, times(1)).removeRecord(any())
          verify(mockTeaSessionCache, times(1)).save(any(), any())(any(), any())

          val expectedAudit: AuditEvent = AuditEvent(
            "SuccessfullyEnrolledPersonalTax",
            "successfully-enrolled-personal-tax",
            Json
              .parse(
                s"""{"NINO":"$NINO","currentAccount":{"credentialId":"credId123","type":"SINGLE_ACCOUNT",
                   |"authProvider":"GovernmentGateway","email":"foobarwizz","affinityGroup":"Individual"}}""".stripMargin
              )
              .as[JsObject]
          )
          verify(mockAuditHandler, times(1)).audit(ameq(expectedAudit))(any[HeaderCarrier])

        }
      }

      s"not silently assign the HMRC-PT Enrolment and redirect to users redirect url" when {
        "the user has been assigned the enrolment already" in new TestHelper {
          mockAuthCallWithPT()
          when(mockTeaSessionCache.save(any(), any())(any(), any()))
            .thenReturn(Future.successful(CacheMap("FAKE_SESSION_ID", Map.empty)))

          mockAccountCheckSuccess(PT_ASSIGNED_TO_CURRENT_USER)

          // removeRecord is called twice.
          // - Once in enrolForPTIfRequired
          // - Once in handleNoneThrottledUsers
          when(mockTeaSessionCache.removeRecord(any())).thenReturn(Future.successful(true))

          val result: Future[Result] = controller
            .accountCheck(returnUrl)
            .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

          status(result)           shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some(returnUrlValue)
          verify(mockTeaSessionCache, times(1)).save(any(), any())(any(), any())
          verify(mockTeaSessionCache, times(2)).removeRecord(any())

        }
      }

      s"silently assign the HMRC-PT Enrolment and redirect to the enrolled for PT interstitial page" when {
        "the user has not been assigned the enrolment already and has multiple accounts" in new TestHelper {
          mockAuthCall()
          when(mockTeaSessionCache.save(any(), any())(any(), any()))
            .thenReturn(Future.successful(CacheMap("FAKE_SESSION_ID", Map.empty)))
          mockAccountCheckSuccess(MULTIPLE_ACCOUNTS)
          mockSilentEnrolSuccess
          mockAuditPTEnrolledWhen(MULTIPLE_ACCOUNTS, requestWithUserDetails(), messagesApi)

          val result: Future[Result] = controller
            .accountCheck(returnUrl)
            .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

          status(result)           shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some("/protect-tax-info/enrol-pt/enrolment-success-no-sa")
          verify(mockTeaSessionCache, times(0)).removeRecord(any())
          verify(mockTeaSessionCache, times(1)).save(any(), any())(any(), any())

          val expectedAudit: AuditEvent = AuditEvent(
            "SuccessfullyEnrolledPersonalTax",
            "successfully-enrolled-personal-tax",
            Json
              .parse(
                s"""{"NINO":"$NINO","currentAccount":{"credentialId":"credId123","type":"MULTIPLE_ACCOUNTS",
                   |"authProvider":"GovernmentGateway","email":"foobarwizz","affinityGroup":"Individual"}}""".stripMargin
              )
              .as[JsObject]
          )
          verify(mockAuditHandler, times(1)).audit(ameq(expectedAudit))(any[HeaderCarrier])

        }
      }

      "return an error page if there was an error assigning the enrolment" in new TestHelper {
        mockAuthCall()
        when(mockTeaSessionCache.save(any(), any())(any(), any()))
          .thenReturn(Future.successful(CacheMap("FAKE_SESSION_ID", Map.empty)))
        mockAccountCheckSuccess(SINGLE_ACCOUNT)
        mockSilentEnrolFailure

        val result: Future[Result] = controller
          .accountCheck(returnUrl)
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(result)        shouldBe INTERNAL_SERVER_ERROR
        contentAsString(result) should include(messages("enrolmentError.heading"))
        verify(mockTeaSessionCache, times(1)).save(any(), any())(any(), any())

      }

    }

    "a single credential exists for a given nino that is already enrolled for PT" should {
      s"redirect to ${UrlPaths.returnUrl}" in new TestHelper {
        mockAuthCall()
        when(mockTeaSessionCache.save(any(), any())(any(), any()))
          .thenReturn(Future.successful(CacheMap("FAKE_SESSION_ID", Map.empty)))

        mockAccountCheckSuccess(PT_ASSIGNED_TO_CURRENT_USER)
        when(
          mockTeaSessionCache
            .removeRecord(any())
        ).thenReturn(Future.successful(true))

        val result: Future[Result] = controller
          .accountCheck(returnUrl)
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(result)           shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(UrlPaths.returnUrl)
        verify(mockTeaSessionCache, times(1)).removeRecord(any())
        verify(mockTeaSessionCache, times(1)).save(any(), any())(any(), any())

      }
    }

    "a PT enrolment exists on another users account" should {
      s"redirect to ${UrlPaths.ptOnOtherAccountPath}" in new TestHelper {
        mockAuthCall()
        when(mockTeaSessionCache.save(any(), any())(any(), any()))
          .thenReturn(Future.successful(CacheMap("FAKE_SESSION_ID", Map.empty)))
        mockAccountCheckSuccess(PT_ASSIGNED_TO_OTHER_USER)

        val result: Future[Result] = controller
          .accountCheck(returnUrl)
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(result)           shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some("/protect-tax-info/no-pt-enrolment")
        verify(mockTeaSessionCache, times(1)).save(any(), any())(any(), any())

      }
    }

    "multiple credential exists for a given nino and no enrolments exists" should {
      s"enrol for PT and redirect to ${UrlPaths.enrolledPTNoSAOnAnyAccountPath}" when {
        "the user has not already been assigned the PT enrolment" in new TestHelper {
          mockAuthCall()
          when(mockTeaSessionCache.save(any(), any())(any(), any()))
            .thenReturn(Future.successful(CacheMap("FAKE_SESSION_ID", Map.empty)))

          mockAccountCheckSuccess(MULTIPLE_ACCOUNTS)
          mockSilentEnrolSuccess
          mockAuditPTEnrolledWhen(MULTIPLE_ACCOUNTS, requestWithUserDetails(), messagesApi)

          val result: Future[Result] = controller
            .accountCheck(returnUrl)
            .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

          status(result)           shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some(
            "/protect-tax-info/enrol-pt/enrolment-success-no-sa"
          )

          verify(mockTeaSessionCache, times(1)).save(any(), any())(any(), any())
          val expectedAudit: AuditEvent = AuditEvent(
            "SuccessfullyEnrolledPersonalTax",
            "successfully-enrolled-personal-tax",
            Json
              .parse(
                s"""{"NINO":"$NINO","currentAccount":{"credentialId":"credId123","type":"MULTIPLE_ACCOUNTS",
                   |"authProvider":"GovernmentGateway","email":"foobarwizz","affinityGroup":"Individual"}}""".stripMargin
              )
              .as[JsObject]
          )
          verify(mockAuditHandler, times(1)).audit(ameq(expectedAudit))(any[HeaderCarrier])

        }
      }

      s"not enrol for PT and redirect to ${UrlPaths.enrolledPTNoSAOnAnyAccountPath}" when {
        "the user has already been assigned the PT enrolment" in new TestHelper {
          mockAuthCallWithPT()
          when(mockTeaSessionCache.save(any(), any())(any(), any()))
            .thenReturn(Future.successful(CacheMap("FAKE_SESSION_ID", Map.empty)))
          mockAccountCheckSuccess(MULTIPLE_ACCOUNTS)
          when(
            mockTeaSessionCache
              .removeRecord(any())
          ).thenReturn(Future.successful(true))

          val result: Future[Result] = controller
            .accountCheck(returnUrl)
            .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

          status(result)           shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some(
            "/protect-tax-info/enrol-pt/enrolment-success-no-sa"
          )
          verify(mockTeaSessionCache, times(1)).removeRecord(any())
          verify(mockTeaSessionCache, times(1)).save(any(), any())(any(), any())

        }
      }
    }

    "multiple credential exists for a given nino and current credential has SA enrolment" should {
      s"enrol for PT and redirect to ${UrlPaths.enrolledPTWithSAAccountPath}" when {
        "the current user hasn't already been assigned a PT enrolment" in new TestHelper {
          mockAuthCallWithSA()
          when(mockTeaSessionCache.save(any(), any())(any(), any()))
            .thenReturn(Future.successful(CacheMap("FAKE_SESSION_ID", Map.empty)))
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

          status(result)           shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some(
            "/protect-tax-info/enrol-pt/enrolment-success-sa-user-id"
          )

          verify(mockTeaSessionCache, times(1)).save(any(), any())(any(), any())
          val expectedAudit: AuditEvent = AuditEvent(
            "SuccessfullyEnrolledPersonalTax",
            "successfully-enrolled-personal-tax",
            Json
              .parse(
                s"""{"NINO":"$NINO","currentAccount":{"credentialId":"credId123","type":"SA_ASSIGNED_TO_CURRENT_USER",
                   |"authProvider":"GovernmentGateway","email":"foobarwizz","affinityGroup":"Individual"},"saAccountCredentialId":"credId123"}""".stripMargin
              )
              .as[JsObject]
          )
          verify(mockAuditHandler, times(1)).audit(ameq(expectedAudit))(any[HeaderCarrier])
        }
      }
    }

    "multiple credential exists for a given nino and a non signed in account has SA enrolment" should {
      s"redirect ${UrlPaths.saOnOtherAccountInterruptPath}" when {
        "the PT enrolment has not already been assigned" in new TestHelper {
          mockAuthCall()
          when(mockTeaSessionCache.save(any(), any())(any(), any()))
            .thenReturn(Future.successful(CacheMap("FAKE_SESSION_ID", Map.empty)))
          mockAccountCheckSuccess(SA_ASSIGNED_TO_OTHER_USER)

          val result: Future[Result] = controller
            .accountCheck(returnUrl)
            .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

          status(result)           shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some(
            "/protect-tax-info/enrol-pt/more-than-one-user-id"
          )
          verify(mockTeaSessionCache, times(1)).save(any(), any())(any(), any())
          verify(mockTeaSessionCache, times(0)).removeRecord(any())

        }
      }

      s"redirect ${UrlPaths.enrolledPTSAOnOtherAccountPath}" when {
        "the PT enrolment has not already been assigned" in new TestHelper {
          mockAuthCallWithPT()
          when(mockTeaSessionCache.save(any(), any())(any(), any()))
            .thenReturn(Future.successful(CacheMap("FAKE_SESSION_ID", Map.empty)))
          mockAccountCheckSuccess(SA_ASSIGNED_TO_OTHER_USER)
          when(
            mockTeaSessionCache
              .removeRecord(any())
          ).thenReturn(Future.successful(true))

          val result: Future[Result] = controller
            .accountCheck(returnUrl)
            .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

          status(result)           shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some(
            "/protect-tax-info/enrol-pt/choose-two-user-ids"
          )
          verify(mockTeaSessionCache, times(1)).removeRecord(any())
          verify(mockTeaSessionCache, times(1)).save(any(), any())(any(), any())
        }
      }
    }

    "a no credentials exists in IV for a given nino" should {
      "render the error page" in new TestHelper {
        mockAuthCall()
        when(mockTeaSessionCache.save(any(), any())(any(), any()))
          .thenReturn(Future.successful(CacheMap("FAKE_SESSION_ID", Map.empty)))

        mockGetAccountTypeFailure(UnexpectedResponseFromIV)

        val res: Future[Result] = controller
          .accountCheck(returnUrl)
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(res)        shouldBe INTERNAL_SERVER_ERROR
        contentAsString(res) should include(messages("enrolmentError.heading"))
        verify(mockTeaSessionCache, times(1)).save(any(), any())(any(), any())
      }
    }
  }

  class TestHelper {
    def mockAuthCall(): OngoingStubbing[Future[
      Option[String] ~ Option[Credentials] ~ Enrolments ~ Option[String] ~ Option[AffinityGroup] ~ Option[String]
    ]] =
      when(mockAuthConnector.authorise(ameq(predicates), ameq(retrievals))(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future.successful(retrievalResponse()))

    def mockAuthCallWithSA(): OngoingStubbing[Future[
      Option[String] ~ Option[Credentials] ~ Enrolments ~ Option[String] ~ Option[AffinityGroup] ~ Option[String]
    ]] =
      when(mockAuthConnector.authorise(ameq(predicates), ameq(retrievals))(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future.successful(retrievalResponse(enrolments = saEnrolmentOnly)))

    def mockAuthCallWithPT(hasSA: Boolean = false): OngoingStubbing[Future[
      Option[String] ~ Option[Credentials] ~ Enrolments ~ Option[String] ~ Option[AffinityGroup] ~ Option[String]
    ]] = {
      val enrolments = if (hasSA) saAndptEnrolments else ptEnrolmentOnly
      when(mockAuthConnector.authorise(ameq(predicates), ameq(retrievals))(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future.successful(retrievalResponse(enrolments = enrolments)))
    }

    def mockGetAccountTypeFailure(
      error: TaxEnrolmentAssignmentErrors
    ): OngoingStubbing[EitherT[Future, TaxEnrolmentAssignmentErrors, AccountTypes.Value]] =
      when(mockAccountCheckOrchestrator.getAccountType(any(), any(), any())).thenReturn(createInboundResultError(error))

    def mockAccountCheckSuccess(
      accountType: AccountTypes.Value
    ): OngoingStubbing[EitherT[Future, TaxEnrolmentAssignmentErrors, AccountTypes.Value]] =
      when(mockAccountCheckOrchestrator.getAccountType(any(), any(), any()))
        .thenReturn(createInboundResult(accountType))

    def mockSilentEnrolSuccess: OngoingStubbing[TEAFResult[Unit]] =
      when(mockSilentAssignmentService.enrolUser()(any(), any(), any()))
        .thenReturn(EitherT.right[TaxEnrolmentAssignmentErrors](Future.successful(())))

    def mockSilentEnrolFailure: OngoingStubbing[TEAFResult[Unit]] =
      when(mockSilentAssignmentService.enrolUser()(any(), any(), any()))
        .thenReturn(createInboundResultError(UnexpectedResponseFromTaxEnrolments))

    def mockAuditPTEnrolledWhen(
      accountType: AccountTypes.Value,
      requestWithUserDetailsFromSession: RequestWithUserDetailsFromSession[_],
      messagesApi: MessagesApi
    ): OngoingStubbing[Future[Unit]] = {
      val expectedAudit = AuditEvent(
        auditType = "SuccessfullyEnrolledPersonalTax",
        transactionName = "successfully-enrolled-personal-tax",
        detail = Json.obj()
      )
      when(mockAuditHandler.audit(ameq(expectedAudit))(any[HeaderCarrier])).thenReturn(Future.successful((): Unit))
    }
  }
}
