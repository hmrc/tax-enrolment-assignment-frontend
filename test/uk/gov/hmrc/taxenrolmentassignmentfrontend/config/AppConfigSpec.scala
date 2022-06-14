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

package uk.gov.hmrc.taxenrolmentassignmentfrontend.config

import play.api.Configuration
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.taxenrolmentassignmentfrontend.config.AppConfig
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.TestFixture

class AppConfigSpec extends TestFixture {

class TestSetup(addTaxesFrontendTestEnabled: Boolean) {
  val appConfigWithFakeServicesConfig = new AppConfig(new ServicesConfig(Configuration.from(Map(
    "microservice.services.add-taxes-frontend.isTest" -> addTaxesFrontendTestEnabled.toString,
    "microservice.services.add-taxes-frontend.host" -> "foo",
    "microservice.services.add-taxes-frontend.port" -> "123",
    "microservice.services.tax-enrolment-assignment-frontend.host" -> "bar",
    "microservice.services.tax-enrolment-assignment-frontend.port" -> "456"
  ))))
}
  "ADD_TAXES_FRONTEND_SA_INIT_URL" should {
    "return test onl reverse route when feature switch enabled" in new TestSetup(true) {
      appConfigWithFakeServicesConfig.ADD_TAXES_FRONTEND_SA_INIT_URL shouldBe "http://bar:456/add-taxes-frontend/test-only/self-assessment/enrol-for-sa"

    }
    "return route of add taxes frontend when feature switch disabled" in new TestSetup(false) {
      appConfigWithFakeServicesConfig.ADD_TAXES_FRONTEND_SA_INIT_URL shouldBe "http://foo:123/internal/self-assessment/enrol-for-sa"
    }
  }
}
