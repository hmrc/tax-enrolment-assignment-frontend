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
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes.{SA_ASSIGNED_TO_CURRENT_USER, SA_ASSIGNED_TO_OTHER_USER}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.actions.{RequestWithUserDetailsFromSession, RequestWithUserDetailsFromSessionAndMongo, UserDetailsFromSession}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.{AccountDetails, MFADetails}

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
        suspiciousAccountDetails
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
        suspiciousAccountDetails
      )
    )
  }

  def auditSuccessfullyAutoEnrolledPersonalTax(accountType: AccountTypes.Value)
                                              (implicit request: RequestWithUserDetailsFromSession[_]): AuditEvent = {

    val optSACredentialId = if(request.userDetails.hasSAEnrolment || accountType == SA_ASSIGNED_TO_CURRENT_USER) {
      Some(request.userDetails.credId)
    } else {
      None
    }
    AuditEvent(
      auditType = "SuccessfullyEnrolledPersonalTax",
      transactionName = "successfully-enrolled-personal-tax",
      detail = getDetailsForPTEnrolled(accountType, optSACredentialId, None)
    )
  }

  def auditSuccessfullyEnrolledPersonalTax(enrolledAfterReportingFraud: Boolean = false)
                                          (implicit request: RequestWithUserDetailsFromSessionAndMongo[_]): AuditEvent = {
    val optSACredentialId = if(request.userDetails.hasSAEnrolment) {
      Some(request.userDetails.credId)
    } else {
      request.accountDetailsFromMongo.optUserAssignedSA.fold[Option[String]](None)(_.enrolledCredential)
    }
    val suspiciousAccountDetails = if(enrolledAfterReportingFraud) {
      optSACredentialId.fold[Option[AccountDetails]](None)(credId => request.accountDetailsFromMongo.optAccountDetails(credId))
    } else {
      None
    }
    AuditEvent(
      auditType = "SuccessfullyEnrolledPersonalTax",
      transactionName = "successfully-enrolled-personal-tax",
      detail = getDetailsForPTEnrolled(
        request.accountDetailsFromMongo.accountType,
        optSACredentialId, suspiciousAccountDetails)(request)
    )
  }

  def auditSigninAgainWithSACredential()
                                          (implicit request: RequestWithUserDetailsFromSessionAndMongo[_]): AuditEvent = {
    AuditEvent(
      auditType = "SignInWithOtherAccount",
      transactionName = "sign-in-with-other-account",
      detail = getDetailsForSigninAgainSA
    )
  }

  private def getDetailsForReportingFraud(
    suspiciousAccountDetails: AccountDetails
  )(implicit request: RequestWithUserDetailsFromSessionAndMongo[_]): JsObject = {
    val userDetails = request.userDetails
    Json.obj(
      ("NINO", JsString(userDetails.nino)),
      ("currentAccount", getCurrentAccountJson(userDetails, request.accountDetailsFromMongo.accountType)),
      ("reportedAccount", getPresentedAccountJson(suspiciousAccountDetails))
    )
  }

  private def getDetailsForPTEnrolled(accountType: AccountTypes.Value,
                                      optSACredentialId: Option[String],
                                      suspiciousAccountDetails: Option[AccountDetails])
                                     (implicit request: RequestWithUserDetailsFromSession[_]): JsObject = {
    val userDetails = request.userDetails
    val optSACredIdJson = optSACredentialId.fold(Json.obj())(credId => Json.obj(("saAccountCredentialId", JsString(credId))))
    val optReportedAccountJson = suspiciousAccountDetails.fold(Json.obj())(accountDetails =>
      Json.obj(("reportedAccount", getPresentedAccountJson(accountDetails))))

    Json.obj(
      ("NINO", JsString(userDetails.nino)),
      ("currentAccount", getCurrentAccountJson(userDetails, accountType))
    ) ++ optSACredIdJson ++ optReportedAccountJson
  }



  private def getDetailsForSigninAgainSA(implicit request: RequestWithUserDetailsFromSessionAndMongo[_]): JsObject = {
    val userDetails = request.userDetails
    val optSACredId: Option[String] = request.accountDetailsFromMongo.optUserAssignedSA.fold[Option[String]](None)(uae => uae.enrolledCredential)
    val optSAAccountJson = optSACredId.fold(Json.obj())(credId =>
      request.accountDetailsFromMongo.optAccountDetails(credId)
        .fold(Json.obj())(accountDetails => Json.obj(("saAccount", getPresentedAccountJson(accountDetails))))
      )

    Json.obj(
      ("NINO", JsString(userDetails.nino)),
      ("currentAccount", getCurrentAccountJson(userDetails, SA_ASSIGNED_TO_OTHER_USER))
    ) ++ optSAAccountJson
  }

  private def getCurrentAccountJson(userDetails: UserDetailsFromSession,
                                    accountType: AccountTypes.Value): JsObject = {
    Json.obj(
      ("credentialId", JsString(userDetails.credId)),
      ("type", JsString(accountType.toString))
    ) ++ userDetails.affinityGroup.toJson.as[JsObject]
  }

  private def getPresentedAccountJson(suspiciousAccountDetails: AccountDetails): JsObject = {
    Json.obj(
      ("credentialId", JsString(suspiciousAccountDetails.credId)),
      ("userId", JsString(s"Ending with ${suspiciousAccountDetails.userId}")),
      ("email", JsString(suspiciousAccountDetails.email.getOrElse("-"))),
      ("lastSignedIn", JsString(suspiciousAccountDetails.lastLoginDate)),
      ("mfaDetails", mfaDetailsToJson(suspiciousAccountDetails.mfaDetails))
    )
  }

  private def mfaDetailsToJson(mfaDetails: Seq[MFADetails]): JsValue =
    JsArray(mfaDetails.map(mfaFactorToJson))

  private def mfaFactorToJson(mfaDetail: MFADetails): JsValue =
    mfaDetail.factorNameKey match {
      case "mfaDetails.totp" =>
        Json.obj(
          ("factorType", JsString("Authenticator app")),
          ("factorValue", JsString(mfaDetail.factorValue))
        )
      case "mfaDetails.voice" =>
        Json.obj(
          ("factorType", JsString("Phone number")),
          ("factorValue", JsString(s"Ending with ${mfaDetail.factorValue}"))
        )
      case _ =>
        Json.obj(
          ("factorType", JsString("Text message")),
          ("factorValue", JsString(s"Ending with ${mfaDetail.factorValue}"))
        )
    }
}
