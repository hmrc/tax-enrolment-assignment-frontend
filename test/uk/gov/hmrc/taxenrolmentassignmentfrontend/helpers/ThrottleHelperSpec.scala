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

package uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers

import play.api.mvc.{Action, AnyContent}
import uk.gov.hmrc.auth.core.{AffinityGroup, Enrolments}
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve.{Credentials, Retrieval, ~}
import uk.gov.hmrc.http.HeaderCarrier
import play.api.test.Helpers._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.TestData.{NINO, buildFakeRequestWithSessionId, noEnrolments, predicates, randomAccountType, retrievalResponse, retrievals}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.services.ThrottleApplied

import scala.concurrent.{ExecutionContext, Future}

trait ThrottleHelperSpec {
_: TestFixture =>
  def specificThrottleTests(controllerFunction: Action[AnyContent]): Unit = {
    s"$ThrottleApplied" should {
      "redirect user to their RedirectURL" in {
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
            Future.successful(retrievalResponse()
          ))

        mockGetDataFromCacheForActionSuccess(accountType = randomAccountType, redirectUrl = "redirect")
        mockAccountShouldBeThrottled(randomAccountType, NINO, noEnrolments.enrolments)
        mockDeleteDataFromCache
        val res = controllerFunction.apply(buildFakeRequestWithSessionId("", ""))

        redirectLocation(res).get shouldBe "redirect"
        status(res) shouldBe SEE_OTHER
      }
    }
    s"Error from throttling service" should {
      s"return $INTERNAL_SERVER_ERROR" in {
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
            Future.successful(retrievalResponse()
            ))

        mockGetDataFromCacheForActionSuccess(accountType = randomAccountType, redirectUrl = "redirect")
        mockErrorFromThrottlingService(randomAccountType, NINO, noEnrolments.enrolments)

        val res = controllerFunction.apply(buildFakeRequestWithSessionId("", ""))

        status(res) shouldBe INTERNAL_SERVER_ERROR
      }
    }
  }
}
