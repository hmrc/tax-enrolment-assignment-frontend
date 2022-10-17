/*
 * Copyright 2022 HM Revenue & Customs
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

import play.api.http.Status.{INTERNAL_SERVER_ERROR, SEE_OTHER}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve.{Credentials, Retrieval, ~}
import uk.gov.hmrc.auth.core.{AffinityGroup, Enrolments}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.actions.UserDetailsFromSession
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.UnexpectedError
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.TestData.{predicates, retrievalResponse, retrievals}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.TestFixture
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.setupSAJourney.SASetupJourneyResponse

import scala.concurrent.{ExecutionContext, Future}

class EnrolForSAControllerSpec extends TestFixture {

  val controller = new EnrolForSAController(
    mockAuthAction,
    mockAccountMongoDetailsAction,
    mockThrottleAction,
    mcc,
    mockMultipleAccountsOrchestrator,
    errorHandler
  )
  "enrolForSA" when {
    "orchestrator returns Success, redirect to URL provided" in {
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

      (mockMultipleAccountsOrchestrator
        .enrolForSA(
          _: UserDetailsFromSession)(
          _: HeaderCarrier,
          _: ExecutionContext
        ))
        .expects(*, *, *)
        .returning(createInboundResult(SASetupJourneyResponse("foobar")))

      val res = controller.enrolForSA(FakeRequest())

      status(res) shouldBe SEE_OTHER
      redirectLocation(res) shouldBe Some("foobar")
    }

    "orchestrator returns Error, return Error" in {
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

      (mockMultipleAccountsOrchestrator
        .enrolForSA(
          _: UserDetailsFromSession)(
          _: HeaderCarrier,
          _: ExecutionContext
        ))
        .expects(*, *, *)
        .returning(createInboundResultError(UnexpectedError))

      val res = controller.enrolForSA(FakeRequest())

      status(res) shouldBe INTERNAL_SERVER_ERROR
      contentAsString(res) should include("enrolmentError.heading")
    }
  }
}
