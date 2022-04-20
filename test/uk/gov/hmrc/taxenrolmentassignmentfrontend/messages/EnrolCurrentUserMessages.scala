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

package uk.gov.hmrc.taxenrolmentassignmentfrontend.messages

object EnrolCurrentUserMessages {

  val continue = "Continue"
  val title =
    "Which Government Gateway user ID do you want to use to access your personal tax information?"
  val heading =
    "Which Government Gateway user ID do you want to use to access your personal tax information?"
  def radioCurrentUserId(id: String) = s"My current user ID $id"
  val radioOtherUserId = "Another user ID"
  def warning(id: String) =
    s"Warning You have access to Self Assessment under user ID $id. We recommend signing back in with this user ID so that you can access Self Assessment from your personal tax account."
  val errorTitle = "There is a problem"
  val errorMessage = "Confirm which Government Gateway user ID you want to use"
  val button = "Continue"
}
