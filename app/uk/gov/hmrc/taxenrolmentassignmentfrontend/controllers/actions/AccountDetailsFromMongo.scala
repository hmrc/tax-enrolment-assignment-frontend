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

package uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.actions

import play.api.libs.json.JsValue
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.{AccountDetails, IVNinoStoreEntry, UsersAssignedEnrolment}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.forms.KeepAccessToSAThroughPTA
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.SessionKeys.{ACCOUNT_TYPE, KEEP_ACCESS_TO_SA_THROUGH_PTA_FORM, OTHER_VALID_PTA_ACCOUNTS, REDIRECT_URL, REPORTED_FRAUD, USER_ASSIGNED_PT_ENROLMENT, USER_ASSIGNED_SA_ENROLMENT, accountDetailsForCredential}

case class AccountDetailsFromMongo(accountType: AccountTypes.Value,
                                   redirectUrl: String,
                                   sessionData: Map[String, JsValue]) {

  val optKeepAccessToSAFormData =
    sessionData.get(KEEP_ACCESS_TO_SA_THROUGH_PTA_FORM).map(_.as[KeepAccessToSAThroughPTA])
  val optReportedFraud =
    sessionData.get(REPORTED_FRAUD).map(_.as[Boolean])
  val optUserAssignedSA =
    sessionData.get(USER_ASSIGNED_SA_ENROLMENT).map(_.as[UsersAssignedEnrolment])
  val optUserAssignedPT =
    sessionData.get(USER_ASSIGNED_PT_ENROLMENT).map(_.as[UsersAssignedEnrolment])
  val optOtherValidPTAAccounts =
    sessionData.get(OTHER_VALID_PTA_ACCOUNTS).map(_.as[Seq[IVNinoStoreEntry]])
  def optAccountDetails(credId: String): Option[AccountDetails] =
    sessionData.get(accountDetailsForCredential(credId)).map(_.as[AccountDetails])

}

object AccountDetailsFromMongo {

  val optAccountType = (sessionData: Map[String, JsValue]) =>
    sessionData.get(ACCOUNT_TYPE).map(_.as[AccountTypes.Value])
  val optRedirectUrl = (sessionData: Map[String, JsValue]) =>
    sessionData.get(REDIRECT_URL).map(_.as[String])
}