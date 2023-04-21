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

package uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.actions

import play.api.libs.json.JsValue
import uk.gov.hmrc.crypto.{AesGCMCrypto, Decrypter, Encrypter}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.{AccountDetails, IVNinoStoreEntry, UsersAssignedEnrolment}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.forms.KeepAccessToSAThroughPTA
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.SessionKeys.{ACCOUNT_TYPE, KEEP_ACCESS_TO_SA_THROUGH_PTA_FORM, REDIRECT_URL, REPORTED_FRAUD, USER_ASSIGNED_PT_ENROLMENT, USER_ASSIGNED_SA_ENROLMENT, accountDetailsForCredential}

case class AccountDetailsFromMongo(accountType: AccountTypes.Value,
                                   redirectUrl: String,
                                   private val sessionData: Map[String, JsValue])(private val crypto: Encrypter with Decrypter) {

  val optKeepAccessToSAFormData: Option[KeepAccessToSAThroughPTA] =
    sessionData.get(KEEP_ACCESS_TO_SA_THROUGH_PTA_FORM).map(_.as[KeepAccessToSAThroughPTA])
  val optReportedFraud: Option[Boolean] =
    sessionData.get(REPORTED_FRAUD).map(_.as[Boolean])
  val optUserAssignedSA: Option[UsersAssignedEnrolment] =
    sessionData.get(USER_ASSIGNED_SA_ENROLMENT).map(_.as[UsersAssignedEnrolment])
  val optUserAssignedPT: Option[UsersAssignedEnrolment] =
    sessionData.get(USER_ASSIGNED_PT_ENROLMENT).map(_.as[UsersAssignedEnrolment])
  def optAccountDetails(credId: String): Option[AccountDetails] =
    sessionData.get(accountDetailsForCredential(credId)).map(_.as[AccountDetails](AccountDetails.mongoFormats(crypto)))

}

object AccountDetailsFromMongo {
  val optAccountType: Map[String, JsValue] => Option[AccountTypes.Value] = (sessionData: Map[String, JsValue]) =>
    sessionData.get(ACCOUNT_TYPE).map(_.as[AccountTypes.Value])
  val optRedirectUrl: Map[String, JsValue] => Option[String] = (sessionData: Map[String, JsValue]) =>
    sessionData.get(REDIRECT_URL).map(_.as[String])
}
