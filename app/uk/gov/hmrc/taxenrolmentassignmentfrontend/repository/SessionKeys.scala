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

package uk.gov.hmrc.taxenrolmentassignmentfrontend.repository

object SessionKeys {

  val ACCOUNT_TYPE                       = "ACCOUNT_TYPE"
  val USER_ASSIGNED_PT_ENROLMENT         = "USER_ASSIGNED_PT_ENROLMENT"
  val USER_ASSIGNED_SA_ENROLMENT         = "USER_ASSIGNED_SA_ENROLMENT"
  val REDIRECT_URL                       = "redirectURL"
  val HAS_OTHER_VALID_PTA_ACCOUNTS       = "HAS_OTHER_VALID_PTA_ACCOUNTS"
  val REPORTED_FRAUD                     = "reportedFraud"
  val KEEP_ACCESS_TO_SA_THROUGH_PTA_FORM = "KEEP_ACCESS_TO_SA_THROUGH_PTA_FORM"

  def accountDetailsForCredential(credId: String) = s"AccountDetailsFor$credId"

}
