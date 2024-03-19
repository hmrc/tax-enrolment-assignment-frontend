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

package uk.gov.hmrc.taxenrolmentassignmentfrontend.testOnly.config

import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import javax.inject.{Inject, Singleton}

@Singleton
class AppConfigTestOnly @Inject() (val config: ServicesConfig) {
  val enrolmentStoreStub: String = config.baseUrl("enrolment-store-stub")
  val identityVerification: String = config.baseUrl("identity-verification")
  val basStubsBaseUrl: String = config.baseUrl("bas-stubs")
  val authLoginStub: String = config.baseUrl("auth-login-stub")
  val tensUrl: String = config.baseUrl("tax-enrolment-assignment-frontend")
}
