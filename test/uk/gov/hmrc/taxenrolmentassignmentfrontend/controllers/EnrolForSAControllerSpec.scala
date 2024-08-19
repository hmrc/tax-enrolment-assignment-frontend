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
import play.api.http.Status.{INTERNAL_SERVER_ERROR, SEE_OTHER}
import play.api.inject.{Binding, bind}
import play.api.mvc.BodyParsers
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.taxenrolmentassignmentfrontend.config.AppConfig
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.BaseSpec
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.TestData._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.orchestrators.AccountCheckOrchestrator
import uk.gov.hmrc.taxenrolmentassignmentfrontend.reporting.AuditHandler
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.TEASessionCache
import uk.gov.hmrc.taxenrolmentassignmentfrontend.services.SilentAssignmentService

import scala.concurrent.{ExecutionContext, Future}

class EnrolForSAControllerSpec extends BaseSpec {

  lazy val appConfig: AppConfig = app.injector.instanceOf[AppConfig]

  lazy val mockSilentAssignmentService: SilentAssignmentService = mock[SilentAssignmentService]
  lazy val mockAccountCheckOrchestrator: AccountCheckOrchestrator = mock[AccountCheckOrchestrator]
  lazy val mockAuditHandler: AuditHandler = mock[AuditHandler]

  lazy val mockAuthConnector: AuthConnector = mock[AuthConnector]
  lazy val testBodyParser: BodyParsers.Default = mock[BodyParsers.Default]
  lazy val mockTeaSessionCache: TEASessionCache = mock[TEASessionCache]

  override lazy val overrides: Seq[Binding[TEASessionCache]] = Seq(
    bind[TEASessionCache].toInstance(mockTeaSessionCache)
  )

  override implicit lazy val app: Application = localGuiceApplicationBuilder()
    .overrides(
      bind[SilentAssignmentService].toInstance(mockSilentAssignmentService),
      bind[AccountCheckOrchestrator].toInstance(mockAccountCheckOrchestrator),
      bind[AuditHandler].toInstance(mockAuditHandler),
      bind[AuthConnector].toInstance(mockAuthConnector),
      bind[BodyParsers.Default].toInstance(testBodyParser)
    )
    .build()

  lazy val controller: EnrolForSAController = app.injector.instanceOf[EnrolForSAController]

  "navigate to bta" when {
    "users has SA enrolment and PT assigned to other cred that they logged in with and wants to access sa from ten's kick out page " in {
      when(mockAuthConnector.authorise(ameq(predicates), ameq(retrievals))(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future.successful(retrievalResponse(enrolments = saEnrolmentOnly)))

      when(mockTeaSessionCache.removeRecord(any())).thenReturn(Future.successful(true))

      val res = controller.enrolForSA.apply(buildFakeRequestWithSessionId("GET"))

      status(res) shouldBe SEE_OTHER
      redirectLocation(res) shouldBe Some(appConfig.btaUrl)
    }

  }

  "throw error message" when {
    "users has does not have SA enrolment and PT assigned to other cred that they logged in with and wants to access sa from ten's kick out page " in {
      when(mockAuthConnector.authorise(ameq(predicates), ameq(retrievals))(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future.successful(retrievalResponse()))

      val res = controller.enrolForSA.apply(buildFakeRequestWithSessionId("GET"))

      status(res) shouldBe INTERNAL_SERVER_ERROR
    }

  }

}
