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

package helpers

import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.routes

object ItUrlPaths {
  val enrolledPTNoSAOnAnyAccountPath: String     = routes.EnrolledForPTController.view.url
  val enrolledPTWithSAOnAnyAccountPath: String   = routes.EnrolledForPTWithSAController.view.url
  val enrolledPTSAOnOtherAccountPath: String     =
    routes.EnrolledPTWithSAOnOtherAccountController.view.url
  val reportFraudPTAccountPath: String           =
    routes.ReportSuspiciousIDController.viewNoSA.url
  val reportFraudSAAccountPath: String           =
    routes.ReportSuspiciousIDController.viewSA.url
  val saOnOtherAccountInterruptPath: String      =
    routes.SABlueInterruptController.view.url
  val saOnOtherAccountKeepAccessToSAPath: String =
    routes.KeepAccessToSAController.view.url
  val saOnOtherAccountSigninAgainPath: String    =
    routes.SignInWithSAAccountController.view.url
  val enrolForSAPath: String                     = routes.EnrolForSAController.enrolForSA.url
  val ptOnOtherAccountPath: String               =
    routes.PTEnrolmentOnOtherAccountController.view.url
  val logoutPath: String                         = routes.SignOutController.signOut.url
  val unauthorizedPath: String                   = routes.AuthorisationController.notAuthorised.url
  val keepAlive: String                          = routes.TimeOutController.keepAlive.url
  val timeout: String                            = routes.TimeOutController.timeout.url
  val signout: String                            = routes.SignOutController.signOut.url
}
