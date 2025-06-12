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
  val title: String      = "We are changing the way you access your personal tax account" + defaultTitleSuffix
  val heading            = "You have more than one Government Gateway user ID"
  val paragraph1: String =
    "We recommend you use only one set of sign in details."
  val paragraph2: String = "If you complete Self Assessment, you can choose:"
  val paragraph3: String = "On the next screens, we will ask you which option you want to choose."
  val listItem1: String  =
    "the same Government Gateway user ID to access both your personal tax account and Self Assessment"
  val listItem2: String  = "or separate Government Gateway user IDs for each"
  val selfAssessButton   = "Continue"
}
