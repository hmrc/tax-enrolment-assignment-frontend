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

package uk.gov.hmrc.taxenrolmentassignmentfrontend.messages

import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.TestData._

trait PTEnrolmentOtherAccountMessages extends BaseMessage {
  val title = "You cannot access your personal tax account" + defaultTitleSuffix
  val heading = ""
  val text1 = ""
  val signoutUrl = "http://test/signout"
  val notMyUserId = ""
  val fraudReportingUrl =
    "/protect-tax-info/no-pt-enrolment/contact-hmrc-pta"
  val signInGuidanceParagraph = "To access your personal tax account and Self Assessment:"
  val signInGuidanceList1 =
    "Sign out."
  val signInGuidanceList2 = ""
  val saHeading = "Self Assessment"
  val saText = ""
}

object PTEnrolmentOtherAccountMessagesBothGG extends PTEnrolmentOtherAccountMessages {

  override val heading =
    "You cannot access your personal tax account with this Government Gateway user ID"
  override val text1 =
    s"To protect your information, access to your personal tax account was limited to Government Gateway user ID:"
  override val notMyUserId = "This Government Gateway user ID is not mine"
  override val signInGuidanceList2 =
    s"Sign in with Government Gateway user ID ending with ${accountDetailsWithPT.userId}."
  override val saText = "You can use this Government Gateway user ID to access your Self Assessment."

}

object PTEnrolmentOtherAccountMessagesBothOL extends PTEnrolmentOtherAccountMessages {

  override val heading =
    "You cannot access your personal tax account with this GOV.UK One Login"
  override val text1 =
    s"To protect your information, access to your personal tax account and Self Assessment was limited to GOV.UK One Login:"
  override val notMyUserId = "This GOV.UK One Login is not mine"
  override val signInGuidanceList2 = s"Sign in with GOV.UK One Login email address ${accountDetailsWithPT.userId}."
  override val saText = "You can use this GOV.UK One Login to access your Self Assessment."
}

object PTEnrolmentOtherAccountMessagesEnrolmentGGLoggedInOL extends BaseMessage with PTEnrolmentOtherAccountMessages {

  override val heading =
    "You cannot access your personal tax account with these sign in details"
  override val text1 =
    s"To protect your information, access to your personal tax account and Self Assessment was limited to Government Gateway user ID:"
  override val notMyUserId = "This Government Gateway user ID is not mine"
  override val signInGuidanceList2 =
    s"Sign in with Government Gateway user ID ending with ${accountDetailsWithPT.userId}."
  override val saText = "You can use this GOV.UK One Login to access your Self Assessment."
}

object PTEnrolmentOtherAccountMessagesEnrolmentOLLoggedInGG extends BaseMessage with PTEnrolmentOtherAccountMessages {

  override val heading =
    "You cannot access your personal tax account with these sign in details"
  override val text1 =
    s"To protect your information, access to your personal tax account and Self Assessment was limited to GOV.UK One Login:"
  override val notMyUserId = "This GOV.UK One Login is not mine"
  override val signInGuidanceList2 =
    s"Sign in with GOV.UK One Login email address ${accountDetailsWithPT.emailObfuscated}."
  override val saText = "You can use this Government Gateway user ID to access your Self Assessment."
}
