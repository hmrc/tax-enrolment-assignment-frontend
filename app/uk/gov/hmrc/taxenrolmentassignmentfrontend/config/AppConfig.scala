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

package uk.gov.hmrc.taxenrolmentassignmentfrontend.config

import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import javax.inject.{Inject, Singleton}

@Singleton
class AppConfig @Inject()(val config: ServicesConfig) {
  lazy val welshLanguageSupportEnabled: Boolean = config
    .getConfBool("features.welsh-language-support", defBool = true)

  lazy val validRedirectHostNames: Set[String] = config.getConfString("validRedirectHostNames", "").split(",").toSet

  lazy val IV_BASE_URL
    : String = config.baseUrl("identity-verification") + "/identity-verification"
  lazy val EACD_BASE_URL
    : String = config.baseUrl("enrolment-store-proxy") + "/enrolment-store-proxy"
  lazy val TAX_ENROLMENTS_BASE_URL
    : String = {
    if (config.getConfBool("tax-enrolments.isTest", defBool = false)) {
      s"$tenBaseUrl/tax-enrolments/test-only"
    } else {
      config.baseUrl("tax-enrolments") + "/tax-enrolments"
    }
  }
  lazy val AUTH_BASE_URL: String = {
    if (config.getConfBool("auth.isTest", defBool = false)) {
      s"$tenBaseUrl/auth/test-only"
    } else {
      config.baseUrl("auth") + "/auth"
    }
  }


  lazy val btaUrl : String =
    config.getString("microservice.services.business-tax-account.url")

  lazy val useTestOnlyUsersGroupSearch: Boolean = {
    config.getConfBool("users-groups-search.isTest", defBool = false)
  }
  lazy val tenBaseUrl: String =
    s"${config.baseUrl("tax-enrolment-assignment-frontend")}"
  lazy val usersGroupsSearchBaseURL: String =
    s"${config.baseUrl("users-groups-search")}/users-groups-search"
  lazy val basAuthHost: String =
    s"${config.getConfString("bas-gateway-frontend.host", "")}"
  lazy val loginCallback: String =
    config.getConfString("bas-gateway-frontend.continue-callback.url", "")
  lazy val loginURL: String = s"$basAuthHost/bas-gateway/sign-in"
  lazy val signOutUrl: String =
    s"$basAuthHost/bas-gateway/sign-out-without-state"
  lazy val keepAliveUrl: String =
    s"/protect-tax-info/keepAlive"

  lazy val appName = config.getString("appName")

  lazy val percentageOfUsersThrottledToGetFakeEnrolment: Int =
    config.getInt("throttle.percentage") - 1

  val ehCacheTtlInSeconds: Int = config.getConfInt("ehCache.ttlInSeconds", 600)

}
