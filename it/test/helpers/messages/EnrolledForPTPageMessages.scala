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

object EnrolledForPTPageMessages {
  val title                 =
    "You can only access your personal tax account with one user ID from now on"
  val heading               =
    "Only your current user ID can access your personal tax account from now on"
  val paragraphSA: String   =
    "You are currently signed in with the Government Gateway user ID ending with 3214. This is now the only user ID that can access your personal tax account. Please keep the details of this user ID safe. You also have access to Self Assessment with your current user ID. If you have any other Government Gateway user IDs, they will lose access to your personal tax account."
  val paragraphNoSA: String =
    "You are currently signed in with the Government Gateway user ID ending with 3214. This is now the only user ID that can access your personal tax account. Please keep the details of this user ID safe. If you have any other Government Gateway user IDs, they will lose access to your personal tax account."
  val heading3: String      =
    "What has happened to my other Government Gateway user IDs?"
  val button                = "Continue"
  val action                =
    "/protect-tax-info/enrol-pt/enrolment-success-no-sa"
}
