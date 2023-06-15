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
  val selfAssessParagraph1: String =
    "We recommend that you use only one Government Gateway user ID to access your personal tax information."
  val selfAssessParagraph2: String =
    "If you complete Self Assessment, you can choose to:"
  val selfAssessParagraph3: String =
    "On the next screens we will ask you which option you want to choose."
  val selfAssessButton = "Continue"
}
