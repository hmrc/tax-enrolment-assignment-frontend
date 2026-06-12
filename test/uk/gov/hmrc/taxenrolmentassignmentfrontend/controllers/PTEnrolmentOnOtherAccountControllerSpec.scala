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

import org.mockito.ArgumentMatchers.{any, eq as ameq}
import org.mockito.Mockito.{times, verify, when}
import play.api.Application
import play.api.inject.{Binding, bind}
import play.api.mvc.BodyParsers
import play.api.test.Helpers.*
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.AccountTypes.PT_ASSIGNED_TO_OTHER_USER
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.*
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.TestData.*
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.{ControllersBaseSpec, UrlPaths}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.PTEnrolmentOnOtherAccount
import uk.gov.hmrc.taxenrolmentassignmentfrontend.orchestrators.{AccountCheckOrchestrator, MultipleAccountsOrchestrator}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.reporting.{AuditEvent, AuditHandler}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.TEASessionCache
import uk.gov.hmrc.taxenrolmentassignmentfrontend.services.SilentAssignmentService
import uk.gov.hmrc.taxenrolmentassignmentfrontend.views.html.{PTEnrolmentOnGGAccountLoggedInGG, PTEnrolmentOnGGAccountLoggedInOL, PTEnrolmentOnOLAccountLoggedInGG, PTEnrolmentOnOLAccountLoggedInOL}

import java.security.MessageDigest
import scala.concurrent.{ExecutionContext, Future}

class PTEnrolmentOnOtherAccountControllerSpec extends ControllersBaseSpec {

  lazy val mockSilentAssignmentService: SilentAssignmentService   = mock[SilentAssignmentService]
  lazy val mockAccountCheckOrchestrator: AccountCheckOrchestrator = mock[AccountCheckOrchestrator]
  lazy val mockAuditHandler: AuditHandler                         = mock[AuditHandler]

  lazy val testBodyParser: BodyParsers.Default                            = mock[BodyParsers.Default]
  lazy val mockMultipleAccountsOrchestrator: MultipleAccountsOrchestrator = mock[MultipleAccountsOrchestrator]

  override lazy val overrides: Seq[Binding[TEASessionCache]] = Seq(
    bind[TEASessionCache].toInstance(mockTeaSessionCache)
  )
  lazy val mockMessageDigest                                 = mock[MessageDigest]

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

  val viewMultipleGG: PTEnrolmentOnGGAccountLoggedInGG =
    app.injector.instanceOf[PTEnrolmentOnGGAccountLoggedInGG]

  val viewMultipleOL: PTEnrolmentOnOLAccountLoggedInOL =
    app.injector.instanceOf[PTEnrolmentOnOLAccountLoggedInOL]

  val viewLoggedInOLPTOnGG: PTEnrolmentOnGGAccountLoggedInOL =
    app.injector.instanceOf[PTEnrolmentOnGGAccountLoggedInOL]

  val viewLoggedInGGPTOnOL: PTEnrolmentOnOLAccountLoggedInGG =
    app.injector.instanceOf[PTEnrolmentOnOLAccountLoggedInGG]
  def identifier(input: String): String                      = {
    val digest    = MessageDigest.getInstance("SHA-256")
    val hashBytes = digest.digest(input.getBytes("UTF-8"))
    hashBytes.map("%02x".format(_)).mkString.take(32)
  }

  "view" when {
    "the user with no SA has another account with PT enrolment" should {
      "render the pt on another page with no Access SA text" in {
        val ptEnrolmentDataModelNone = ptEnrolmentDataModel(None)

        when(mockAuthConnector.authorise(ameq(predicates), ameq(retrievals))(any[HeaderCarrier], any[ExecutionContext]))
          .thenReturn(Future.successful(retrievalResponse()))

        when(mockMultipleAccountsOrchestrator.getCurrentAndPTAAndSAIfExistsForUser(any(), any(), any()))
          .thenReturn(createInboundResult[PTEnrolmentOnOtherAccount](ptEnrolmentDataModelNone))

        mockGetDataFromCacheForActionSuccess(randomAccountType)

        val auditEvent = AuditEvent.auditPTEnrolmentOnOtherAccount(
          accountDetails,
          accountDetailsWithPT.copy(lastLoginDate = Some(s"27 February 2022 ${messages("common.dateToTime")} 12:00PM"))
        )(requestWithAccountType(randomAccountType), messagesApi)

        when(mockAuditHandler.audit(ameq(auditEvent))(any[HeaderCarrier])).thenReturn(Future.successful((): Unit))

        val result = controller.view
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(result)           shouldBe OK
        contentAsString(result)    should include(
          "You cannot access your personal tax account with this Government Gateway user ID"
        )
        contentAsString(result) shouldNot include("access your Self Assessment")
        verify(mockAuditHandler, times(1)).audit(ameq(auditEvent))(any[HeaderCarrier])
      }
    }

    "the current user with SA has another account with PT enrolment" should {
      "render the pt on another page with Access SA text and GG messaging" in {

        val ptEnrolmentModel =
          ptEnrolmentDataModel(
            Some(CREDENTIAL_ID),
            currentAccountDetails =
              accountDetailsSA.copy(credId = CREDENTIAL_ID, lastLoginDate = Some("2022-02-27T12:00:27Z"))
          )

        when(mockAuthConnector.authorise(ameq(predicates), ameq(retrievals))(any[HeaderCarrier], any[ExecutionContext]))
          .thenReturn(Future.successful(retrievalResponse(enrolments = saEnrolmentOnly)))

        when(mockMultipleAccountsOrchestrator.getCurrentAndPTAAndSAIfExistsForUser(any(), any(), any()))
          .thenReturn(createInboundResult[PTEnrolmentOnOtherAccount](ptEnrolmentModel))

        mockGetDataFromCacheForActionSuccess(randomAccountType)

        val auditEvent = AuditEvent.auditPTEnrolmentOnOtherAccount(
          testAccountDetailsWithSA.copy(credId = CREDENTIAL_ID),
          accountDetailsWithPT.copy(lastLoginDate = Some(s"27 February 2022 ${messages("common.dateToTime")} 12:00PM"))
        )(requestWithAccountType(randomAccountType), messagesApi)

        when(mockAuditHandler.audit(ameq(auditEvent))(any[HeaderCarrier])).thenReturn(Future.successful((): Unit))

        val result = controller.view
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(result)        shouldBe OK
        contentAsString(result) should include(
          "You cannot access your personal tax account with this Government Gateway user ID"
        )
        contentAsString(result) should include("access your Self Assessment")
        verify(mockAuditHandler, times(1)).audit(ameq(auditEvent))(any[HeaderCarrier])
      }
      "render the pt on another page with Access SA text and OL messaging" in {

        val ptEnrolmentModel = ptEnrolmentDataModelOL(
          Some(CREDENTIAL_ID),
          currentAccountDetails =
            accountDetailsSAOL.copy(credId = CREDENTIAL_ID, lastLoginDate = Some("2022-02-27T12:00:27Z"))
        )

        when(mockAuthConnector.authorise(ameq(predicates), ameq(retrievals))(any[HeaderCarrier], any[ExecutionContext]))
          .thenReturn(Future.successful(retrievalResponse(enrolments = saEnrolmentOnly)))

        when(mockMultipleAccountsOrchestrator.getCurrentAndPTAAndSAIfExistsForUser(any(), any(), any()))
          .thenReturn(createInboundResult[PTEnrolmentOnOtherAccount](ptEnrolmentModel))

        mockGetDataFromCacheForActionSuccess(randomAccountType)

        val auditEvent = AuditEvent.auditPTEnrolmentOnOtherAccount(
          accountDetailsSAOL,
          accountDetailsWithPTOL.copy(lastLoginDate =
            Some(s"27 February 2022 ${messages("common.dateToTime")} 12:00PM")
          )
        )(requestWithAccountType(randomAccountType), messagesApi)

        when(mockAuditHandler.audit(ameq(auditEvent))(any[HeaderCarrier])).thenReturn(Future.successful((): Unit))

        val result = controller.view
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(result)        shouldBe OK
        contentAsString(result) should include(
          "You cannot access your personal tax account with this GOV.UK One Login"
        )
        contentAsString(result) should include("access your Self Assessment")
        verify(mockAuditHandler, times(1)).audit(ameq(auditEvent))(any[HeaderCarrier])
      }
      "render the pt on another page with Access SA text and mixed identity provider (logged in gg, PT on OL) messaging" in {

        when(mockAuthConnector.authorise(ameq(predicates), ameq(retrievals))(any[HeaderCarrier], any[ExecutionContext]))
          .thenReturn(Future.successful(retrievalResponse(enrolments = saEnrolmentOnly)))

        when(mockMultipleAccountsOrchestrator.getCurrentAndPTAAndSAIfExistsForUser(any(), any(), any()))
          .thenReturn(
            createInboundResult[PTEnrolmentOnOtherAccount](
              ptEnrolmentDataModelOL(
                Some(CREDENTIAL_ID),
                currentAccountDetails =
                  accountDetailsSA.copy(credId = CREDENTIAL_ID, lastLoginDate = Some("2022-02-27T12:00:27Z"))
              )
            )
          )

        mockGetDataFromCacheForActionSuccess(randomAccountType)

        val auditEvent = AuditEvent.auditPTEnrolmentOnOtherAccount(
          accountDetailsSA.copy(lastLoginDate = Some("27 February 2022 at 12:00PM")),
          accountDetailsWithPTOL.copy(lastLoginDate = Some("27 February 2022 at 12:00PM"))
        )(requestWithAccountType(randomAccountType), messagesApi)

        when(mockAuditHandler.audit(ameq(auditEvent))(any[HeaderCarrier])).thenReturn(Future.successful((): Unit))

        val result = controller.view
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(result)        shouldBe OK
        contentAsString(result) should include(
          "You cannot access your personal tax account with these sign in details"
        )
        contentAsString(result) should include("access your Self Assessment")
        verify(mockAuditHandler, times(1)).audit(ameq(auditEvent))(any[HeaderCarrier])
      }
      "render the pt on another page with Access SA text and mixed identity provider (logged in OL, PT on GG) messaging" in {

        val ptEnrolmentModel = ptEnrolmentDataModel(
          Some(CREDENTIAL_ID),
          currentAccountDetails =
            accountDetailsSAOL.copy(credId = CREDENTIAL_ID, lastLoginDate = Some("2022-02-27T12:00:27Z"))
        )

        when(mockAuthConnector.authorise(ameq(predicates), ameq(retrievals))(any[HeaderCarrier], any[ExecutionContext]))
          .thenReturn(Future.successful(retrievalResponse(enrolments = saEnrolmentOnly)))

        when(mockMultipleAccountsOrchestrator.getCurrentAndPTAAndSAIfExistsForUser(any(), any(), any()))
          .thenReturn(createInboundResult[PTEnrolmentOnOtherAccount](ptEnrolmentModel))

        mockGetDataFromCacheForActionSuccess(randomAccountType)

        val auditEvent = AuditEvent.auditPTEnrolmentOnOtherAccount(
          accountDetailsSAOL.copy(lastLoginDate = Some(s"27 February 2022 ${messages("common.dateToTime")} 12:00PM")),
          accountDetailsWithPT.copy(lastLoginDate = Some(s"27 February 2022 ${messages("common.dateToTime")} 12:00PM"))
        )(requestWithAccountType(randomAccountType), messagesApi)

        when(mockAuditHandler.audit(ameq(auditEvent))(any[HeaderCarrier])).thenReturn(Future.successful((): Unit))

        val result = controller.view
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(result)        shouldBe OK
        contentAsString(result) should include(
          "You cannot access your personal tax account with these sign in details"
        )
        contentAsString(result) should include("access your Self Assessment")
      }
      "render the pt on another page with Access SA and MTDIT text and One Login messaging" in {

        val ptEnrolmentModel = ptEnrolmentDataModelOL(
          Some(CREDENTIAL_ID),
          ptAccountDetails = accountDetailsWithPTOL,
          hasMtdit = true
        )

        when(mockAuthConnector.authorise(ameq(predicates), ameq(retrievals))(any[HeaderCarrier], any[ExecutionContext]))
          .thenReturn(Future.successful(retrievalResponse(enrolments = saAndmtditAndptEnrolments)))

        when(mockMultipleAccountsOrchestrator.getCurrentAndPTAAndSAIfExistsForUser(any(), any(), any()))
          .thenReturn(createInboundResult[PTEnrolmentOnOtherAccount](ptEnrolmentModel))

        mockGetDataFromCacheForActionSuccess(randomAccountType)

        val auditEvent = AuditEvent.auditPTEnrolmentOnOtherAccount(
          accountDetailsOL.copy(lastLoginDate = Some("27 February 2022 at 12:00PM")),
          accountDetailsWithPTOL.copy(lastLoginDate = Some("27 February 2022 at 12:00PM"))
        )(requestWithAccountType(randomAccountType), messagesApi)

        when(mockAuditHandler.audit(ameq(auditEvent))(any[HeaderCarrier])).thenReturn(Future.successful((): Unit))

        val result = controller.view
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(result)        shouldBe OK
        contentAsString(result) should include(
          "You cannot access your personal tax account with this GOV.UK One Login"
        )
        contentAsString(result) should include("Self Assessment")
        contentAsString(result) should include("Making Tax Digital for Income Tax")
        verify(mockAuditHandler, times(1)).audit(ameq(auditEvent))(any[HeaderCarrier])
      }
    }

    "the signed user has another account with SA enrolment which has both PT enrolment" should {
      "render the pt on another page with Access Self Assessment text" in {

        val ptEnrolmentModel = ptEnrolmentDataModel(Some(CREDENTIAL_ID_1))

        when(mockAuthConnector.authorise(ameq(predicates), ameq(retrievals))(any[HeaderCarrier], any[ExecutionContext]))
          .thenReturn(Future.successful(retrievalResponse(enrolments = saEnrolmentOnly)))

        when(mockMultipleAccountsOrchestrator.getCurrentAndPTAAndSAIfExistsForUser(any(), any(), any()))
          .thenReturn(createInboundResult[PTEnrolmentOnOtherAccount](ptEnrolmentModel))

        mockGetDataFromCacheForActionSuccess(randomAccountType)

        val auditEvent = AuditEvent.auditPTEnrolmentOnOtherAccount(
          accountDetails,
          accountDetailsWithPT.copy(lastLoginDate = Some(s"27 February 2022 ${messages("common.dateToTime")} 12:00PM"))
        )(requestWithAccountType(randomAccountType), messagesApi)

        when(mockAuditHandler.audit(ameq(auditEvent))(any[HeaderCarrier])).thenReturn(Future.successful((): Unit))

        val result = controller.view
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(result)        shouldBe OK
        contentAsString(result) should include(
          "You cannot access your personal tax account with this Government Gateway user ID"
        )
        contentAsString(result) should include(
          "To protect your information, access to your personal tax account and Self Assessment was limited to Government Gateway user ID:"
        )
        verify(mockAuditHandler, times(1)).audit(ameq(auditEvent))(any[HeaderCarrier])
      }
    }

    "the signed user has other accounts with SA enrolment and a different one with PT enrolment" should {
      "render the pt on another page with Access Self Assessment text" in {

        val ptEnrolmentModel = ptEnrolmentDataModel(Some("8764"))

        when(mockAuthConnector.authorise(ameq(predicates), ameq(retrievals))(any[HeaderCarrier], any[ExecutionContext]))
          .thenReturn(Future.successful(retrievalResponse(enrolments = saEnrolmentOnly)))

        when(mockMultipleAccountsOrchestrator.getCurrentAndPTAAndSAIfExistsForUser(any(), any(), any()))
          .thenReturn(createInboundResult[PTEnrolmentOnOtherAccount](ptEnrolmentModel))

        mockGetDataFromCacheForActionSuccess(randomAccountType)

        val auditEvent = AuditEvent.auditPTEnrolmentOnOtherAccount(
          accountDetails,
          accountDetailsWithPT.copy(lastLoginDate = Some(s"27 February 2022 ${messages("common.dateToTime")} 12:00PM"))
        )(requestWithAccountType(randomAccountType), messagesApi)

        when(mockAuditHandler.audit(ameq(auditEvent))(any[HeaderCarrier])).thenReturn(Future.successful((): Unit))

        val result = controller.view
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(result)        shouldBe OK
        contentAsString(result) should include(
          "You cannot access your personal tax account with this Government Gateway user ID"
        )
        verify(mockAuditHandler, times(1)).audit(ameq(auditEvent))(any[HeaderCarrier])
      }
    }

    s"the user does not have an account type of $PT_ASSIGNED_TO_OTHER_USER"                      should {
      s"redirect to ${UrlPaths.accountCheckPath}" in {
        when(mockAuthConnector.authorise(ameq(predicates), ameq(retrievals))(any[HeaderCarrier], any[ExecutionContext]))
          .thenReturn(Future.successful(retrievalResponse()))

        when(mockMultipleAccountsOrchestrator.getCurrentAndPTAAndSAIfExistsForUser(any(), any(), any()))
          .thenReturn(createInboundResultError(IncorrectUserType(UrlPaths.returnUrl, randomAccountType)))

        mockGetDataFromCacheForActionSuccess(randomAccountType)

        val result = controller.view
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(result)           shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(UrlPaths.accountCheckPath)
      }
    }

    "the current user has a no PT enrolment on other account but session says it is other account" should {
      "render the error page" in {

        when(mockAuthConnector.authorise(ameq(predicates), ameq(retrievals))(any[HeaderCarrier], any[ExecutionContext]))
          .thenReturn(Future.successful(retrievalResponse(enrolments = saEnrolmentOnly)))

        when(mockMultipleAccountsOrchestrator.getCurrentAndPTAAndSAIfExistsForUser(any(), any(), any()))
          .thenReturn(createInboundResultError(NoPTEnrolmentWhenOneExpected))

        mockGetDataFromCacheForActionSuccess(randomAccountType)

        val res = controller.view
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(res)        shouldBe INTERNAL_SERVER_ERROR
        contentAsString(res) should include(messages("enrolmentError.heading"))
      }
    }
    "no redirect url in cache"                                                                     should {
      "render the error page" in {
        when(mockAuthConnector.authorise(ameq(predicates), ameq(retrievals))(any[HeaderCarrier], any[ExecutionContext]))
          .thenReturn(Future.successful(retrievalResponse()))

        mockGetDataFromCacheForActionNoRedirectUrl

        val res = controller.view
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(res)        shouldBe INTERNAL_SERVER_ERROR
        contentAsString(res) should include(messages("enrolmentError.heading"))
      }
    }
  }
}
