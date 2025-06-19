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

package uk.gov.hmrc.taxenrolmentassignmentfrontend.reporting

import play.api.i18n.{Lang, MessagesApi}
import play.api.libs.json.*
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes.{SA_ASSIGNED_TO_CURRENT_USER, SA_ASSIGNED_TO_OTHER_USER}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.actions.*
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.{AccountDetails, MFADetails}

import java.util.Locale

case class AuditEvent(auditType: String, transactionName: String, detail: JsObject)

object AuditEvent {

  def auditReportSuspiciousSAAccount(suspiciousAccountDetails: AccountDetails)(implicit
    request: RequestWithUserDetailsFromSessionAndMongo[_],
    messagesApi: MessagesApi
  ): AuditEvent = {
    implicit val lang: Lang = getLang(request, implicitly)
    AuditEvent(
      auditType = "ReportUnrecognisedAccount",
      transactionName = "reporting-unrecognised-sa-account",
      detail = getDetailsForReportingFraud(
        suspiciousAccountDetails
      )(request, messagesApi, lang)
    )
  }

  def auditReportSuspiciousPTAccount(suspiciousAccountDetails: AccountDetails)(implicit
    request: RequestWithUserDetailsFromSessionAndMongo[_],
    messagesApi: MessagesApi
  ): AuditEvent = {
    implicit val lang: Lang = getLang(request, implicitly)
    AuditEvent(
      auditType = "ReportUnrecognisedAccount",
      transactionName = "reporting-unrecognised-pt-account",
      detail = getDetailsForReportingFraud(
        suspiciousAccountDetails
      )
    )
  }

  def auditPTEnrolmentOnOtherAccount(enrolledAccountDetails: AccountDetails)(implicit
    request: RequestWithUserDetailsFromSessionAndMongo[_],
    messagesApi: MessagesApi
  ): AuditEvent = {
    implicit val lang: Lang = getLang(request, implicitly)
    AuditEvent(
      auditType = "EnrolledOnAnotherAccount",
      transactionName = "enrolled-on-another-account",
      detail = getDetailsForEnrolmentOnAnotherAccount(
        enrolledAccountDetails
      )
    )
  }

  def auditSuccessfullyEnrolledPTWhenSANotOnOtherAccount(
    accountType: AccountTypes.Value
  )(implicit request: RequestWithUserDetailsFromSessionAndMongo[_], messagesApi: MessagesApi): AuditEvent = {
    implicit val lang: Lang               = getLang
    val optSACredentialId: Option[String] =
      if (request.userDetails.hasSAEnrolment || accountType == SA_ASSIGNED_TO_CURRENT_USER) {
        Some(request.userDetails.credId)
      } else {
        None
      }
    val details: JsObject                 = getDetailsForPTEnrolled(accountType, optSACredentialId, None)

    auditSuccessfullyEnrolledForPT(details)
  }

  def auditSuccessfullyEnrolledPTWhenSAOnOtherAccount(
    enrolledAfterReportingFraud: Boolean = false
  )(implicit request: RequestWithUserDetailsFromSessionAndMongo[_], messagesApi: MessagesApi): AuditEvent = {
    implicit val lang: Lang                              = getLang(request, implicitly)
    val optSACredentialId: Option[String]                = if (request.userDetails.hasSAEnrolment) {
      Some(request.userDetails.credId)
    } else {
      request.accountDetailsFromMongo.optUserAssignedSA.fold[Option[String]](None)(_.enrolledCredential)
    }
    val suspiciousAccountDetails: Option[AccountDetails] = if (enrolledAfterReportingFraud) {
      optSACredentialId.fold[Option[AccountDetails]](None)(credId =>
        request.accountDetailsFromMongo.optAccountDetails(credId)
      )
    } else {
      None
    }
    val details: JsObject                                = getDetailsForPTEnrolled(
      request.accountDetailsFromMongo.accountType,
      optSACredentialId,
      suspiciousAccountDetails
    )

    auditSuccessfullyEnrolledForPT(details)
  }

  def auditSigninAgainWithSACredential()(implicit
    request: RequestWithUserDetailsFromSessionAndMongo[_],
    messagesApi: MessagesApi
  ): AuditEvent = {
    implicit val lang: Lang = getLang(request, implicitly)
    AuditEvent(
      auditType = "SignInWithOtherAccount",
      transactionName = "sign-in-with-other-account",
      detail = getDetailsForSigninAgainSA
    )
  }

  private def auditSuccessfullyEnrolledForPT(details: JsObject): AuditEvent =
    AuditEvent(
      auditType = "SuccessfullyEnrolledPersonalTax",
      transactionName = "successfully-enrolled-personal-tax",
      detail = details
    )

  private def getDetailsForReportingFraud(
    suspiciousAccountDetails: AccountDetails
  )(implicit request: RequestWithUserDetailsFromSessionAndMongo[_], messagesApi: MessagesApi, lang: Lang): JsObject = {
    val userDetails: UserDetailsFromSession = request.userDetails
    val defaultDetails                      = Json.obj(
      "NINO"            -> userDetails.nino.nino,
      "currentAccount"  -> getCurrentAccountJson(
        userDetails,
        request.accountDetailsFromMongo.accountType,
        request.accountDetailsFromMongo.optAccountDetails(userDetails.credId)
      ),
      "reportedAccount" -> getPresentedAccountJson(suspiciousAccountDetails)
    )
    if (translationRequired) {
      defaultDetails ++
        Json.obj("reportedAccountEN" -> getTranslatedAccountJson(suspiciousAccountDetails))
    } else {
      defaultDetails
    }
  }
  private def getDetailsForEnrolmentOnAnotherAccount(
    enrolledAccountDetails: AccountDetails
  )(implicit request: RequestWithUserDetailsFromSessionAndMongo[_], messagesApi: MessagesApi, lang: Lang): JsObject = {
    val userDetails: UserDetailsFromSession = request.userDetails
    val defaultDetails                      = Json.obj(
      "NINO"            -> userDetails.nino.nino,
      "currentAccount"  -> getCurrentAccountJson(
        userDetails,
        request.accountDetailsFromMongo.accountType,
        request.accountDetailsFromMongo.optAccountDetails(userDetails.credId)
      ),
      "enrolledAccount" -> getPresentedAccountJson(enrolledAccountDetails)
    )
    if (translationRequired) {
      defaultDetails ++
        Json.obj("enrolledAccountEN" -> getTranslatedAccountJson(enrolledAccountDetails))
    } else {
      defaultDetails
    }
  }

  private def getDetailsForPTEnrolled(
    accountType: AccountTypes.Value,
    optSACredentialId: Option[String],
    suspiciousAccountDetails: Option[AccountDetails]
  )(implicit request: RequestWithUserDetailsFromSessionAndMongo[_], messagesApi: MessagesApi, lang: Lang): JsObject = {

    val userDetails: UserDetailsFromSession = request.userDetails
    val optSACredIdJson: JsObject           =
      optSACredentialId.fold(Json.obj())(credId => Json.obj("saAccountCredentialId" -> credId))
    val optReportedAccountJson: JsObject    = suspiciousAccountDetails.fold(Json.obj()) { accountDetails =>
      val defaultReportedAccountDetails = Json.obj("reportedAccount" -> getPresentedAccountJson(accountDetails))
      if (translationRequired) {
        defaultReportedAccountDetails ++
          Json.obj("reportedAccountEN" -> getTranslatedAccountJson(accountDetails))
      } else {
        defaultReportedAccountDetails
      }
    }

    Json.obj(
      "NINO"           -> userDetails.nino.nino,
      "currentAccount" -> getCurrentAccountJson(
        userDetails,
        accountType,
        request.accountDetailsFromMongo.optAccountDetails(userDetails.credId)
      )
    ) ++ optSACredIdJson ++ optReportedAccountJson
  }

  private def getDetailsForSigninAgainSA(implicit
    request: RequestWithUserDetailsFromSessionAndMongo[_],
    messagesApi: MessagesApi,
    lang: Lang
  ): JsObject = {
    val userDetails: UserDetailsFromSession = request.userDetails
    val optSACredId: Option[String]         =
      request.accountDetailsFromMongo.optUserAssignedSA.fold[Option[String]](None)(uae => uae.enrolledCredential)
    val optSAAccountJson: JsObject          = optSACredId.fold(Json.obj()) { credId =>
      request.accountDetailsFromMongo
        .optAccountDetails(credId)
        .fold(Json.obj()) { accountDetails =>
          val defaultSAAccountDetails = Json.obj("saAccount" -> getPresentedAccountJson(accountDetails))
          if (translationRequired) {
            defaultSAAccountDetails ++
              Json.obj("saAccountEN" -> getTranslatedAccountJson(accountDetails))
          } else {
            defaultSAAccountDetails
          }
        }
    }

    Json.obj(
      "NINO"           -> userDetails.nino.nino,
      "currentAccount" -> getCurrentAccountJson(
        userDetails,
        SA_ASSIGNED_TO_OTHER_USER,
        request.accountDetailsFromMongo.optAccountDetails(userDetails.credId)
      )
    ) ++ optSAAccountJson
  }

  private def getCurrentAccountJson(
    userDetails: UserDetailsFromSession,
    accountType: AccountTypes.Value,
    accountDetailsOpt: Option[AccountDetails] = None
  ): JsObject = {
    val correctAuthProvider =
      accountDetailsOpt.map(_.identityProviderType.toString).getOrElse(userDetails.providerType)

    Json.obj(
      "credentialId" -> userDetails.credId,
      "type"         -> accountType.toString,
      "authProvider" -> correctAuthProvider,
      "email"        -> userDetails.email.getOrElse[String]("-")
    ) ++ userDetails.affinityGroup.toJson.as[JsObject]
  }

  private def getPresentedAccountJson(
    accountDetails: AccountDetails
  )(implicit messagesApi: MessagesApi, lang: Lang): JsObject =
    Json.obj(
      "credentialId" -> accountDetails.credId,
      "userId"       -> messagesApi("common.endingWith", accountDetails.userId),
      "email"        -> accountDetails.emailDecrypted.getOrElse[String]("-"),
      "lastSignedIn" -> accountDetails.lastLoginDate.getOrElse[String](""),
      "mfaDetails"   -> mfaDetailsToJson(accountDetails.mfaDetails),
      "authProvider" -> accountDetails.identityProviderType.toString
    )

  private def getTranslatedAccountJson(accountDetails: AccountDetails)(implicit messagesApi: MessagesApi): JsObject = {
    val enLang = Lang(Locale.ENGLISH)
    Json.obj(
      "credentialId" -> accountDetails.credId,
      "userId"       -> messagesApi("common.endingWith", accountDetails.userId)(enLang),
      "email"        -> accountDetails.emailDecrypted.getOrElse[String]("-"),
      "lastSignedIn" -> accountDetails.lastLoginDate.getOrElse[String](""),
      "mfaDetails"   -> mfaDetailsToJson(accountDetails.mfaDetails)(messagesApi, enLang),
      "authProvider" -> accountDetails.identityProviderType.toString
    )
  }

  private def mfaDetailsToJson(mfaDetails: Seq[MFADetails])(implicit messagesApi: MessagesApi, lang: Lang): JsValue =
    JsArray(mfaDetails.map(mfaFactorToJson))

  private def mfaFactorToJson(mfaDetail: MFADetails)(implicit messagesApi: MessagesApi, lang: Lang): JsValue =
    mfaDetail.factorNameKey match {
      case "mfaDetails.totp"  =>
        Json.obj(
          "factorType"  -> messagesApi("mfaDetails.totp"),
          "factorValue" -> mfaDetail.factorValue
        )
      case "mfaDetails.voice" =>
        Json.obj(
          "factorType"  -> messagesApi("mfaDetails.voice"),
          "factorValue" -> messagesApi("common.endingWith", mfaDetail.factorValue)
        )
      case _                  =>
        Json.obj(
          "factorType"  -> messagesApi("mfaDetails.text"),
          "factorValue" -> messagesApi("common.endingWith", mfaDetail.factorValue)
        )
    }

  private def getLang(implicit request: RequestWithUserDetailsFromSessionAndMongo[_], messagesApi: MessagesApi): Lang =
    messagesApi.preferred(request.request).lang

  private def translationRequired(implicit lang: Lang): Boolean =
    lang.locale != Locale.ENGLISH
}
