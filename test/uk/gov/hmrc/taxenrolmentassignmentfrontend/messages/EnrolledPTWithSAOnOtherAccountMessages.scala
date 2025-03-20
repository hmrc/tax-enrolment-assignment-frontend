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

object EnrolledPTWithSAOnOtherAccountMessages extends BaseMessage {
  val title: String =
    "You are choosing to have two Government Gateway user IDs" + defaultTitleSuffix
  val heading =
    "You have chosen to have two separate Government Gateway user IDs"
  val subheading1: String = "User ID for Self Assessment in business tax account"
  val paragraph1: String =
    s"You can only use these sign in details from now on. Keep these details safe."
  def paragraph2(userId: String): String =
    s"You are currently signed in with Government Gateway user ID ending with $userId"
  val subheading2 = "Self Assessment"
  val linkText = "The above user ID does not belong to me"
  val exclamation =
    "If you have any other Government Gateway user IDs, you will not be able to use them to access your personal tax information or Self Assessment" // TODO - Add once class is figured out
  val ptaLinkText = "Continue to personal tax account"
  val ptaLink =
    "/personal-account"
}
