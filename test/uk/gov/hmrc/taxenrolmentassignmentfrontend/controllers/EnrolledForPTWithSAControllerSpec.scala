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
import org.mockito.MockitoSugar.{mock, when}
import play.api.Application
import play.api.http.Status.OK
import play.api.inject.{Binding, bind}
import play.api.mvc.BodyParsers
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes.SA_ASSIGNED_TO_CURRENT_USER
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.TestData._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.{ControllersBaseSpec, UrlPaths}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.orchestrators.{AccountCheckOrchestrator, MultipleAccountsOrchestrator}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.reporting.AuditHandler
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.TEASessionCache
import uk.gov.hmrc.taxenrolmentassignmentfrontend.services.SilentAssignmentService
import uk.gov.hmrc.taxenrolmentassignmentfrontend.views.html.EnrolledForPTPage

import scala.concurrent.{ExecutionContext, Future}

class EnrolledForPTWithSAControllerSpec extends ControllersBaseSpec {

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

  lazy val controller: EnrolledForPTWithSAController = app.injector.instanceOf[EnrolledForPTWithSAController]

  val view: EnrolledForPTPage =
    app.injector.instanceOf[EnrolledForPTPage]

  "view" when {
    "the user has multiple accounts, is signed in with one with SA then" should {
      "see the Enrolled to PT with SA page" in {

        when(mockAuthConnector.authorise(ameq(predicates), ameq(retrievals))(any[HeaderCarrier], any[ExecutionContext]))
          .thenReturn(Future.successful(retrievalResponse(enrolments = saEnrolmentOnly)))

        when(mockMultipleAccountsOrchestrator.getDetailsForEnrolledPT(any(), any(), any()))
          .thenReturn(createInboundResult(accountDetails))

        mockGetDataFromCacheForActionSuccess(randomAccountType)

        val result = controller.view
          .apply(buildFakeRequestWithSessionId(""))

        status(result) shouldBe OK

        contentAsString(result).contains("enrolledForPT.title")

        contentAsString(result).contains("enrolledForPT.paragraphSA")

      }
    }
  }

  "continue" when {
    "the user has multiple accounts, is signed in with one with SA then" should {
      s"redirect to ${UrlPaths.returnUrl}" in {

        when(mockAuthConnector.authorise(ameq(predicates), ameq(retrievals))(any[HeaderCarrier], any[ExecutionContext]))
          .thenReturn(Future.successful(retrievalResponse(enrolments = saEnrolmentOnly)))
        mockDeleteDataFromCacheWhen
        mockGetDataFromCacheForActionSuccess(SA_ASSIGNED_TO_CURRENT_USER, UrlPaths.returnUrl)

        val result = controller
          .continue()
          .apply(buildFakeRequestWithSessionId(""))

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(
          UrlPaths.returnUrl
        )
        mockDeleteDataFromCacheVerify
      }
    }
  }

}
