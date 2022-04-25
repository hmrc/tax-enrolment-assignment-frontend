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

package helpers.messages

object PTEnrolmentOtherAccountMesages {

  val title =
    "We have found your personal tax account under a different Government Gateway user ID"
  val heading =
    "We have found your personal tax account under a different Government Gateway user ID"
  val text1 =
    "To access your personal tax information you need to sign in again with the following user ID:"
  val signoutUrl = "http://test/signout"
  val notMyUserId = "This user ID does not belong to me"
  //TODO remove fraud reporting url after consultation with leads, as it requires other changes such remove couple of tests etc etc
  val fraudReportingUrl =
    "/protect-tax-info/no-pt-enrolment/contact-hmrc-pta"
  val saHeading = "Access to Self Assessment"
  val saText = "Your current user ID can access Self Assessment."
  val saUrl = "http://test/self-assessment"

}
