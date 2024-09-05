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

import org.mockito.ArgumentMatchers.{any, eq => ameq}
import org.mockito.MockitoSugar.{mock, times, verify, when}
import play.api.Application
import play.api.http.Status.OK
import play.api.inject.{Binding, bind}
import play.api.mvc.BodyParsers
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes.PT_ASSIGNED_TO_OTHER_USER
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.TestData._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.{ControllersBaseSpec, UrlPaths}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.UserAnswers
import uk.gov.hmrc.taxenrolmentassignmentfrontend.orchestrators.{AccountCheckOrchestrator, MultipleAccountsOrchestrator}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.pages.{AccountTypePage, RedirectUrlPage}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.reporting.{AuditEvent, AuditHandler}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.JourneyCacheRepository
import uk.gov.hmrc.taxenrolmentassignmentfrontend.services.SilentAssignmentService
import uk.gov.hmrc.taxenrolmentassignmentfrontend.views.html.PTEnrolmentOnAnotherAccount

import scala.concurrent.{ExecutionContext, Future}

class PTEnrolmentOnOtherAccountControllerSpec extends ControllersBaseSpec {

  lazy val mockSilentAssignmentService: SilentAssignmentService = mock[SilentAssignmentService]
  lazy val mockAccountCheckOrchestrator: AccountCheckOrchestrator = mock[AccountCheckOrchestrator]
  lazy val mockAuditHandler: AuditHandler = mock[AuditHandler]

  lazy val testBodyParser: BodyParsers.Default = mock[BodyParsers.Default]
  lazy val mockMultipleAccountsOrchestrator: MultipleAccountsOrchestrator = mock[MultipleAccountsOrchestrator]

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
      bind[MultipleAccountsOrchestrator].toInstance(mockMultipleAccountsOrchestrator)
    )
    .build()

  lazy val controller: PTEnrolmentOnOtherAccountController =
    app.injector.instanceOf[PTEnrolmentOnOtherAccountController]

  val view: PTEnrolmentOnAnotherAccount =
    app.injector.instanceOf[PTEnrolmentOnAnotherAccount]

  "view" when {
    "the user with no SA has another account with PT enrolment" should {
      "render the pt on another page with no Access SA text" in {
        val ptEnrolmentDataModelNone = ptEnrolmentDataModel(None)

        when(mockAuthConnector.authorise(ameq(predicates), ameq(retrievals))(any[HeaderCarrier], any[ExecutionContext]))
          .thenReturn(Future.successful(retrievalResponse()))

        when(mockMultipleAccountsOrchestrator.getCurrentAndPTAAndSAIfExistsForUser(any(), any(), any()))
          .thenReturn(createInboundResult(ptEnrolmentDataModelNone))

        val mockUserAnswers: UserAnswers = UserAnswers("id", generateNino.nino)
          .setOrException(AccountTypePage, randomAccountType.toString)
          .setOrException(RedirectUrlPage, "foo")
        mockGetDataFromCacheForActionSuccess(mockUserAnswers)

        val auditEvent = AuditEvent.auditPTEnrolmentOnOtherAccount(
          accountDetailsWithPT.copy(lastLoginDate = Some(s"27 February 2022 ${messages("common.dateToTime")} 12:00PM"))
        )(requestWithGivenMongoData(requestWithAccountType(randomAccountType)), messagesApi)

        when(mockAuditHandler.audit(ameq(auditEvent))(any[HeaderCarrier])).thenReturn(Future.successful((): Unit))

        val result = controller.view
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(result) shouldBe OK
        contentAsString(result) shouldBe view(
          ptEnrolmentDataModelNone
            .copy(
              currentAccountDetails = ptEnrolmentDataModelNone.currentAccountDetails.copy(lastLoginDate =
                Some(s"27 February 2022 ${messages("common.dateToTime")} 12:00PM")
              ),
              ptAccountDetails = ptEnrolmentDataModelNone.ptAccountDetails.copy(lastLoginDate =
                Some(s"27 February 2022 ${messages("common.dateToTime")} 12:00PM")
              )
            )
        )(
          fakeRequest,
          messages
        ).toString
        verify(mockAuditHandler, times(1)).audit(ameq(auditEvent))(any[HeaderCarrier])
      }
    }

    "the current user with SA has another account with PT enrolment" should {
      "render the pt on another page with Access SA text" in {

        val ptEnrolmentModel = ptEnrolmentDataModel(Some(USER_ID))

        when(mockAuthConnector.authorise(ameq(predicates), ameq(retrievals))(any[HeaderCarrier], any[ExecutionContext]))
          .thenReturn(Future.successful(retrievalResponse(enrolments = saEnrolmentOnly)))

        when(mockMultipleAccountsOrchestrator.getCurrentAndPTAAndSAIfExistsForUser(any(), any(), any()))
          .thenReturn(createInboundResult(ptEnrolmentModel))

        val mockUserAnswers: UserAnswers = UserAnswers("id", generateNino.nino)
          .setOrException(AccountTypePage, randomAccountType.toString)
          .setOrException(RedirectUrlPage, "foo")
        mockGetDataFromCacheForActionSuccess(mockUserAnswers)

        val auditEvent = AuditEvent.auditPTEnrolmentOnOtherAccount(
          accountDetailsWithPT.copy(lastLoginDate = Some(s"27 February 2022 ${messages("common.dateToTime")} 12:00PM"))
        )(requestWithGivenMongoData(requestWithAccountType(randomAccountType)), messagesApi)

        when(mockAuditHandler.audit(ameq(auditEvent))(any[HeaderCarrier])).thenReturn(Future.successful((): Unit))

        val result = controller.view
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(result) shouldBe OK
        contentAsString(result) shouldBe view(
          ptEnrolmentModel.copy(
            currentAccountDetails = ptEnrolmentModel.currentAccountDetails.copy(lastLoginDate =
              Some(s"27 February 2022 ${messages("common.dateToTime")} 12:00PM")
            ),
            ptAccountDetails = ptEnrolmentModel.ptAccountDetails.copy(lastLoginDate =
              Some(s"27 February 2022 ${messages("common.dateToTime")} 12:00PM")
            )
          )
        )(
          fakeRequest,
          messages
        ).toString
        verify(mockAuditHandler, times(1)).audit(ameq(auditEvent))(any[HeaderCarrier])
      }
    }

    "the signed user has SA enrolment in session and PT enrolment on another account" should {
      "render the pt on another page with Access Self Assessment text" in {

        val ptEnrolmentModel = ptEnrolmentDataModel(Some(USER_ID))

        when(mockAuthConnector.authorise(ameq(predicates), ameq(retrievals))(any[HeaderCarrier], any[ExecutionContext]))
          .thenReturn(Future.successful(retrievalResponse(enrolments = saEnrolmentOnly)))

        when(mockMultipleAccountsOrchestrator.getCurrentAndPTAAndSAIfExistsForUser(any(), any(), any()))
          .thenReturn(createInboundResult(ptEnrolmentModel))

        val mockUserAnswers: UserAnswers = UserAnswers("id", generateNino.nino)
          .setOrException(AccountTypePage, randomAccountType.toString)
          .setOrException(RedirectUrlPage, "foo")
        mockGetDataFromCacheForActionSuccess(mockUserAnswers)

        val auditEvent = AuditEvent.auditPTEnrolmentOnOtherAccount(
          accountDetailsWithPT.copy(lastLoginDate = Some(s"27 February 2022 ${messages("common.dateToTime")} 12:00PM"))
        )(requestWithGivenMongoData(requestWithAccountType(randomAccountType)), messagesApi)

        when(mockAuditHandler.audit(ameq(auditEvent))(any[HeaderCarrier])).thenReturn(Future.successful((): Unit))

        val result = controller.view
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(result) shouldBe OK
        contentAsString(result) shouldBe view(
          ptEnrolmentModel.copy(
            currentAccountDetails = ptEnrolmentModel.currentAccountDetails.copy(lastLoginDate =
              Some(s"27 February 2022 ${messages("common.dateToTime")} 12:00PM")
            ),
            ptAccountDetails = ptEnrolmentModel.ptAccountDetails.copy(lastLoginDate =
              Some(s"27 February 2022 ${messages("common.dateToTime")} 12:00PM")
            )
          )
        )(
          fakeRequest,
          messages
        ).toString
        verify(mockAuditHandler, times(1)).audit(ameq(auditEvent))(any[HeaderCarrier])
      }
    }

    "the signed user has another account with SA enrolment which has both PT enrolment" should {
      "render the pt on another page with Access Self Assessment text" in {

        val ptEnrolmentModel = ptEnrolmentDataModel(Some(PT_USER_ID))

        when(mockAuthConnector.authorise(ameq(predicates), ameq(retrievals))(any[HeaderCarrier], any[ExecutionContext]))
          .thenReturn(Future.successful(retrievalResponse(enrolments = saEnrolmentOnly)))

        when(mockMultipleAccountsOrchestrator.getCurrentAndPTAAndSAIfExistsForUser(any(), any(), any()))
          .thenReturn(createInboundResult(ptEnrolmentModel))

        val mockUserAnswers: UserAnswers = UserAnswers("id", generateNino.nino)
          .setOrException(AccountTypePage, randomAccountType.toString)
          .setOrException(RedirectUrlPage, "foo")
        mockGetDataFromCacheForActionSuccess(mockUserAnswers)

        val auditEvent = AuditEvent.auditPTEnrolmentOnOtherAccount(
          accountDetailsWithPT.copy(lastLoginDate = Some(s"27 February 2022 ${messages("common.dateToTime")} 12:00PM"))
        )(requestWithGivenMongoData(requestWithAccountType(randomAccountType)), messagesApi)

        when(mockAuditHandler.audit(ameq(auditEvent))(any[HeaderCarrier])).thenReturn(Future.successful((): Unit))

        val result = controller.view
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(result) shouldBe OK
        contentAsString(result) shouldBe view(
          ptEnrolmentModel.copy(
            currentAccountDetails = ptEnrolmentModel.currentAccountDetails.copy(lastLoginDate =
              Some(s"27 February 2022 ${messages("common.dateToTime")} 12:00PM")
            ),
            ptAccountDetails = ptEnrolmentModel.ptAccountDetails.copy(lastLoginDate =
              Some(s"27 February 2022 ${messages("common.dateToTime")} 12:00PM")
            )
          )
        )(
          fakeRequest,
          messages
        ).toString
        verify(mockAuditHandler, times(1)).audit(ameq(auditEvent))(any[HeaderCarrier])
      }
    }

    "the signed user has other accounts with SA enrolment and a different one with PT enrolment" should {
      "render the pt on another page with Access Self Assessment text" in {

        val ptEnrolmentModel = ptEnrolmentDataModel(Some("8764"))

        when(mockAuthConnector.authorise(ameq(predicates), ameq(retrievals))(any[HeaderCarrier], any[ExecutionContext]))
          .thenReturn(Future.successful(retrievalResponse(enrolments = saEnrolmentOnly)))

        when(mockMultipleAccountsOrchestrator.getCurrentAndPTAAndSAIfExistsForUser(any(), any(), any()))
          .thenReturn(createInboundResult(ptEnrolmentModel))

        val mockUserAnswers: UserAnswers = UserAnswers("id", generateNino.nino)
          .setOrException(AccountTypePage, randomAccountType.toString)
          .setOrException(RedirectUrlPage, "foo")
        mockGetDataFromCacheForActionSuccess(mockUserAnswers)

        val auditEvent = AuditEvent.auditPTEnrolmentOnOtherAccount(
          accountDetailsWithPT.copy(lastLoginDate = Some(s"27 February 2022 ${messages("common.dateToTime")} 12:00PM"))
        )(requestWithGivenMongoData(requestWithAccountType(randomAccountType)), messagesApi)

        when(mockAuditHandler.audit(ameq(auditEvent))(any[HeaderCarrier])).thenReturn(Future.successful((): Unit))

        val result = controller.view
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(result) shouldBe OK
        contentAsString(result) shouldBe view(
          ptEnrolmentModel.copy(
            currentAccountDetails = ptEnrolmentModel.currentAccountDetails.copy(lastLoginDate =
              Some(s"27 February 2022 ${messages("common.dateToTime")} 12:00PM")
            ),
            ptAccountDetails = ptEnrolmentModel.ptAccountDetails.copy(lastLoginDate =
              Some(s"27 February 2022 ${messages("common.dateToTime")} 12:00PM")
            )
          )
        )(
          fakeRequest,
          messages
        ).toString
        verify(mockAuditHandler, times(1)).audit(ameq(auditEvent))(any[HeaderCarrier])
      }
    }

    s"the user does not have an account type of $PT_ASSIGNED_TO_OTHER_USER" should {
      s"redirect to ${UrlPaths.accountCheckPath}" in {
        when(mockAuthConnector.authorise(ameq(predicates), ameq(retrievals))(any[HeaderCarrier], any[ExecutionContext]))
          .thenReturn(Future.successful(retrievalResponse()))

        when(mockMultipleAccountsOrchestrator.getCurrentAndPTAAndSAIfExistsForUser(any(), any(), any()))
          .thenReturn(createInboundResultError(IncorrectUserType(UrlPaths.returnUrl, randomAccountType)))

        val mockUserAnswers: UserAnswers = UserAnswers("id", generateNino.nino)
          .setOrException(AccountTypePage, randomAccountType.toString)
          .setOrException(RedirectUrlPage, "foo")
        mockGetDataFromCacheForActionSuccess(mockUserAnswers)

        val result = controller.view
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(UrlPaths.accountCheckPath)
      }
    }

    "the current user has a no PT enrolment on other account but session says it is other account" should {
      "render the error page" in {

        when(mockAuthConnector.authorise(ameq(predicates), ameq(retrievals))(any[HeaderCarrier], any[ExecutionContext]))
          .thenReturn(Future.successful(retrievalResponse(enrolments = saEnrolmentOnly)))

        when(mockMultipleAccountsOrchestrator.getCurrentAndPTAAndSAIfExistsForUser(any(), any(), any()))
          .thenReturn(createInboundResultError(NoPTEnrolmentWhenOneExpected))

        val mockUserAnswers: UserAnswers = UserAnswers("id", generateNino.nino)
          .setOrException(AccountTypePage, randomAccountType.toString)
          .setOrException(RedirectUrlPage, "foo")
        mockGetDataFromCacheForActionSuccess(mockUserAnswers)

        val res = controller.view
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(res) shouldBe INTERNAL_SERVER_ERROR
        contentAsString(res) should include(messages("enrolmentError.heading"))
      }
    }
    "no redirect url in cache" should {
      "render the error page" in {
        when(mockAuthConnector.authorise(ameq(predicates), ameq(retrievals))(any[HeaderCarrier], any[ExecutionContext]))
          .thenReturn(Future.successful(retrievalResponse()))

        mockGetDataFromCacheForActionNoRedirectUrl

        val res = controller.view
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(res) shouldBe INTERNAL_SERVER_ERROR
        contentAsString(res) should include(messages("enrolmentError.heading"))
      }
    }
  }
}
