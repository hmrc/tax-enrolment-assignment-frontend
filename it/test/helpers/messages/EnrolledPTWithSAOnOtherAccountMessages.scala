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
  val title                              =
    "You are choosing to have two Government Gateway user IDs"
  val paragraph1: String                 =
    "You can only use these sign in details from now on. Keep these details safe."
  def paragraph2(userId: String): String =
    s"You are currently signed in with Government Gateway user ID ending with $userId"
}
