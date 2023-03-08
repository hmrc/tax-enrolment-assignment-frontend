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

package helpers.messages

object PTEnrolmentOtherAccountMesages {

  val title =
    "We have found your personal tax account under a different Government Gateway user ID"
  val heading =
    "We have found your personal tax account under a different Government Gateway user ID"
  val text1 =
    s"To protect your information, access to your personal tax account was limited to the user ID below, ending with 6037."
  val signoutUrl = "http://test/signout"
  val notMyUserId = "The above user ID does not belong to me"
  val fraudReportingUrl =
    "/protect-tax-info/no-pt-enrolment/contact-hmrc-pta"
  val saHeading = "Access to Self Assessment"
  val saText =
    s"To access your Self Assessment you need to sign in again with user ID ending with 1243."
  val saText2 =
    "To access your Self Assessment sign in again with the above user ID."
  val saText3 =
    "The user ID you are currently signed in with can access Self Assessment."
  val saUrl = "/protect-tax-info/logout"

}
