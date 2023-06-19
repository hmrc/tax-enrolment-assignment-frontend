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

object SABlueInterruptMessages extends BaseMessage {
  val title = "You currently have more than one Government Gateway user ID" + defaultTitleSuffix
  val heading = "You currently have more than one Government Gateway user ID"
  val paragraph1: String =
    "We recommend that you use only one Government Gateway user ID to access your personal tax information."
  val paragraph2: String = "If you complete Self Assessment, you can choose to:"
  val paragraph3: String = "On the next screens we will ask you which option you want to choose."
  val listItem1: String = "use a single user ID for both personal tax and Self Assessment"
  val listItem2: String = "use a separate user ID for Self Assessment only"
  val selfAssessButton = "Continue"
}
