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

import play.api.http.Status.{INTERNAL_SERVER_ERROR, SEE_OTHER}
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve.{Credentials, Retrieval, ~}
import uk.gov.hmrc.auth.core.{AffinityGroup, Enrolments}
import uk.gov.hmrc.http.HeaderCarrier
import play.api.mvc.{AnyContent}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.actions.{RequestWithUserDetailsFromSession, UserDetailsFromSession}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.TestData.{buildFakeRequestWithSessionId, predicates, retrievalResponse, retrievals, saEnrolmentOnly}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.{TestFixture}

import scala.concurrent.{ExecutionContext, Future}

class EnrolForSAControllerSpec extends TestFixture {

  val controller = new EnrolForSAController(
    mockAuthAction,
    mockAccountMongoDetailsAction,
    mockThrottleAction,
    mcc,
    appConfig,
    errorHandler,
    mockTeaSessionCache
  )
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
