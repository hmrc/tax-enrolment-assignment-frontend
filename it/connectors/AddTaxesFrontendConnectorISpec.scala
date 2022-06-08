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

package connectors

import helpers.IntegrationSpecBase
import helpers.WiremockHelper.stubPost
import play.api.http.Status
import play.api.http.Status.{BAD_REQUEST, OK}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.connectors.AddTaxesFrontendConnector
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.{ResponseBodyInvalidFromAddTaxesFrontendSASetup, UnexpectedResponseFromAddTaxesFrontendSASetup}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.setupSAJourney.SASetupJourneyRequest

class AddTaxesFrontendConnectorISpec extends IntegrationSpecBase {

  lazy val connector = app.injector.instanceOf[AddTaxesFrontendConnector]

  "saSetupJourney" should {
    s"return response when add taxes returns $OK with valid body" in {
      stubPost("/write/audit/merged", Status.NO_CONTENT, "")
      stubPost("/internal/self-assessment/enrol-for-sa", OK, """{"redirectUrl" : "foo"}""")
      await(connector.saSetupJourney(SASetupJourneyRequest("", None, "")).value).right.get.redirectUrl shouldBe "foo"
    }
    s"return Left when add taxes returns $OK but invalid body" in {
      stubPost("/write/audit/merged", Status.NO_CONTENT, "")
      stubPost("/internal/self-assessment/enrol-for-sa", OK, """{"redirect" : "foo"}""")
      await(connector.saSetupJourney(SASetupJourneyRequest("", None, "")).value).left.get shouldBe ResponseBodyInvalidFromAddTaxesFrontendSASetup
    }
    s"return Left when add taxes returns non success status" in {
      stubPost("/write/audit/merged", Status.NO_CONTENT, "")
      stubPost("/internal/self-assessment/enrol-for-sa", BAD_REQUEST, "")
      await(connector.saSetupJourney(SASetupJourneyRequest("", None, "")).value).left.get shouldBe UnexpectedResponseFromAddTaxesFrontendSASetup
    }
  }
}
