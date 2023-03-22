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

import play.api.Application
import play.api.http.Status.{INTERNAL_SERVER_ERROR, SEE_OTHER}
import play.api.inject.bind
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve.{Credentials, Retrieval, ~}
import uk.gov.hmrc.auth.core.{AffinityGroup, AuthConnector, Enrolments}
import uk.gov.hmrc.http.HeaderCarrier
import play.api.mvc.{AnyContent, BodyParsers}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.config.AppConfig
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.actions.{AuthAction, RequestWithUserDetailsFromSession}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.TestData.{buildFakeRequestWithSessionId, predicates, retrievalResponse, retrievals, saEnrolmentOnly}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.{BaseSpec, TestFixture}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.orchestrators.AccountCheckOrchestrator
import uk.gov.hmrc.taxenrolmentassignmentfrontend.reporting.AuditHandler
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.TEASessionCache
import uk.gov.hmrc.taxenrolmentassignmentfrontend.services.{SilentAssignmentService, ThrottlingService}

import scala.concurrent.{ExecutionContext, Future}

class EnrolForSAControllerSpec extends BaseSpec {

  lazy val appConfig = app.injector.instanceOf[AppConfig]

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

  lazy val controller = app.injector.instanceOf[EnrolForSAController]

  "navigate to bta" when {
    "users has SA enrolment and PT assigned to other cred that they logged in with and wants to access sa from ten's kick out page " in {
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
        .returning(
          Future.successful(retrievalResponse(enrolments = saEnrolmentOnly))
        )

      (mockTeaSessionCache
        .removeRecord(_: RequestWithUserDetailsFromSession[AnyContent]))
        .expects(*)
        .returning(Future.successful(true))

      val res = controller.enrolForSA.apply(buildFakeRequestWithSessionId("GET", ""))

      status(res) shouldBe SEE_OTHER
      redirectLocation(res) shouldBe Some(appConfig.btaUrl)
    }

  }

  "throw error message" when {
    "users has does not have SA enrolment and PT assigned to other cred that they logged in with and wants to access sa from ten's kick out page " in {
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
        .returning(
          Future.successful(retrievalResponse())
        )

      val res = controller.enrolForSA.apply(buildFakeRequestWithSessionId("GET", ""))

      status(res) shouldBe INTERNAL_SERVER_ERROR
    }

  }

}
