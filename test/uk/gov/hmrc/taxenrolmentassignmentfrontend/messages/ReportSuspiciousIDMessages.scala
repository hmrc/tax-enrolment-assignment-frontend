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

object ReportSuspiciousIDMessages extends BaseMessage {

  val title: String          = "You need to contact us" + defaultTitleSuffix
  val heading                = "You need to contact us"
  val paragraphGG: String    =
    "Take a note of this Government Gateway user ID as this screen will time out after 15 minutes."
  val paragraphOL: String    = "Take a note of this GOV.UK One Login as this screen will time out after 15 minutes."
  val linkTextGG: String     = "Contact technical support with HMRC online services (opens in new tab)"
  val postLinkTextGG: String = "and quote PTA951"
  val linkTextOL: String     = "Contact GOV.UK One Login (opens in new tab)"
  val linkGG: String         = "https://www.gov.uk/government/organisations/hm-revenue-customs/contact/online-services-helpdesk"
  val linkOL: String         = "https://home.account.gov.uk/contact-gov-uk-one-login"

}
