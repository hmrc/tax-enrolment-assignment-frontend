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

package uk.gov.hmrc.taxenrolmentassignmentfrontend.services

import org.scalatest.concurrent.ScalaFutures
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.TestData.{UTR, userDetailsWithSAEnrolment}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.TestFixture
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.setupSAJourney.{SASetupJourneyRequest, SASetupJourneyResponse}

import scala.concurrent.ExecutionContext

class AddTaxesFrontendServiceSpec extends TestFixture with ScalaFutures {

  val service = new AddTaxesFrontendService(mockAddTaxesFrontendConnector)

  "saSetupJourney" should {
    "return result from connector" in {
      val resultFromConnector = createInboundResult(SASetupJourneyResponse("foo"))
      (mockAddTaxesFrontendConnector
        .saSetupJourney(_: SASetupJourneyRequest)(
          _: ExecutionContext,
          _: HeaderCarrier
        ))
        .expects(SASetupJourneyRequest("bta-sa", Some(UTR), userDetailsWithSAEnrolment.credId), *, *)
        .returning(resultFromConnector)
      service.saSetupJourney(userDetailsWithSAEnrolment) shouldBe resultFromConnector
    }
  }
}
