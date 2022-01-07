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

package uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.controllers

import play.api.test.Helpers.{status, _}
import uk.gov.hmrc.auth.core.Enrolments
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve.{Credentials, Retrieval, ~}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.helpers.TestData._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.helpers.TestFixture
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.{AccountCheckController, testOnly}

import scala.concurrent.{ExecutionContext, Future}

class AccountCheckControllerSpec extends TestFixture {

  val controller = new AccountCheckController(mockAuthAction, mcc)

  "accountCheck" should {
    "return OK" in {
      (mockAuthConnector.authorise(_: Predicate, _: Retrieval[(Option[String] ~ Option[Credentials]) ~ Enrolments])(_: HeaderCarrier, _: ExecutionContext))
        .expects(predicates, retrievals, *, *).returning(Future.successful(retrievalResponse()))

      val result = controller.accountCheck(testOnly.routes.TestOnlyController.successfulCall.url)
        .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/tax-enrolment-assignment-frontend/test-only/successful")
    }
  }

}
