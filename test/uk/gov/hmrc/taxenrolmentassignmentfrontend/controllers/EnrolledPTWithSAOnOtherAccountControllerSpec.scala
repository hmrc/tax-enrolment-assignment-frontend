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

import org.jsoup.Jsoup
import org.mockito.ArgumentMatchers.{any, eq => ameq}
import org.mockito.MockitoSugar.{mock, when}
import play.api.Application
import play.api.http.Status.OK
import play.api.inject.{Binding, bind}
import play.api.mvc.BodyParsers
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.actions.RequestWithUserDetailsFromSessionAndMongo
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.{IncorrectUserType, UnexpectedResponseFromUsersGroupsSearch}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.TestData._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.{ControllersBaseSpec, UrlPaths}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.orchestrators.{AccountCheckOrchestrator, MultipleAccountsOrchestrator}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.reporting.AuditHandler
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.TEASessionCache
import uk.gov.hmrc.taxenrolmentassignmentfrontend.services.SilentAssignmentService
import uk.gov.hmrc.taxenrolmentassignmentfrontend.views.html.EnrolledForPTWithSAOnOtherAccount

import scala.concurrent.{ExecutionContext, Future}

class EnrolledPTWithSAOnOtherAccountControllerSpec extends ControllersBaseSpec {

  lazy val mockSilentAssignmentService: SilentAssignmentService = mock[SilentAssignmentService]
  lazy val mockAccountCheckOrchestrator: AccountCheckOrchestrator = mock[AccountCheckOrchestrator]
  lazy val mockAuditHandler: AuditHandler = mock[AuditHandler]

  lazy val testBodyParser: BodyParsers.Default = mock[BodyParsers.Default]
  lazy val mockMultipleAccountsOrchestrator: MultipleAccountsOrchestrator = mock[MultipleAccountsOrchestrator]

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
      bind[MultipleAccountsOrchestrator].toInstance(mockMultipleAccountsOrchestrator)
    )
    .build()

  lazy val controller: EnrolledPTWithSAOnOtherAccountController =
    app.injector.instanceOf[EnrolledPTWithSAOnOtherAccountController]

  val view: EnrolledForPTWithSAOnOtherAccount =
    app.injector.instanceOf[EnrolledForPTWithSAOnOtherAccount]

  "view" when {

    "the user has enrolled for PT after choosing to have SA separate" should {
      "render the EnrolledForPTWithSAOnOtherAccount page with SA details" in {

        when(mockAuthConnector.authorise(ameq(predicates), ameq(retrievals))(any[HeaderCarrier], any[ExecutionContext]))
          .thenReturn(Future.successful(retrievalResponse(enrolments = saEnrolmentOnly)))

        when(
          mockMultipleAccountsOrchestrator.getDetailsForEnrolledPTWithSAOnOtherAccount(
            any[RequestWithUserDetailsFromSessionAndMongo[_]],
            any[HeaderCarrier],
            any[ExecutionContext]
          )
        )
          .thenReturn(
            createInboundResult(accountDetails)
          )

        when(
          mockMultipleAccountsOrchestrator.getSACredentialIfNotFraud(
            any[RequestWithUserDetailsFromSessionAndMongo[_]],
            any[HeaderCarrier],
            any[ExecutionContext]
          )
        )
          .thenReturn(
            createInboundResult(
              Some(accountDetails.copy(userId = "********1234"))
            )
          )

        mockGetDataFromCacheForActionSuccess(randomAccountType)
        val result = controller.view
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(result) shouldBe OK
        val content = Jsoup
          .parse(contentAsString(result))

        content.body().text() should include(messages("choseSeparate.gg.heading"))
        content.body().text() should include(
          messages(
            "choseSeparate.paragraph"
          )
        )
      }
    }

    "the user has enrolled for PT after reporting fraud" should {
      "return INTERNAL_SERVER_ERROR the EnrolledForPTWithSAOnOtherAccount page without SA" in { // Check this as new status is different
        when(mockAuthConnector.authorise(ameq(predicates), ameq(retrievals))(any[HeaderCarrier], any[ExecutionContext]))
          .thenReturn(Future.successful(retrievalResponse(enrolments = saEnrolmentOnly)))

        when(mockMultipleAccountsOrchestrator.getDetailsForEnrolledPTWithSAOnOtherAccount(any(), any(), any()))
          .thenReturn(createInboundResult(accountDetails))

        when(mockMultipleAccountsOrchestrator.getSACredentialIfNotFraud(any(), any(), any()))
          .thenReturn(createInboundResult(None))

        mockGetDataFromCacheForActionSuccess(randomAccountType)

        val result = controller.view
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(result) shouldBe INTERNAL_SERVER_ERROR
      }
    }

    "the user is the wrong usertype" should {
      s"redirect to the ${UrlPaths.accountCheckPath} page" in {
        when(mockAuthConnector.authorise(ameq(predicates), ameq(retrievals))(any[HeaderCarrier], any[ExecutionContext]))
          .thenReturn(Future.successful(retrievalResponse()))

        when(mockMultipleAccountsOrchestrator.getDetailsForEnrolledPTWithSAOnOtherAccount(any(), any(), any()))
          .thenReturn(
            createInboundResultError(
              IncorrectUserType(UrlPaths.returnUrl, randomAccountType)
            )
          )

        mockGetDataFromCacheForActionSuccess(randomAccountType)
        val res = controller.view
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(res) shouldBe SEE_OTHER
        redirectLocation(res) shouldBe Some(UrlPaths.accountCheckPath)
      }
    }
    "no redirectUrl stored in session" should {
      "render the error view" in {
        when(mockAuthConnector.authorise(ameq(predicates), ameq(retrievals))(any[HeaderCarrier], any[ExecutionContext]))
          .thenReturn(Future.successful(retrievalResponse()))

        mockGetDataFromCacheForActionNoRedirectUrl

        val res = controller
          .view()
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(res) shouldBe INTERNAL_SERVER_ERROR
        contentAsString(res) should include(messages("enrolmentError.heading"))
      }
    }

    "the call to users-group-search fails" should {
      "render error view" in {
        when(mockAuthConnector.authorise(ameq(predicates), ameq(retrievals))(any[HeaderCarrier], any[ExecutionContext]))
          .thenReturn(Future.successful(retrievalResponse()))

        when(mockMultipleAccountsOrchestrator.getDetailsForEnrolledPTWithSAOnOtherAccount(any(), any(), any()))
          .thenReturn(createInboundResultError(UnexpectedResponseFromUsersGroupsSearch))

        mockGetDataFromCacheForActionSuccess(randomAccountType)
        val res = controller.view
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(res) shouldBe INTERNAL_SERVER_ERROR
        contentAsString(res) should include(messages("enrolmentError.heading"))
      }
    }
  }
  "continue" when {
    "the call to continue deletes user data and redirects to their redirectURL" in {
      when(mockAuthConnector.authorise(ameq(predicates), ameq(retrievals))(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future.successful(retrievalResponse()))

      mockGetDataFromCacheForActionSuccess(accountType = randomAccountType, redirectUrl = "redirect")
      mockDeleteDataFromCacheWhen
      val res = controller.continue
        .apply(buildFakeRequestWithSessionId(""))

      status(res) shouldBe SEE_OTHER
      redirectLocation(res).get shouldBe "redirect"
      mockDeleteDataFromCacheVerify
    }
  }
}
