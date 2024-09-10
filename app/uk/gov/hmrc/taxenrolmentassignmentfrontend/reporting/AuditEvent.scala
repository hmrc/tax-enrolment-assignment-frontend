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
import play.api.libs.json._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes.{SA_ASSIGNED_TO_CURRENT_USER, SA_ASSIGNED_TO_OTHER_USER}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.actions._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.{AccountDetails, MFADetails}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.pages.AccountDetailsForCredentialPage
import uk.gov.hmrc.taxenrolmentassignmentfrontend.services.TENCrypto

import java.util.Locale

case class AuditEvent(auditType: String, transactionName: String, detail: JsObject)

object AuditEvent {

  def auditReportSuspiciousSAAccount(suspiciousAccountDetails: AccountDetails)(implicit
    request: DataRequest[_],
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
    request: DataRequest[_],
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
    request: DataRequest[_],
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
  )(implicit request: DataRequest[_], messagesApi: MessagesApi): AuditEvent = {
    implicit val lang: Lang = getLang
    val optSACredentialId: Option[String] =
      if (request.userDetails.hasSAEnrolment || accountType == SA_ASSIGNED_TO_CURRENT_USER) {
        Some(request.userDetails.credId)
      } else {
        None
      }
    val details: JsObject = getDetailsForPTEnrolled(accountType, optSACredentialId, None)

    auditSuccessfullyEnrolledForPT(details)
  }

  def auditSuccessfullyEnrolledPTWhenSAOnOtherAccount(
    enrolledAfterReportingFraud: Boolean = false
  )(implicit
    request: DataRequest[_],
    messagesApi: MessagesApi,
    crypto: TENCrypto
  ): AuditEvent = {
    implicit val lang: Lang = getLang(request, implicitly)
    val optSACredentialId: Option[String] = if (request.userDetails.hasSAEnrolment) {
      Some(request.userDetails.credId)
    } else {
      request.requestWithUserDetailsFromSessionAndMongo.get.accountDetailsFromMongo.optUserAssignedSA
        .fold[Option[String]](None)(_.enrolledCredential)
    }
    val suspiciousAccountDetails: Option[AccountDetails] = if (enrolledAfterReportingFraud) {
      optSACredentialId.fold[Option[AccountDetails]](None)(credId =>
        request.userAnswers.get(AccountDetailsForCredentialPage(credId))(AccountDetails.mongoFormats(crypto.crypto))
      )
    } else {
      None
    }
    val details: JsObject = getDetailsForPTEnrolled(
      request.requestWithUserDetailsFromSessionAndMongo.get.accountDetailsFromMongo.accountType,
      optSACredentialId,
      suspiciousAccountDetails
    )(request, implicitly, implicitly)

    auditSuccessfullyEnrolledForPT(details)
  }

  def auditSigninAgainWithSACredential()(implicit
    request: DataRequest[_],
    messagesApi: MessagesApi,
    crypto: TENCrypto
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
  )(implicit request: DataRequest[_], messagesApi: MessagesApi, lang: Lang): JsObject = {
    val userDetails: UserDetailsFromSession = request.userDetails
    val defaultDetails = Json.obj(
      "NINO" -> userDetails.nino.nino,
      "currentAccount" -> getCurrentAccountJson(
        userDetails,
        request.requestWithUserDetailsFromSessionAndMongo.get.accountDetailsFromMongo.accountType
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
  )(implicit request: DataRequest[_], messagesApi: MessagesApi, lang: Lang): JsObject = {
    val userDetails: UserDetailsFromSession = request.userDetails
    val defaultDetails = Json.obj(
      "NINO" -> userDetails.nino.nino,
      "currentAccount" -> getCurrentAccountJson(
        userDetails,
        request.requestWithUserDetailsFromSessionAndMongo.get.accountDetailsFromMongo.accountType
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
  )(implicit request: DataRequest[_], messagesApi: MessagesApi, lang: Lang): JsObject = {

    val userDetails: UserDetailsFromSession = request.userDetails
    println("sandeep" + userDetails)
    val optSACredIdJson: JsObject =
      optSACredentialId.fold(Json.obj())(credId => Json.obj("saAccountCredentialId" -> credId))
    val optReportedAccountJson: JsObject = suspiciousAccountDetails.fold(Json.obj()) { accountDetails =>
      val defaultReportedAccountDetails = Json.obj("reportedAccount" -> getPresentedAccountJson(accountDetails))
      if (translationRequired) {
        defaultReportedAccountDetails ++
          Json.obj("reportedAccountEN" -> getTranslatedAccountJson(accountDetails))
      } else {
        defaultReportedAccountDetails
      }
    }

    val jsonto = Json.obj(
      "NINO"           -> userDetails.nino.nino,
      "currentAccount" -> getCurrentAccountJson(userDetails, accountType, withCurrentEmail = true)
    ) ++ optSACredIdJson ++ optReportedAccountJson

    print("sandeepjson:" + jsonto)
    jsonto
  }

  private def getDetailsForSigninAgainSA(implicit
    request: DataRequest[_],
    messagesApi: MessagesApi,
    lang: Lang,
    crypto: TENCrypto
  ): JsObject = {
    val userDetails: UserDetailsFromSession = request.userDetails
    val optSACredId: Option[String] =
      request.requestWithUserDetailsFromSessionAndMongo.get.accountDetailsFromMongo.optUserAssignedSA
        .fold[Option[String]](None)(uae => uae.enrolledCredential)
    val optSAAccountJson: JsObject = optSACredId.fold(Json.obj()) { credId =>
      request.userAnswers
        .get(AccountDetailsForCredentialPage(credId))(AccountDetails.mongoFormats(crypto.crypto))
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
      "currentAccount" -> getCurrentAccountJson(userDetails, SA_ASSIGNED_TO_OTHER_USER)
    ) ++ optSAAccountJson
  }

  private def getCurrentAccountJson(
    userDetails: UserDetailsFromSession,
    accountType: AccountTypes.Value,
    withCurrentEmail: Boolean = false
  ): JsObject = {

    val emailObj = if (withCurrentEmail) {
      Json.obj("email" -> userDetails.email.getOrElse("-").toString)
    } else {
      Json.obj()
    }
    Json.obj(
      "credentialId" -> userDetails.credId,
      "type"         -> accountType.toString
    ) ++ userDetails.affinityGroup.toJson.as[JsObject].deepMerge(emailObj)

  }

  private def getPresentedAccountJson(
    accountDetails: AccountDetails
  )(implicit messagesApi: MessagesApi, lang: Lang): JsObject =
    Json.obj(
      "credentialId" -> accountDetails.credId,
      "userId"       -> messagesApi("common.endingWith", accountDetails.userId),
      "email"        -> accountDetails.emailDecrypted.getOrElse("-").toString,
      "lastSignedIn" -> accountDetails.lastLoginDate.getOrElse("").toString,
      "mfaDetails"   -> mfaDetailsToJson(accountDetails.mfaDetails)
    )

  private def getTranslatedAccountJson(accountDetails: AccountDetails)(implicit messagesApi: MessagesApi): JsObject = {
    val enLang = Lang(Locale.ENGLISH)
    Json.obj(
      "credentialId" -> accountDetails.credId,
      "userId"       -> messagesApi("common.endingWith", accountDetails.userId)(enLang),
      "email"        -> accountDetails.emailDecrypted.getOrElse("-").toString,
      "lastSignedIn" -> accountDetails.lastLoginDate.getOrElse("").toString,
      "mfaDetails"   -> mfaDetailsToJson(accountDetails.mfaDetails)(messagesApi, enLang)
    )
  }

  private def mfaDetailsToJson(mfaDetails: Seq[MFADetails])(implicit messagesApi: MessagesApi, lang: Lang): JsValue =
    JsArray(mfaDetails.map(mfaFactorToJson))

  private def mfaFactorToJson(mfaDetail: MFADetails)(implicit messagesApi: MessagesApi, lang: Lang): JsValue =
    mfaDetail.factorNameKey match {
      case "mfaDetails.totp" =>
        Json.obj(
          "factorType"  -> messagesApi("mfaDetails.totp"),
          "factorValue" -> mfaDetail.factorValue
        )
      case "mfaDetails.voice" =>
        Json.obj(
          "factorType"  -> messagesApi("mfaDetails.voice"),
          "factorValue" -> messagesApi("common.endingWith", mfaDetail.factorValue)
        )
      case _ =>
        Json.obj(
          "factorType"  -> messagesApi("mfaDetails.text"),
          "factorValue" -> messagesApi("common.endingWith", mfaDetail.factorValue)
        )
    }

  private def getLang(implicit request: DataRequest[_], messagesApi: MessagesApi): Lang =
    if (request.requestWithUserDetailsFromSessionAndMongo.isDefined) {
      messagesApi
        .preferred(
          request.requestWithUserDetailsFromSessionAndMongo.get.request
        )
        .lang
    } else {
      messagesApi
        .preferred(
          request.request
        )
        .lang
    }

  private def translationRequired(implicit lang: Lang): Boolean =
    lang.locale != Locale.ENGLISH
}
