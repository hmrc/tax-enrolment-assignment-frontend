/*
 * Copyright 2026 HM Revenue & Customs
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
import play.api.http.Status.{INTERNAL_SERVER_ERROR, OK}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.connectors.ScaWrapperDataConnector

class ScaWrapperDataConnectorISpec extends IntegrationSpecBase {

  lazy val connector: ScaWrapperDataConnector = app.injector.instanceOf[ScaWrapperDataConnector]

  def scaResponse(value: Boolean): String =
    s"""
      |{
      |   "useNewServiceNavigation": $value
      |}
      |""".stripMargin

  "serviceNavigationToggle" must {
    "return true" when {
      "sca-wrapper-data service returns valid true response" in {
        stubGet("/single-customer-account-wrapper-data/service-navigation/toggle", OK, scaResponse(true))

        val result = connector.serviceNavigationToggle().futureValue
        result shouldBe true
      }
    }
    "return false" when {
      "sca-wrapper-data service returns valid false response" in {
        stubGet("/single-customer-account-wrapper-data/service-navigation/toggle", OK, scaResponse(false))

        val result = connector.serviceNavigationToggle().futureValue
        result shouldBe false
      }
      "sca-wrapper-data service returns invalid response" in {
        stubGet("/single-customer-account-wrapper-data/service-navigation/toggle", OK, "random response")

        val result = connector.serviceNavigationToggle().futureValue
        result shouldBe false
      }
      "sca-wrapper-data service returns error response" in {
        stubGet("/single-customer-account-wrapper-data/service-navigation/toggle", INTERNAL_SERVER_ERROR, "random response")

        val result = connector.serviceNavigationToggle().futureValue
        result shouldBe false
      }
    }
  }

}
