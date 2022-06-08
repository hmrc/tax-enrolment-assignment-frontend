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

import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.service.TEAFResult
import uk.gov.hmrc.taxenrolmentassignmentfrontend.connectors.AddTaxesFrontendConnector
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.actions.UserDetailsFromSession
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.setupSAJourney.{SASetupJourneyRequest, SASetupJourneyResponse}

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class AddTaxesFrontendService @Inject()(addTaxesFrontendConnector: AddTaxesFrontendConnector) {

  def saSetupJourney(userDetailsFromSession: UserDetailsFromSession)(implicit hc: HeaderCarrier, ec: ExecutionContext): TEAFResult[SASetupJourneyResponse] = {
    addTaxesFrontendConnector.saSetupJourney(SASetupJourneyRequest(
      "bta-sa",
      userDetailsFromSession.utr,
      userDetailsFromSession.credId
    )
    )
  }
}
