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

import uk.gov.hmrc.play.bootstrap.binders.RedirectUrl
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.routes

object UrlPaths {

  val returnUrl: String                  = "/redirect/url"
  val accountCheckPath                   =
    routes.AccountCheckController.accountCheck(RedirectUrl.apply(returnUrl)).url
  val enrolledPTNoSAOnAnyAccountPath     = routes.EnrolledForPTController.view.url
  val enrolledPTWithSAAccountPath        = routes.EnrolledForPTWithSAController.view.url
  val enrolledPTSAOnOtherAccountPath     =
    routes.EnrolledPTWithSAOnOtherAccountController.view.url
  val reportFraudPTAccountPath           =
    routes.ReportSuspiciousIDController.viewNoSA.url
  val reportFraudSAAccountPath           =
    routes.ReportSuspiciousIDController.viewSA.url
  val saOnOtherAccountInterruptPath      =
    routes.SABlueInterruptController.view.url
  val saOnOtherAccountKeepAccessToSAPath =
    routes.KeepAccessToSAController.view.url
  val saOnOtherAccountSigninAgainPath    =
    routes.SignInWithSAAccountController.view.url
  val ptOnOtherAccountPath               =
    routes.PTEnrolmentOnOtherAccountController.view.url
  val logoutPath                         = routes.SignOutController.signOut.url

}
