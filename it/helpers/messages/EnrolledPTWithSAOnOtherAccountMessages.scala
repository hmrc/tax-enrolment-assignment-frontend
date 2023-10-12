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

package helpers.messages

object EnrolledPTWithSAOnOtherAccountMessages {
  val title =
    "You are choosing to have two Government Gateway user IDs"
  def paragraph1(userId: String): String =
    s"The user ID you are signed in with now ends in $userId. This will be the only ID you can use to access you personal tax information."
  def paragraph2(userId: String): String =
    s"In the future, to manage your Self Assessment, you will need to go to your business tax account. To do this now, you must sign out and sign in with your other user ID ending in $userId."
  val paragraph3: String =
    "If you do not want to go to your Self Assessment now, please make a note of the details here. This will be the only user ID you can use to access Self Assessment"
}
