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

object ContinueWithCurrentIdMessages {
  val title = "Only your current user ID can access your personal tax account from now on"
  val heading = "Only your current user ID can access your personal tax account from now on"
  val paragraph1 = "You are currently signed in with the Government Gateway user ID testCurrentId."
  val paragraph2 = "This is now the only user ID that can access your personal tax account. Please keep the details of this user ID safe."
  val heading2 = "What has happened to my other Government Gateway user IDs?"
  val paragraph3 = "To access Self Assessment you will need to log in with the user ID Some(TestSAId)."
  val paragraph4 = "If you have any other Government Gateway user IDs, they will lose access to your personal tax account."
  val button = "Continue"
}