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

package uk.gov.hmrc.taxenrolmentassignmentfrontend.reporting

import java.time.ZonedDateTime
import java.time.temporal.{ChronoUnit, Temporal}

import akka.http.scaladsl.model.StatusCode
import play.api.libs.json._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.actions.{
  RequestWithUserDetailsFromSession,
  RequestWithUserDetailsFromSessionAndMongo,
  UserDetailsFromSession
}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.{
  AccountDetails,
  MFADetails
}

case class AuditEvent(auditType: String,
                      transactionName: String,
                      detail: JsObject)

object AuditEvent {

  def auditReportSuspiciousSAAccount(suspiciousAccountDetails: AccountDetails)(
    implicit request: RequestWithUserDetailsFromSessionAndMongo[_]
  ): AuditEvent = {
    AuditEvent(
      auditType = "ReportUnrecognisedAccount",
      transactionName = "reporting-unrecognised-sa-account",
      detail = getDetailsForReportingFraud(
        request.userDetails,
        suspiciousAccountDetails,
        AccountTypes.SA_ASSIGNED_TO_OTHER_USER
      )
    )
  }

  def auditReportSuspiciousPTAccount(suspiciousAccountDetails: AccountDetails)(
    implicit request: RequestWithUserDetailsFromSessionAndMongo[_]
  ): AuditEvent = {
    AuditEvent(
      auditType = "ReportUnrecognisedAccount",
      transactionName = "reporting-unrecognised-pt-account",
      detail = getDetailsForReportingFraud(
        request.userDetails,
        suspiciousAccountDetails,
        AccountTypes.PT_ASSIGNED_TO_OTHER_USER
      )
    )
  }

  private def getDetailsForReportingFraud(
    userDetails: UserDetailsFromSession,
    suspiciousAccountDetails: AccountDetails,
    accountType: AccountTypes.Value
  ): JsObject = {
    val currentAccountDetails = Json.obj(
      ("credentialId", JsString(userDetails.credId)),
      ("type", JsString(accountType.toString)),
      (
        "affinityGroup",
        JsString(userDetails.affinityGroup.getClass.getSimpleName)
      )
    )

    val reportedAccountDetails = Json.obj(
      ("credentialId", JsString(suspiciousAccountDetails.credId)),
      ("userId", JsString(s"Ending with ${suspiciousAccountDetails.userId}")),
      ("email", JsString(suspiciousAccountDetails.email.getOrElse("-"))),
      ("lastSignedIn", JsString(suspiciousAccountDetails.lastLoginDate)),
      ("mfaDetails", mfaDetailsToJson(suspiciousAccountDetails.mfaDetails))
    )

    Json.obj(
      ("NINO", JsString(userDetails.nino)),
      ("currentAccount", currentAccountDetails),
      ("reportedAccount", reportedAccountDetails)
    )
  }

  private def mfaDetailsToJson(mfaDetails: Seq[MFADetails]): JsValue =
    JsArray(mfaDetails.map(mfaFactorToJson))

  private def mfaFactorToJson(mfaDetail: MFADetails): JsValue =
    mfaDetail.factorNameKey match {
      case "mfaDetails.totp" =>
        Json.obj(("Authenticator app", JsString(mfaDetail.factorValue)))
      case "mfaDetails.voice" =>
        Json.obj(
          ("Phone number", JsString(s"Ending with ${mfaDetail.factorValue}"))
        )
      case _ =>
        Json.obj(
          ("Text message", JsString(s"Ending with ${mfaDetail.factorValue}"))
        )
    }
}
