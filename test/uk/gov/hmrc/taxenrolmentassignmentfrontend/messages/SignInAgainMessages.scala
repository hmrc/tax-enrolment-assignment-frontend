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

object SignInAgainMessages extends BaseMessage {
  val title: String = "You have chosen to have a single Government Gateway user ID" + defaultTitleSuffix
  val heading = "You now need to sign in again"
  val subheading = "User ID for personal tax information and Self Assessment"
  val paragraph1 = "You are currently signed in with a user ID ending with "
  val paragraph2 = "To access personal tax and Self Assessment:"
  val heading2 = "What to do next"
  val backLink = "Back"
  val linkText = "The above user ID does not belong to me"
  val listItem1 = "sign out now"
  val listItem2 = "sign in again using your other user ID ending with "
  val exclamation =
    "If you have any other Government Gateway user IDs, you will not be able to use them to access your personal tax information or Self Assessment" // TODO - Add once class is figured out
  val confirmAndSignOut = "Confirm and sign out"
}
