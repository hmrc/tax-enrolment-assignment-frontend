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

object KeepAccessToSAMessages extends BaseMessage {

  val continue = "Continue"
  val title: String =
    "Do you want to keep access to Self Assessment from your personal tax account?" + defaultTitleSuffix
  val heading =
    "Do you want to use the same Government Gateway user ID to access your personal tax account and Self Assessment?"
  val radioYes = "Yes"
  val radioNo = "No, I want to keep them separate"
  val noSALink = "I do not complete Self Assessment Online"
  val fraudReportingUrl =
    "/protect-tax-info/enrol-pt/contact-hmrc-sa"
  val errorTitle = "There is a problem"
  val errorMessage =
    "Select yes if you want to use the same sign in details for your personal tax account and Self Assessment"
  val button = "Continue"
  val action: String =
    uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.routes.KeepAccessToSAController.continue.url

}
