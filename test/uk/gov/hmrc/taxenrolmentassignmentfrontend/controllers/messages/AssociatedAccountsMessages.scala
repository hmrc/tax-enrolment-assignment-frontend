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

package uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.messages

object AssociatedAccountsMessages {

  val continue = "Continue"
  val title =
    "Government Gateway user IDs with access to your personal tax information"
  val heading =
    "Government Gateway user IDs with access to your personal tax information"
  def userId(id: String): String = s"User ID$id"
  val radiosHeading = "Do all of these user IDs belong to you?"
  val radioDoesRecogniseIds = "Yes"
  val radioDoesNotRecogniseIds = "No, I want to report a suspicious user ID"
  val errorTitle = "There is a problem"
  val errorMessage = "Confirm if the user IDs belong to you"
  val button = "Continue"
}
