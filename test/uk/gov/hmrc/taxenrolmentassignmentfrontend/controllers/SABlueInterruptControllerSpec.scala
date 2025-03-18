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
import play.api.inject.{Binding, bind}
import play.api.mvc.BodyParsers
import play.api.test.Helpers.{status, _}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes.SA_ASSIGNED_TO_OTHER_USER
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.{IncorrectUserType, UnexpectedPTEnrolment}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.TestData._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.{ControllersBaseSpec, UrlPaths}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.orchestrators.{AccountCheckOrchestrator, MultipleAccountsOrchestrator}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.reporting.AuditHandler
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.TEASessionCache
import uk.gov.hmrc.taxenrolmentassignmentfrontend.services.SilentAssignmentService
import uk.gov.hmrc.taxenrolmentassignmentfrontend.views.html.SABlueInterrupt

import scala.concurrent.{ExecutionContext, Future}

class SABlueInterruptControllerSpec extends ControllersBaseSpec {

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

  lazy val controller: SABlueInterruptController = app.injector.instanceOf[SABlueInterruptController]

  val blueSAView: SABlueInterrupt =
    inject[SABlueInterrupt]

  "view" when {
    "a user has SA on another account" should {
      "render the SABlueInterrupt page" when {
        "the user has not already been assigned a PT enrolment" in {

          when(
            mockAuthConnector.authorise(ameq(predicates), ameq(retrievals))(any[HeaderCarrier], any[ExecutionContext])
          )
            .thenReturn(Future.successful(retrievalResponse()))

          when(mockMultipleAccountsOrchestrator.checkAccessAllowedForPage(ameq(List(SA_ASSIGNED_TO_OTHER_USER)))(any()))
            .thenReturn(Right(SA_ASSIGNED_TO_OTHER_USER))

          mockGetDataFromCacheForActionSuccess(randomAccountType)

          val result = controller
            .view()
            .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

          status(result) shouldBe OK
          val page = Jsoup.parse(contentAsString(result))
          page
            .select("h1")
            .text() shouldBe messages("selfAssessmentInterrupt.gg.heading")

          page
            .select("p")
            .get(0)
            .text() shouldBe messages("selfAssessmentInterrupt.gg.paragraph1")

          page
            .select("p")
            .get(1)
            .text() shouldBe messages("selfAssessmentInterrupt.paragraph2")

          page
            .select("p")
            .get(2)
            .text() shouldBe messages("selfAssessmentInterrupt.paragraph3")
        }
      }

      s"redirect to ${UrlPaths.enrolledPTSAOnOtherAccountPath}" when {
        "the user has already been assigned a PT enrolment already" in {
          when(
            mockAuthConnector.authorise(ameq(predicates), ameq(retrievals))(any[HeaderCarrier], any[ExecutionContext])
          )
            .thenReturn(Future.successful(retrievalResponse(enrolments = ptEnrolmentOnly)))

          when(mockMultipleAccountsOrchestrator.checkAccessAllowedForPage(ameq(List(SA_ASSIGNED_TO_OTHER_USER)))(any()))
            .thenReturn(Left(UnexpectedPTEnrolment(SA_ASSIGNED_TO_OTHER_USER)))

          mockGetDataFromCacheForActionSuccess(randomAccountType)

          val result = controller
            .view()
            .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some(UrlPaths.enrolledPTSAOnOtherAccountPath)
        }
      }
    }
    s"the cache no redirectUrl" should {
      "render the error page" in {
        when(mockAuthConnector.authorise(ameq(predicates), ameq(retrievals))(any[HeaderCarrier], any[ExecutionContext]))
          .thenReturn(Future.successful(retrievalResponse()))

        mockGetDataFromCacheForActionNoRedirectUrl

        val result = controller
          .view()
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(result) shouldBe INTERNAL_SERVER_ERROR
        contentAsString(result) should include(messages("enrolmentError.heading"))
      }
    }

    s"the user does not have an account type of $SA_ASSIGNED_TO_OTHER_USER" should {
      s"redirect to ${UrlPaths.accountCheckPath}" in {
        when(mockAuthConnector.authorise(ameq(predicates), ameq(retrievals))(any[HeaderCarrier], any[ExecutionContext]))
          .thenReturn(Future.successful(retrievalResponse()))

        when(mockMultipleAccountsOrchestrator.checkAccessAllowedForPage(ameq(List(SA_ASSIGNED_TO_OTHER_USER)))(any()))
          .thenReturn(Left(IncorrectUserType(UrlPaths.returnUrl, randomAccountType)))

        mockGetDataFromCacheForActionSuccess(randomAccountType)

        val result = controller
          .view()
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(UrlPaths.accountCheckPath)
      }
    }
  }

  "continue" when {
    "a user has SA on another account" should {
      s"redirect to ${UrlPaths.saOnOtherAccountKeepAccessToSAPath}" in {
        when(mockAuthConnector.authorise(ameq(predicates), ameq(retrievals))(any[HeaderCarrier], any[ExecutionContext]))
          .thenReturn(Future.successful(retrievalResponse()))

        when(mockMultipleAccountsOrchestrator.checkAccessAllowedForPage(ameq(List(SA_ASSIGNED_TO_OTHER_USER)))(any()))
          .thenReturn(Right(SA_ASSIGNED_TO_OTHER_USER))

        mockGetDataFromCacheForActionSuccess(randomAccountType)

        val result = controller
          .continue()
          .apply(buildFakePOSTRequestWithSessionId(Map.empty))

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(
          UrlPaths.saOnOtherAccountKeepAccessToSAPath
        )
      }
    }
    s"the cache no redirectUrl" should {
      "render the error page" in {
        when(mockAuthConnector.authorise(ameq(predicates), ameq(retrievals))(any[HeaderCarrier], any[ExecutionContext]))
          .thenReturn(Future.successful(retrievalResponse()))
        mockGetDataFromCacheForActionNoRedirectUrl

        val result = controller
          .continue()
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(result) shouldBe INTERNAL_SERVER_ERROR
        contentAsString(result) should include(messages("enrolmentError.heading"))
      }
    }

    s"the user does not have an account type of $SA_ASSIGNED_TO_OTHER_USER" should {
      s"redirect to ${UrlPaths.accountCheckPath}" in {
        when(mockAuthConnector.authorise(ameq(predicates), ameq(retrievals))(any[HeaderCarrier], any[ExecutionContext]))
          .thenReturn(Future.successful(retrievalResponse()))

        when(mockMultipleAccountsOrchestrator.checkAccessAllowedForPage(ameq(List(SA_ASSIGNED_TO_OTHER_USER)))(any()))
          .thenReturn(Left(IncorrectUserType(UrlPaths.returnUrl, randomAccountType)))

        mockGetDataFromCacheForActionSuccess(randomAccountType)

        val result = controller
          .continue()
          .apply(buildFakePOSTRequestWithSessionId(Map.empty))

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(UrlPaths.accountCheckPath)
      }
    }
  }
}
