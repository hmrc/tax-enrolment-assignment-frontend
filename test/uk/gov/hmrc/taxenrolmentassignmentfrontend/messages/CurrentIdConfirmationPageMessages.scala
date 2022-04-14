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

object CurrentIdConfirmationPageMessages {
  val title = "Your personal tax information can only be accessed from one Government Gateway user ID"
  val heading = "Your personal tax information can only be accessed from one Government Gateway user ID"
  val paragraph: String = "You are currently signed in with the Government Gateway user ID currentId." +
    " From now on, you can only access your personal tax information with this user ID."+
    " To access Self Assessment you can still use the ID SaCred, however this user ID will no longer access your personal tax account."
  val button = "Continue"
}