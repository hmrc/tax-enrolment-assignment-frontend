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

import play.api.libs.json.{JsObject, JsString, Json}
import uk.gov.hmrc.crypto.Sensitive.SensitiveString
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes.{PT_ASSIGNED_TO_OTHER_USER, SA_ASSIGNED_TO_CURRENT_USER, SA_ASSIGNED_TO_OTHER_USER, SINGLE_OR_MULTIPLE_ACCOUNTS}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.actions.{AccountDetailsFromMongo, RequestWithUserDetailsFromSessionAndMongo}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.BaseSpec
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.TestData._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.{AccountDetails, MFADetails, SCP, UserAnswers}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.pages.{AccountDetailsForCredentialPage, UserAssignedSaEnrolmentPage}

class AuditEventSpec extends BaseSpec {

  val accountDetailsWithOneMFADetails: AccountDetails = AccountDetails(
    identityProviderType = SCP,
    credId = CREDENTIAL_ID_1,
    userId = "6037",
    email = Some(SensitiveString("test@mail.com")),
    lastLoginDate = Some("27 February 2022 at 12:00PM"),
    mfaDetails = Seq(MFADetails("mfaDetails.text", "24321"))
  )

  def getReportedAccountJson(accountDetails: AccountDetails, isWelsh: Boolean = false): JsObject = {
    val mfaJson = accountDetails.mfaDetails.length match {
      case 0 => Json.arr()
      case 1 =>
        Json.arr(
          Json.obj(
            "factorType"  -> getFactorType("text", isWelsh),
            "factorValue" -> getEndingWith("24321", isWelsh)
          )
        )
      case _ =>
        Json.arr(
          Json.obj(
            "factorType"  -> getFactorType("text", isWelsh),
            "factorValue" -> getEndingWith("24321", isWelsh)
          ),
          Json.obj(
            "factorType"  -> getFactorType("voice", isWelsh),
            "factorValue" -> getEndingWith("24322", isWelsh)
          ),
          Json.obj(
            "factorType"  -> getFactorType("totp", isWelsh),
            "factorValue" -> "TEST"
          )
        )
    }
    Json.obj(
      "credentialId" -> accountDetails.credId,
      "userId"       -> getEndingWith(accountDetails.userId, isWelsh),
      "email"        -> accountDetails.emailDecrypted.getOrElse("-").toString,
      "lastSignedIn" -> accountDetails.lastLoginDate,
      "mfaDetails"   -> mfaJson
    )
  }

  def getExpectedAuditEvent(
    reportedAccountDetails: AccountDetails,
    isSA: Boolean,
    isWelsh: Boolean = false
  ): AuditEvent = {
    val currentAccountDetails = Json.obj(
      ("credentialId", JsString(CREDENTIAL_ID)),
      (
        "type",
        JsString(if (isSA) {
          SA_ASSIGNED_TO_OTHER_USER.toString
        } else {
          PT_ASSIGNED_TO_OTHER_USER.toString
        })
      ),
      ("affinityGroup", JsString("Individual"))
    )

    val translatedAccountJson = if (isWelsh) {
      Json.obj(
        ("reportedAccountEN", getReportedAccountJson(reportedAccountDetails))
      )
    } else {
      Json.obj()
    }

    AuditEvent(
      auditType = "ReportUnrecognisedAccount",
      transactionName = if (isSA) {
        "reporting-unrecognised-sa-account"
      } else {
        "reporting-unrecognised-pt-account"
      },
      detail = Json.obj(
        ("NINO", JsString(NINO.nino)),
        ("currentAccount", currentAccountDetails),
        ("reportedAccount", getReportedAccountJson(reportedAccountDetails, isWelsh))
      ) ++ translatedAccountJson
    )
  }

  def getExpectedAuditForPTEnrolled(
    accountType: AccountTypes.Value,
    optReportedAccountDetails: Option[JsObject],
    optSACred: Option[String],
    withEmail: Option[String] = Some(CURRENT_USER_EMAIL)
  ): AuditEvent = {
    val email = if (withEmail.isDefined) Json.obj("email" -> withEmail.get) else Json.obj()
    val currentAccountDetails = Json
      .obj(
        ("credentialId", JsString(CREDENTIAL_ID)),
        ("type", JsString(accountType.toString)),
        ("affinityGroup", JsString("Individual"))
      )
      .deepMerge(email)

    val details = Json.obj(
      ("NINO", JsString(NINO.nino)),
      ("currentAccount", currentAccountDetails)
    ) ++ optSACred.fold(Json.obj())(credId =>
      Json.obj(("saAccountCredentialId", JsString(credId)))
    ) ++ optReportedAccountDetails.getOrElse(Json.obj())

    AuditEvent(
      auditType = "SuccessfullyEnrolledPersonalTax",
      transactionName = "successfully-enrolled-personal-tax",
      detail = details
    )
  }

  def getExpectedAuditForSigninWithSA(saAccountDetails: Option[JsObject]): AuditEvent = {
    val currentAccountDetails = Json.obj(
      ("credentialId", JsString(CREDENTIAL_ID)),
      ("type", JsString(SA_ASSIGNED_TO_OTHER_USER.toString)),
      ("affinityGroup", JsString("Individual"))
    )
    val details = Json.obj(
      ("NINO", JsString(NINO.nino)),
      ("currentAccount", currentAccountDetails)
    ) ++ saAccountDetails.getOrElse(Json.obj())

    AuditEvent(
      auditType = "SignInWithOtherAccount",
      transactionName = "sign-in-with-other-account",
      detail = details
    )
  }

  private def getFactorType(fType: String, isWelsh: Boolean): String =
    fType match {
      case "text" =>
        if (isWelsh) { "Neges destun" }
        else { "Text message" }
      case "voice" =>
        if (isWelsh) { "Rhif ffôn" }
        else { "Phone number" }
      case _ =>
        if (isWelsh) { "Ap dilysu" }
        else { "Authenticator app" }
    }

  private def getEndingWith(value: String, isWelsh: Boolean) =
    if (isWelsh) {
      s"yn gorffen gyda $value"
    } else {
      s"Ending with $value"
    }

  "auditReportSuspiciousSAAccount" should {
    val requestWithMongoAndAccountType =
      requestWithAccountType(SA_ASSIGNED_TO_OTHER_USER)
    val requestWithMongoAndAccountTypeLangCY =
      requestWithAccountType(SA_ASSIGNED_TO_OTHER_USER, langCode = "cy")

    "return an audit event with the expected details" when {
      "the reported account has email and one mfaDetails" in {
        val expectedAuditEvent =
          getExpectedAuditEvent(accountDetailsWithOneMFADetails, isSA = true)

        AuditEvent.auditReportSuspiciousSAAccount(
          accountDetailsWithOneMFADetails
        )(requestWithGivenMongoData(requestWithMongoAndAccountType), messagesApi) shouldEqual expectedAuditEvent
      }

      "the reported account has no email or mfaDetails" in {
        val accountDetailsNoEmailOrMFA = accountDetailsWithOneMFADetails
          .copy(email = None, mfaDetails = Seq.empty)
        val expectedAuditEvent =
          getExpectedAuditEvent(accountDetailsNoEmailOrMFA, isSA = true)

        AuditEvent.auditReportSuspiciousSAAccount(accountDetailsNoEmailOrMFA)(
          requestWithGivenMongoData(requestWithMongoAndAccountType),
          messagesApi
        ) shouldEqual expectedAuditEvent
      }

      "the reported account has no email" in {
        val accountDetailsNoEmail = accountDetailsWithOneMFADetails
          .copy(email = None)
        val expectedAuditEvent =
          getExpectedAuditEvent(accountDetailsNoEmail, isSA = true)

        AuditEvent.auditReportSuspiciousSAAccount(accountDetailsNoEmail)(
          requestWithGivenMongoData(requestWithMongoAndAccountType),
          messagesApi
        ) shouldEqual expectedAuditEvent
      }

      "the reported account has no mfaDetails" in {
        val accountDetailsNoMFA = accountDetailsWithOneMFADetails
          .copy(mfaDetails = Seq.empty)
        val expectedAuditEvent =
          getExpectedAuditEvent(accountDetailsNoMFA, isSA = true)

        AuditEvent.auditReportSuspiciousSAAccount(accountDetailsNoMFA)(
          requestWithGivenMongoData(requestWithMongoAndAccountType),
          messagesApi
        ) shouldEqual expectedAuditEvent
      }

      "the reported account has email and three mfaDetails" in {
        val accountDetailsWithThreeMFADetails = accountDetailsWithOneMFADetails
          .copy(
            mfaDetails = Seq(
              MFADetails("mfaDetails.text", "24321"),
              MFADetails("mfaDetails.voice", "24322"),
              MFADetails("mfaDetails.totp", "TEST")
            )
          )
        val expectedAuditEvent =
          getExpectedAuditEvent(accountDetailsWithThreeMFADetails, isSA = true)

        AuditEvent.auditReportSuspiciousSAAccount(
          accountDetailsWithThreeMFADetails
        )(requestWithGivenMongoData(requestWithMongoAndAccountType), messagesApi) shouldEqual expectedAuditEvent
      }
    }

    "return an audit event with the expected details and translation" when {
      "the reported account has email and one mfaDetails and lang is welsh" in {
        val expectedAuditEvent =
          getExpectedAuditEvent(accountDetailsWithOneMFADetails, isSA = true, isWelsh = true)

        AuditEvent.auditReportSuspiciousSAAccount(
          accountDetailsWithOneMFADetails
        )(requestWithGivenMongoData(requestWithMongoAndAccountTypeLangCY), messagesApi) shouldEqual expectedAuditEvent
      }

      "the reported account has no email or mfaDetails" in {
        val accountDetailsNoEmailOrMFA = accountDetailsWithOneMFADetails
          .copy(email = None, mfaDetails = Seq.empty)
        val expectedAuditEvent =
          getExpectedAuditEvent(accountDetailsNoEmailOrMFA, isSA = true, isWelsh = true)

        AuditEvent.auditReportSuspiciousSAAccount(accountDetailsNoEmailOrMFA)(
          requestWithGivenMongoData(requestWithMongoAndAccountTypeLangCY),
          messagesApi
        ) shouldEqual expectedAuditEvent
      }

      "the reported account has no email and lang is welsh" in {
        val accountDetailsNoEmail = accountDetailsWithOneMFADetails
          .copy(email = None)
        val expectedAuditEvent =
          getExpectedAuditEvent(accountDetailsNoEmail, isSA = true, isWelsh = true)

        AuditEvent.auditReportSuspiciousSAAccount(accountDetailsNoEmail)(
          requestWithGivenMongoData(requestWithMongoAndAccountTypeLangCY),
          messagesApi
        ) shouldEqual expectedAuditEvent
      }

      "the reported account has no mfaDetails and lang is welsh" in {
        val accountDetailsNoMFA = accountDetailsWithOneMFADetails
          .copy(mfaDetails = Seq.empty)
        val expectedAuditEvent =
          getExpectedAuditEvent(accountDetailsNoMFA, isSA = true, isWelsh = true)

        AuditEvent.auditReportSuspiciousSAAccount(accountDetailsNoMFA)(
          requestWithGivenMongoData(requestWithMongoAndAccountTypeLangCY),
          messagesApi
        ) shouldEqual expectedAuditEvent
      }

      "the reported account has email and three mfaDetails and lang is welsh" in {
        val accountDetailsWithThreeMFADetails = accountDetailsWithOneMFADetails
          .copy(
            mfaDetails = Seq(
              MFADetails("mfaDetails.text", "24321"),
              MFADetails("mfaDetails.voice", "24322"),
              MFADetails("mfaDetails.totp", "TEST")
            )
          )
        val expectedAuditEvent =
          getExpectedAuditEvent(accountDetailsWithThreeMFADetails, isSA = true, isWelsh = true)

        AuditEvent.auditReportSuspiciousSAAccount(
          accountDetailsWithThreeMFADetails
        )(requestWithGivenMongoData(requestWithMongoAndAccountTypeLangCY), messagesApi) shouldEqual expectedAuditEvent
      }
    }
  }

  "auditReportSuspiciousPTAccount" should {
    val requestWithMongoAndAccountType =
      requestWithAccountType(PT_ASSIGNED_TO_OTHER_USER)
    val requestWithMongoAndAccountTypeLangCY =
      requestWithAccountType(PT_ASSIGNED_TO_OTHER_USER, langCode = "cy")

    "return an audit event with the expected details" when {
      "the reported account has email and one mfaDetails" in {
        val expectedAuditEvent =
          getExpectedAuditEvent(accountDetailsWithOneMFADetails, isSA = false)

        AuditEvent.auditReportSuspiciousPTAccount(
          accountDetailsWithOneMFADetails
        )(requestWithGivenMongoData(requestWithMongoAndAccountType), messagesApi) shouldEqual expectedAuditEvent
      }

      "the reported account has no email or mfaDetails" in {
        val accountDetailsNoEmailOrMFA = accountDetailsWithOneMFADetails
          .copy(email = None, mfaDetails = Seq.empty)
        val expectedAuditEvent =
          getExpectedAuditEvent(accountDetailsNoEmailOrMFA, isSA = false)

        AuditEvent.auditReportSuspiciousPTAccount(accountDetailsNoEmailOrMFA)(
          requestWithGivenMongoData(requestWithMongoAndAccountType),
          messagesApi
        ) shouldEqual expectedAuditEvent
      }

      "the reported account has no email" in {
        val accountDetailsNoEmail = accountDetailsWithOneMFADetails
          .copy(email = None)
        val expectedAuditEvent =
          getExpectedAuditEvent(accountDetailsNoEmail, isSA = false)

        AuditEvent.auditReportSuspiciousPTAccount(accountDetailsNoEmail)(
          requestWithGivenMongoData(requestWithMongoAndAccountType),
          messagesApi
        ) shouldEqual expectedAuditEvent
      }

      "the reported account has no mfaDetails" in {
        val accountDetailsNoMFA = accountDetailsWithOneMFADetails
          .copy(mfaDetails = Seq.empty)
        val expectedAuditEvent =
          getExpectedAuditEvent(accountDetailsNoMFA, isSA = false)

        AuditEvent.auditReportSuspiciousPTAccount(accountDetailsNoMFA)(
          requestWithGivenMongoData(requestWithMongoAndAccountType),
          messagesApi
        ) shouldEqual expectedAuditEvent
      }

      "the reported account has email and three mfaDetails" in {
        val accountDetailsWithThreeMFADetails = accountDetailsWithOneMFADetails
          .copy(
            mfaDetails = Seq(
              MFADetails("mfaDetails.text", "24321"),
              MFADetails("mfaDetails.voice", "24322"),
              MFADetails("mfaDetails.totp", "TEST")
            )
          )
        val expectedAuditEvent =
          getExpectedAuditEvent(accountDetailsWithThreeMFADetails, isSA = false)

        AuditEvent.auditReportSuspiciousPTAccount(
          accountDetailsWithThreeMFADetails
        )(requestWithGivenMongoData(requestWithMongoAndAccountType), messagesApi) shouldEqual expectedAuditEvent
      }
    }

    "return an audit event with the expected details and translation" when {
      "the reported account has email and one mfaDetails and language set to welsh" in {
        val expectedAuditEvent =
          getExpectedAuditEvent(accountDetailsWithOneMFADetails, isSA = false, isWelsh = true)

        AuditEvent.auditReportSuspiciousPTAccount(
          accountDetailsWithOneMFADetails
        )(requestWithGivenMongoData(requestWithMongoAndAccountTypeLangCY), messagesApi) shouldEqual expectedAuditEvent
      }

      "the reported account has no email or mfaDetails and language set to welsh" in {
        val accountDetailsNoEmailOrMFA = accountDetailsWithOneMFADetails
          .copy(email = None, mfaDetails = Seq.empty)
        val expectedAuditEvent =
          getExpectedAuditEvent(accountDetailsNoEmailOrMFA, isSA = false, isWelsh = true)

        AuditEvent.auditReportSuspiciousPTAccount(accountDetailsNoEmailOrMFA)(
          requestWithGivenMongoData(requestWithMongoAndAccountTypeLangCY),
          messagesApi
        ) shouldEqual expectedAuditEvent
      }

      "the reported account has no email and language set to welsh" in {
        val accountDetailsNoEmail = accountDetailsWithOneMFADetails
          .copy(email = None)
        val expectedAuditEvent =
          getExpectedAuditEvent(accountDetailsNoEmail, isSA = false, isWelsh = true)

        AuditEvent.auditReportSuspiciousPTAccount(accountDetailsNoEmail)(
          requestWithGivenMongoData(requestWithMongoAndAccountTypeLangCY),
          messagesApi
        ) shouldEqual expectedAuditEvent
      }

      "the reported account has no mfaDetails and language set to welsh" in {
        val accountDetailsNoMFA = accountDetailsWithOneMFADetails
          .copy(mfaDetails = Seq.empty)
        val expectedAuditEvent =
          getExpectedAuditEvent(accountDetailsNoMFA, isSA = false, isWelsh = true)

        AuditEvent.auditReportSuspiciousPTAccount(accountDetailsNoMFA)(
          requestWithGivenMongoData(requestWithMongoAndAccountTypeLangCY),
          messagesApi
        ) shouldEqual expectedAuditEvent
      }

      "the reported account has email and three mfaDetails and language set to welsh" in {
        val accountDetailsWithThreeMFADetails = accountDetailsWithOneMFADetails
          .copy(
            mfaDetails = Seq(
              MFADetails("mfaDetails.text", "24321"),
              MFADetails("mfaDetails.voice", "24322"),
              MFADetails("mfaDetails.totp", "TEST")
            )
          )

        val expectedAuditEvent =
          getExpectedAuditEvent(accountDetailsWithThreeMFADetails, isSA = false, isWelsh = true)

        AuditEvent.auditReportSuspiciousPTAccount(
          accountDetailsWithThreeMFADetails
        )(requestWithGivenMongoData(requestWithMongoAndAccountTypeLangCY), messagesApi) shouldEqual expectedAuditEvent
      }
    }
  }

  "auditSuccessfullyEnrolledPTWhenSANotOnOtherAccount" when {

    "email does not exist in user details session" should {
      "return an audit that does not contain the email" in {
        val requestForAudit =
          requestWithUserDetails(userDetailsNoEnrolments.copy(email = None))

        val expectedAuditEvent =
          getExpectedAuditForPTEnrolled(SINGLE_OR_MULTIPLE_ACCOUNTS, None, None, withEmail = Some("-"))

        AuditEvent.auditSuccessfullyEnrolledPTWhenSANotOnOtherAccount(
          SINGLE_OR_MULTIPLE_ACCOUNTS
        )(requestForAudit, messagesApi) shouldEqual expectedAuditEvent

      }
    }

    "the user has a single account with no SA" should {
      "return an audit event with the expected details" in {
        val requestForAudit =
          requestWithUserDetails(userDetailsNoEnrolments)

        val expectedAuditEvent =
          getExpectedAuditForPTEnrolled(SINGLE_OR_MULTIPLE_ACCOUNTS, None, None)

        AuditEvent.auditSuccessfullyEnrolledPTWhenSANotOnOtherAccount(
          SINGLE_OR_MULTIPLE_ACCOUNTS
        )(requestForAudit, messagesApi) shouldEqual expectedAuditEvent
      }
    }

    "the user has a single account with SA" should {
      "return an audit event with the expected details" in {
        val requestForAudit =
          requestWithUserDetails(userDetailsWithSAEnrolment)

        val expectedAuditEvent =
          getExpectedAuditForPTEnrolled(SINGLE_OR_MULTIPLE_ACCOUNTS, None, Some(CREDENTIAL_ID))

        AuditEvent.auditSuccessfullyEnrolledPTWhenSANotOnOtherAccount(
          SINGLE_OR_MULTIPLE_ACCOUNTS
        )(requestForAudit, messagesApi) shouldEqual expectedAuditEvent
      }
    }

    "the user has multiple accounts with no SA" should {
      "return an audit event with the expected details" in {
        val requestForAudit =
          requestWithUserDetails(userDetailsNoEnrolments)

        val expectedAuditEvent =
          getExpectedAuditForPTEnrolled(SINGLE_OR_MULTIPLE_ACCOUNTS, None, None)

        AuditEvent.auditSuccessfullyEnrolledPTWhenSANotOnOtherAccount(
          SINGLE_OR_MULTIPLE_ACCOUNTS
        )(requestForAudit, messagesApi) shouldEqual expectedAuditEvent
      }
    }

    "the user has multiple accounts and is signed in with SA account" should {
      "return an audit event with the expected details" in {
        val requestForAudit =
          requestWithUserDetails(userDetailsWithSAEnrolment)

        val expectedAuditEvent =
          getExpectedAuditForPTEnrolled(SA_ASSIGNED_TO_CURRENT_USER, None, Some(CREDENTIAL_ID))

        AuditEvent.auditSuccessfullyEnrolledPTWhenSANotOnOtherAccount(
          SA_ASSIGNED_TO_CURRENT_USER
        )(requestForAudit, messagesApi) shouldEqual expectedAuditEvent
      }
    }
  }

  "auditSuccessfullyEnrolledPTWhenSAOnOtherAccount" when {
    "email does not exist in user details session" should {
      "return an audit that does not contain the email" in {
        val requestForAudit =
          RequestWithUserDetailsFromSessionAndMongo(
            request.request.withTransientLang("en"),
            request.userDetails.copy(email = None),
            request.sessionID,
            AccountDetailsFromMongo(
              SA_ASSIGNED_TO_OTHER_USER,
              "foo",
              None,
              None,
              Some(UsersAssignedEnrolment1),
              None
            )
          )

        val mockUserAnswers = UserAnswers("id", generateNino.nino)
          .setOrException(UserAssignedSaEnrolmentPage, UsersAssignedEnrolment1)
          .setOrException(AccountDetailsForCredentialPage(CREDENTIAL_ID_1), accountDetailsSA)(
            AccountDetails.mongoFormats(crypto.crypto)
          )

        val expectedAuditEvent =
          getExpectedAuditForPTEnrolled(SA_ASSIGNED_TO_OTHER_USER, None, Some(CREDENTIAL_ID_1), withEmail = Some("-"))

        AuditEvent.auditSuccessfullyEnrolledPTWhenSAOnOtherAccount(enrolledAfterReportingFraud = false)(
          requestWithGivenMongoDataAndUserAnswers(requestForAudit, mockUserAnswers),
          messagesApi,
          crypto
        ) shouldEqual expectedAuditEvent
      }
    }
    "the user has enrolled after choosing to keep PT and SA separate" should {
      "return an audit event with the expected details" in {

        val mockUserAnswers = UserAnswers("id", generateNino.nino)
          .setOrException(UserAssignedSaEnrolmentPage, UsersAssignedEnrolment1)
          .setOrException(AccountDetailsForCredentialPage(CREDENTIAL_ID_1), accountDetailsSA)(
            AccountDetails.mongoFormats(crypto.crypto)
          )

        val requestForAudit =
          requestWithAccountType(SA_ASSIGNED_TO_OTHER_USER)
        val expectedAuditEvent =
          getExpectedAuditForPTEnrolled(SA_ASSIGNED_TO_OTHER_USER, None, Some(CREDENTIAL_ID_1))

        AuditEvent.auditSuccessfullyEnrolledPTWhenSAOnOtherAccount(
          enrolledAfterReportingFraud = false
        )(
          requestWithGivenMongoDataAndUserAnswers(requestForAudit, mockUserAnswers),
          messagesApi,
          crypto
        ) shouldEqual expectedAuditEvent
      }
    }

    "the user has enrolled after reporting fraud" should {
      "return an audit event with the expected details" when {
        "the reported account has no email or mfaDetails" in {
          val accountDetailsNoEmailOrMFA = accountDetailsWithOneMFADetails
            .copy(email = None, mfaDetails = Seq.empty)
          val reportedAccountDetails =
            Json.obj(("reportedAccount", getReportedAccountJson(accountDetailsNoEmailOrMFA)))
          val expectedAuditEvent =
            getExpectedAuditForPTEnrolled(
              SA_ASSIGNED_TO_OTHER_USER,
              Some(reportedAccountDetails),
              Some(CREDENTIAL_ID_1)
            )

          val mockUserAnswers = UserAnswers("id", generateNino.nino)
            .setOrException(UserAssignedSaEnrolmentPage, UsersAssignedEnrolment1)
            .setOrException(AccountDetailsForCredentialPage(CREDENTIAL_ID_1), accountDetailsNoEmailOrMFA)(
              AccountDetails.mongoFormats(crypto.crypto)
            )

          val requestForAudit =
            requestWithAccountType(SA_ASSIGNED_TO_OTHER_USER)

          AuditEvent.auditSuccessfullyEnrolledPTWhenSAOnOtherAccount(enrolledAfterReportingFraud = true)(
            requestWithGivenMongoDataAndUserAnswers(requestForAudit, mockUserAnswers),
            messagesApi,
            crypto
          ) shouldEqual expectedAuditEvent
        }

        "the reported account has no email" in {
          val accountDetailsNoEmail = accountDetailsWithOneMFADetails
            .copy(email = None)
          val reportedAccountDetails =
            Json.obj(("reportedAccount", getReportedAccountJson(accountDetailsNoEmail)))
          val expectedAuditEvent =
            getExpectedAuditForPTEnrolled(
              SA_ASSIGNED_TO_OTHER_USER,
              Some(reportedAccountDetails),
              Some(CREDENTIAL_ID_1)
            )

          val mockUserAnswers = UserAnswers("id", generateNino.nino)
            .setOrException(UserAssignedSaEnrolmentPage, UsersAssignedEnrolment1)
            .setOrException(AccountDetailsForCredentialPage(CREDENTIAL_ID_1), accountDetailsNoEmail)(
              AccountDetails.mongoFormats(crypto.crypto)
            )

          val requestForAudit =
            requestWithAccountType(SA_ASSIGNED_TO_OTHER_USER)

          AuditEvent.auditSuccessfullyEnrolledPTWhenSAOnOtherAccount(enrolledAfterReportingFraud = true)(
            requestWithGivenMongoDataAndUserAnswers(requestForAudit, mockUserAnswers),
            messagesApi,
            crypto
          ) shouldEqual expectedAuditEvent
        }

        "the reported account has no mfaDetails" in {
          val accountDetailsNoMFA = accountDetailsWithOneMFADetails
            .copy(mfaDetails = Seq.empty)

          val reportedAccountDetails = Json.obj(("reportedAccount", getReportedAccountJson(accountDetailsNoMFA)))
          val expectedAuditEvent =
            getExpectedAuditForPTEnrolled(
              SA_ASSIGNED_TO_OTHER_USER,
              Some(reportedAccountDetails),
              Some(CREDENTIAL_ID_1)
            )

          val mockUserAnswers = UserAnswers("id", generateNino.nino)
            .setOrException(UserAssignedSaEnrolmentPage, UsersAssignedEnrolment1)
            .setOrException(AccountDetailsForCredentialPage(CREDENTIAL_ID_1), accountDetailsNoMFA)(
              AccountDetails.mongoFormats(crypto.crypto)
            )

          val requestForAudit =
            requestWithAccountType(SA_ASSIGNED_TO_OTHER_USER)

          AuditEvent.auditSuccessfullyEnrolledPTWhenSAOnOtherAccount(enrolledAfterReportingFraud = true)(
            requestWithGivenMongoDataAndUserAnswers(requestForAudit, mockUserAnswers),
            messagesApi,
            crypto
          ) shouldEqual expectedAuditEvent
        }

        "the reported account has email and three mfaDetails" in {
          val accountDetailsWithThreeMFADetails = accountDetailsWithOneMFADetails
            .copy(
              mfaDetails = Seq(
                MFADetails("mfaDetails.text", "24321"),
                MFADetails("mfaDetails.voice", "24322"),
                MFADetails("mfaDetails.totp", "TEST")
              )
            )
          val reportedAccountDetails =
            Json.obj(("reportedAccount", getReportedAccountJson(accountDetailsWithThreeMFADetails)))
          val expectedAuditEvent =
            getExpectedAuditForPTEnrolled(
              SA_ASSIGNED_TO_OTHER_USER,
              Some(reportedAccountDetails),
              Some(CREDENTIAL_ID_1)
            )

          val mockUserAnswers = UserAnswers("id", generateNino.nino)
            .setOrException(UserAssignedSaEnrolmentPage, UsersAssignedEnrolment1)
            .setOrException(AccountDetailsForCredentialPage(CREDENTIAL_ID_1), accountDetailsWithThreeMFADetails)(
              AccountDetails.mongoFormats(crypto.crypto)
            )

          val requestForAudit =
            requestWithAccountType(SA_ASSIGNED_TO_OTHER_USER)

          AuditEvent.auditSuccessfullyEnrolledPTWhenSAOnOtherAccount(enrolledAfterReportingFraud = true)(
            requestWithGivenMongoDataAndUserAnswers(requestForAudit, mockUserAnswers),
            messagesApi,
            crypto
          ) shouldEqual expectedAuditEvent
        }
      }
    }

    "the user has enrolled after reporting fraud and has language set to welsh" should {
      "return an audit event with the expected details and translation for reported account" when {
        "the reported account has no email or mfaDetails" in {
          val accountDetailsNoEmailOrMFA = accountDetailsWithOneMFADetails
            .copy(email = None, mfaDetails = Seq.empty)
          val reportedAccountDetails =
            Json.obj(
              ("reportedAccount", getReportedAccountJson(accountDetailsNoEmailOrMFA, isWelsh = true)),
              ("reportedAccountEN", getReportedAccountJson(accountDetailsNoEmailOrMFA))
            )
          val expectedAuditEvent =
            getExpectedAuditForPTEnrolled(
              SA_ASSIGNED_TO_OTHER_USER,
              Some(reportedAccountDetails),
              Some(CREDENTIAL_ID_1)
            )

          val mockUserAnswers = UserAnswers("id", generateNino.nino)
            .setOrException(UserAssignedSaEnrolmentPage, UsersAssignedEnrolment1)
            .setOrException(AccountDetailsForCredentialPage(CREDENTIAL_ID_1), accountDetailsNoEmailOrMFA)(
              AccountDetails.mongoFormats(crypto.crypto)
            )

          val requestForAudit =
            requestWithAccountType(
              SA_ASSIGNED_TO_OTHER_USER,
              langCode = "cy"
            )

          AuditEvent.auditSuccessfullyEnrolledPTWhenSAOnOtherAccount(enrolledAfterReportingFraud = true)(
            requestWithGivenMongoDataAndUserAnswers(requestForAudit, mockUserAnswers),
            messagesApi,
            crypto
          ) shouldEqual expectedAuditEvent
        }

        "the reported account has no email" in {
          val accountDetailsNoEmail = accountDetailsWithOneMFADetails
            .copy(email = None)
          val reportedAccountDetails =
            Json.obj(
              ("reportedAccount", getReportedAccountJson(accountDetailsNoEmail, isWelsh = true)),
              ("reportedAccountEN", getReportedAccountJson(accountDetailsNoEmail))
            )
          val expectedAuditEvent =
            getExpectedAuditForPTEnrolled(
              SA_ASSIGNED_TO_OTHER_USER,
              Some(reportedAccountDetails),
              Some(CREDENTIAL_ID_1)
            )

          val mockUserAnswers = UserAnswers("id", generateNino.nino)
            .setOrException(UserAssignedSaEnrolmentPage, UsersAssignedEnrolment1)
            .setOrException(AccountDetailsForCredentialPage(CREDENTIAL_ID_1), accountDetailsNoEmail)(
              AccountDetails.mongoFormats(crypto.crypto)
            )

          val requestForAudit =
            requestWithAccountType(
              SA_ASSIGNED_TO_OTHER_USER,
              langCode = "cy"
            )

          AuditEvent.auditSuccessfullyEnrolledPTWhenSAOnOtherAccount(enrolledAfterReportingFraud = true)(
            requestWithGivenMongoDataAndUserAnswers(requestForAudit, mockUserAnswers),
            messagesApi,
            crypto
          ) shouldEqual expectedAuditEvent
        }

        "the reported account has no mfaDetails" in {
          val accountDetailsNoMFA = accountDetailsWithOneMFADetails
            .copy(mfaDetails = Seq.empty)
          val reportedAccountDetails = Json.obj(
            ("reportedAccount", getReportedAccountJson(accountDetailsNoMFA, isWelsh = true)),
            ("reportedAccountEN", getReportedAccountJson(accountDetailsNoMFA))
          )
          val expectedAuditEvent =
            getExpectedAuditForPTEnrolled(
              SA_ASSIGNED_TO_OTHER_USER,
              Some(reportedAccountDetails),
              Some(CREDENTIAL_ID_1)
            )

          val mockUserAnswers = UserAnswers("id", generateNino.nino)
            .setOrException(UserAssignedSaEnrolmentPage, UsersAssignedEnrolment1)
            .setOrException(AccountDetailsForCredentialPage(CREDENTIAL_ID_1), accountDetailsNoMFA)(
              AccountDetails.mongoFormats(crypto.crypto)
            )

          val requestForAudit =
            requestWithAccountType(
              SA_ASSIGNED_TO_OTHER_USER,
              langCode = "cy"
            )

          AuditEvent.auditSuccessfullyEnrolledPTWhenSAOnOtherAccount(enrolledAfterReportingFraud = true)(
            requestWithGivenMongoDataAndUserAnswers(requestForAudit, mockUserAnswers),
            messagesApi,
            crypto
          ) shouldEqual expectedAuditEvent
        }

        "the reported account has email and three mfaDetails" in {
          val accountDetailsWithThreeMFADetails = accountDetailsWithOneMFADetails
            .copy(
              mfaDetails = Seq(
                MFADetails("mfaDetails.text", "24321"),
                MFADetails("mfaDetails.voice", "24322"),
                MFADetails("mfaDetails.totp", "TEST")
              )
            )
          val reportedAccountDetails =
            Json.obj(
              ("reportedAccount", getReportedAccountJson(accountDetailsWithThreeMFADetails, isWelsh = true)),
              ("reportedAccountEN", getReportedAccountJson(accountDetailsWithThreeMFADetails))
            )
          val expectedAuditEvent =
            getExpectedAuditForPTEnrolled(
              SA_ASSIGNED_TO_OTHER_USER,
              Some(reportedAccountDetails),
              Some(CREDENTIAL_ID_1)
            )

          val mockUserAnswers = UserAnswers("id", generateNino.nino)
            .setOrException(UserAssignedSaEnrolmentPage, UsersAssignedEnrolment1)
            .setOrException(AccountDetailsForCredentialPage(CREDENTIAL_ID_1), accountDetailsWithThreeMFADetails)(
              AccountDetails.mongoFormats(crypto.crypto)
            )

          val requestForAudit =
            requestWithAccountType(
              SA_ASSIGNED_TO_OTHER_USER,
              langCode = "cy"
            )

          AuditEvent.auditSuccessfullyEnrolledPTWhenSAOnOtherAccount(enrolledAfterReportingFraud = true)(
            requestWithGivenMongoDataAndUserAnswers(requestForAudit, mockUserAnswers),
            messagesApi,
            crypto
          ) shouldEqual expectedAuditEvent
        }
      }
    }
  }

  "auditSigninAgainWithSACredential" should {
    "return an audit event with the expected details and no translation" when {
      "the sa account has no email or mfaDetails" in {
        val accountDetailsNoEmailOrMFA = accountDetailsWithOneMFADetails
          .copy(email = None, mfaDetails = Seq.empty)
        val saAccountDetails =
          Json.obj(("saAccount", getReportedAccountJson(accountDetailsNoEmailOrMFA)))
        val expectedAuditEvent =
          getExpectedAuditForSigninWithSA(Some(saAccountDetails))

        val mockUserAnswers = UserAnswers("id", generateNino.nino)
          .setOrException(UserAssignedSaEnrolmentPage, UsersAssignedEnrolment1)
          .setOrException(AccountDetailsForCredentialPage(CREDENTIAL_ID_1), accountDetailsNoEmailOrMFA)(
            AccountDetails.mongoFormats(crypto.crypto)
          )

        val requestForAudit =
          requestWithAccountType(SA_ASSIGNED_TO_OTHER_USER)

        AuditEvent.auditSigninAgainWithSACredential()(
          requestWithGivenMongoDataAndUserAnswers(requestForAudit, mockUserAnswers),
          messagesApi,
          crypto
        ) shouldEqual expectedAuditEvent
      }

      "the reported account has no email" in {
        val accountDetailsNoEmail = accountDetailsWithOneMFADetails
          .copy(email = None)
        val saAccountDetails =
          Json.obj(("saAccount", getReportedAccountJson(accountDetailsNoEmail)))
        val expectedAuditEvent =
          getExpectedAuditForSigninWithSA(Some(saAccountDetails))

        val mockUserAnswers = UserAnswers("id", generateNino.nino)
          .setOrException(UserAssignedSaEnrolmentPage, UsersAssignedEnrolment1)
          .setOrException(AccountDetailsForCredentialPage(CREDENTIAL_ID_1), accountDetailsNoEmail)(
            AccountDetails.mongoFormats(crypto.crypto)
          )

        val requestForAudit =
          requestWithAccountType(SA_ASSIGNED_TO_OTHER_USER)

        AuditEvent.auditSigninAgainWithSACredential()(
          requestWithGivenMongoDataAndUserAnswers(requestForAudit, mockUserAnswers),
          messagesApi,
          crypto
        ) shouldEqual expectedAuditEvent
      }

      "the reported account has no mfaDetails" in {
        val accountDetailsNoMFA = accountDetailsWithOneMFADetails
          .copy(mfaDetails = Seq.empty)
        val saAccountDetails = Json.obj(("saAccount", getReportedAccountJson(accountDetailsNoMFA)))
        val expectedAuditEvent =
          getExpectedAuditForSigninWithSA(Some(saAccountDetails))
        val requestForAudit =
          requestWithAccountType(SA_ASSIGNED_TO_OTHER_USER)

        val mockUserAnswers = UserAnswers("id", generateNino.nino)
          .setOrException(UserAssignedSaEnrolmentPage, UsersAssignedEnrolment1)
          .setOrException(AccountDetailsForCredentialPage(CREDENTIAL_ID_1), accountDetailsNoMFA)(
            AccountDetails.mongoFormats(crypto.crypto)
          )

        AuditEvent.auditSigninAgainWithSACredential()(
          requestWithGivenMongoDataAndUserAnswers(requestForAudit, mockUserAnswers),
          messagesApi,
          crypto
        ) shouldEqual expectedAuditEvent
      }

      "the reported account has email and three mfaDetails" in {
        val accountDetailsWithThreeMFADetails = accountDetailsWithOneMFADetails
          .copy(
            mfaDetails = Seq(
              MFADetails("mfaDetails.text", "24321"),
              MFADetails("mfaDetails.voice", "24322"),
              MFADetails("mfaDetails.totp", "TEST")
            )
          )
        val saAccountDetails =
          Json.obj(("saAccount", getReportedAccountJson(accountDetailsWithThreeMFADetails)))
        val expectedAuditEvent =
          getExpectedAuditForSigninWithSA(Some(saAccountDetails))

        val mockUserAnswers = UserAnswers("id", generateNino.nino)
          .setOrException(UserAssignedSaEnrolmentPage, UsersAssignedEnrolment1)
          .setOrException(AccountDetailsForCredentialPage(CREDENTIAL_ID_1), accountDetailsWithThreeMFADetails)(
            AccountDetails.mongoFormats(crypto.crypto)
          )

        val requestForAudit =
          requestWithAccountType(SA_ASSIGNED_TO_OTHER_USER)

        AuditEvent.auditSigninAgainWithSACredential()(
          requestWithGivenMongoDataAndUserAnswers(requestForAudit, mockUserAnswers),
          messagesApi,
          crypto
        ) shouldEqual expectedAuditEvent
      }

      "return no saAccountDetails if not in sessionCache" in {
        val expectedAuditEvent =
          getExpectedAuditForSigninWithSA(None)

        val requestForAudit =
          requestWithAccountType(SA_ASSIGNED_TO_OTHER_USER)

        AuditEvent.auditSigninAgainWithSACredential()(
          requestWithGivenMongoData(requestForAudit),
          messagesApi,
          crypto
        ) shouldEqual expectedAuditEvent
      }
    }

    "return an audit event with the expected details that includes translation" when {
      "the sa account has no email or mfaDetails and the page was displayed in welsh" in {
        val accountDetailsNoEmailOrMFA = accountDetailsWithOneMFADetails
          .copy(email = None, mfaDetails = Seq.empty)
        val saAccountDetails =
          Json.obj(
            ("saAccount", getReportedAccountJson(accountDetailsNoEmailOrMFA, isWelsh = true)),
            ("saAccountEN", getReportedAccountJson(accountDetailsNoEmailOrMFA))
          )
        val expectedAuditEvent =
          getExpectedAuditForSigninWithSA(Some(saAccountDetails))

        val mockUserAnswers = UserAnswers("id", generateNino.nino)
          .setOrException(UserAssignedSaEnrolmentPage, UsersAssignedEnrolment1)
          .setOrException(AccountDetailsForCredentialPage(CREDENTIAL_ID_1), accountDetailsNoEmailOrMFA)(
            AccountDetails.mongoFormats(crypto.crypto)
          )

        val requestForAudit =
          requestWithAccountType(SA_ASSIGNED_TO_OTHER_USER, langCode = "cy")

        AuditEvent.auditSigninAgainWithSACredential()(
          requestWithGivenMongoDataAndUserAnswers(requestForAudit, mockUserAnswers),
          messagesApi,
          crypto
        ) shouldEqual expectedAuditEvent
      }

      "the reported account has no email" in {
        val accountDetailsNoEmail = accountDetailsWithOneMFADetails
          .copy(email = None)
        val saAccountDetails =
          Json.obj(
            ("saAccount", getReportedAccountJson(accountDetailsNoEmail, isWelsh = true)),
            ("saAccountEN", getReportedAccountJson(accountDetailsNoEmail))
          )
        val expectedAuditEvent =
          getExpectedAuditForSigninWithSA(Some(saAccountDetails))

        val mockUserAnswers = UserAnswers("id", generateNino.nino)
          .setOrException(UserAssignedSaEnrolmentPage, UsersAssignedEnrolment1)
          .setOrException(AccountDetailsForCredentialPage(CREDENTIAL_ID_1), accountDetailsNoEmail)(
            AccountDetails.mongoFormats(crypto.crypto)
          )

        val requestForAudit =
          requestWithAccountType(SA_ASSIGNED_TO_OTHER_USER, langCode = "cy")

        AuditEvent.auditSigninAgainWithSACredential()(
          requestWithGivenMongoDataAndUserAnswers(requestForAudit, mockUserAnswers),
          messagesApi,
          crypto
        ) shouldEqual expectedAuditEvent
      }

      "the reported account has no mfaDetails" in {
        val accountDetailsNoMFA = accountDetailsWithOneMFADetails
          .copy(mfaDetails = Seq.empty)
        val saAccountDetails =
          Json.obj(
            ("saAccount", getReportedAccountJson(accountDetailsNoMFA, isWelsh = true)),
            ("saAccountEN", getReportedAccountJson(accountDetailsNoMFA))
          )
        val expectedAuditEvent =
          getExpectedAuditForSigninWithSA(Some(saAccountDetails))

        val mockUserAnswers = UserAnswers("id", generateNino.nino)
          .setOrException(UserAssignedSaEnrolmentPage, UsersAssignedEnrolment1)
          .setOrException(AccountDetailsForCredentialPage(CREDENTIAL_ID_1), accountDetailsNoMFA)(
            AccountDetails.mongoFormats(crypto.crypto)
          )

        val requestForAudit =
          requestWithAccountType(SA_ASSIGNED_TO_OTHER_USER, langCode = "cy")

        AuditEvent.auditSigninAgainWithSACredential()(
          requestWithGivenMongoDataAndUserAnswers(requestForAudit, mockUserAnswers),
          messagesApi,
          crypto
        ) shouldEqual expectedAuditEvent
      }

      "the reported account has email and three mfaDetails" in {

        val accountDetailsWithThreeMFADetails = accountDetailsWithOneMFADetails
          .copy(
            mfaDetails = Seq(
              MFADetails("mfaDetails.text", "24321"),
              MFADetails("mfaDetails.voice", "24322"),
              MFADetails("mfaDetails.totp", "TEST")
            )
          )

        val mockUserAnswers = UserAnswers("id", generateNino.nino)
          .setOrException(UserAssignedSaEnrolmentPage, UsersAssignedEnrolment1)
          .setOrException(AccountDetailsForCredentialPage(CREDENTIAL_ID_1), accountDetailsWithThreeMFADetails)(
            AccountDetails.mongoFormats(crypto.crypto)
          )

        val saAccountDetails =
          Json.obj(
            ("saAccount", getReportedAccountJson(accountDetailsWithThreeMFADetails, isWelsh = true)),
            ("saAccountEN", getReportedAccountJson(accountDetailsWithThreeMFADetails))
          )
        val expectedAuditEvent =
          getExpectedAuditForSigninWithSA(Some(saAccountDetails))

        val requestForAudit =
          requestWithAccountType(SA_ASSIGNED_TO_OTHER_USER, langCode = "cy")

        AuditEvent.auditSigninAgainWithSACredential()(
          requestWithGivenMongoDataAndUserAnswers(requestForAudit, mockUserAnswers),
          messagesApi,
          crypto
        ) shouldEqual expectedAuditEvent
      }

      "return no saAccountDetails if not in sessionCache" in {
        val expectedAuditEvent =
          getExpectedAuditForSigninWithSA(None)

        val requestForAudit =
          requestWithAccountType(SA_ASSIGNED_TO_OTHER_USER, langCode = "cy")

        AuditEvent.auditSigninAgainWithSACredential()(
          requestWithGivenMongoData(requestForAudit),
          messagesApi,
          crypto
        ) shouldEqual expectedAuditEvent
      }
    }
  }
}
