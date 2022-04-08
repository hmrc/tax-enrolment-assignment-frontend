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

object EnrolledAfterReportingFraudMessages {
  val title =
    "Only your current user ID can access your personal tax account from now on"
  val heading =
    "Only your current user ID can access your personal tax account from now on"
  val paragraphs: String =
    "You are currently signed in with the Government Gateway user ID ********3214." +
      " This is now the only user ID that can access your personal tax account." +
      " Please keep the details of this user ID safe." +
      " If you have any other Government Gateway user IDs," +
      " they will lose access to your personal tax account."
  val paragraphsSA: String =
    "You are currently signed in with the Government Gateway user ID ********3214." +
      " This is now the only user ID that can access your personal tax account." +
      " Please keep the details of this user ID safe. " +
      "To access Self Assessment you will need to log in with the user ID ********3215." +
      " If you have any other Government Gateway user IDs," +
      " they will lose access to your personal tax account."
  val heading2: String =
    "What has happened to my other Government Gateway user IDs?"
  val button = "Continue"
  val action =
    "/tax-enrolment-assignment-frontend/enrol-pt/enrolment-success-sa-access-not-wanted"
}
