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

import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import javax.inject.{Inject, Singleton}

@Singleton
class AppConfig @Inject()(val servicesConfig: ServicesConfig) {
  lazy val welshLanguageSupportEnabled: Boolean = servicesConfig
    .getConfBool("features.welsh-language-support", defBool = false)
  lazy val IV_BASE_URL
    : String = servicesConfig.baseUrl("identity-verification") + "/identity-verification"
  lazy val EACD_BASE_URL: String = servicesConfig.baseUrl(
    "enrolment-store-proxy"
  ) + "/enrolment-store-proxy"
  lazy val TAX_ENROLMENTS_BASE_URL: String = servicesConfig.baseUrl(
    "tax-enrolments"
  ) + "/tax-enrolments"
  lazy val basAuthHost: String =
    s"${servicesConfig.getConfString("bas-gateway.host", "")}"
  lazy val loginCallback: String =
    servicesConfig.getConfString("bas-gateway.continue-callback.url", "")
  lazy val loginURL: String = s"$basAuthHost/bas-gateway/sign-in"
  lazy val useTestOnlyUsersGroupSearch: Boolean =
    servicesConfig.getConfBool("users-group-search.isTest", false)
  lazy val usersGroupSearchBaseURL: String =
    s"${servicesConfig.baseUrl("users-group-search")}/users-group-search"
  lazy val signOutUrl: String =
    s"$basAuthHost/bas-gateway/sign-out-without-state"
}
