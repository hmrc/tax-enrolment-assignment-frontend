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

import play.api.libs.json.{JsObject, JsString, Json}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes.{
  PT_ASSIGNED_TO_OTHER_USER,
  SA_ASSIGNED_TO_OTHER_USER
}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.TestData._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.TestFixture
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.{
  AccountDetails,
  MFADetails
}

class AuditEventSpec extends TestFixture {

  val accountDetailsWithOneMFADetails = AccountDetails(
    credId = CREDENTIAL_ID_1,
    userId = "6037",
    email = Some("test@mail.com"),
    lastLoginDate = "27 February 2022 at 12:00 PM",
    mfaDetails = Seq(MFADetails("mfaDetails.text", "24321"))
  )

  def getReportedAccountJson(accountDetails: AccountDetails): JsObject = {
    val mfaJson = accountDetails.mfaDetails.length match {
      case 0 => Json.arr()
      case 1 =>
        Json.arr(
          Json.obj(
            ("factorType", JsString("Text message")),
            ("factorValue", JsString(s"Ending with 24321"))
          )
        )
      case _ =>
        Json.arr(
          Json.obj(
            ("factorType", JsString("Text message")),
            ("factorValue", JsString(s"Ending with 24321"))
          ),
          Json.obj(
            ("factorType", JsString("Phone number")),
            ("factorValue", JsString(s"Ending with 24322"))
          ),
          Json.obj(
            ("factorType", JsString("Authenticator app")),
            ("factorValue", JsString("TEST"))
          )
        )
    }
    Json.obj(
      ("credentialId", JsString(accountDetails.credId)),
      ("userId", JsString(s"Ending with ${accountDetails.userId}")),
      ("email", JsString(accountDetails.email.getOrElse("-"))),
      ("lastSignedIn", JsString(accountDetails.lastLoginDate)),
      ("mfaDetails", mfaJson)
    )
  }

  def getExpectedAuditEvent(reportedAccountDetails: JsObject,
                            isSA: Boolean): AuditEvent = {
    val currentAccountDetails = Json.obj(
      ("credentialId", JsString(CREDENTIAL_ID)),
      ("type", JsString(if (isSA) { SA_ASSIGNED_TO_OTHER_USER.toString } else {
        PT_ASSIGNED_TO_OTHER_USER.toString
      })),
      ("affinityGroup", JsString("Individual"))
    )
    AuditEvent(
      auditType = "ReportUnrecognisedAccount",
      transactionName = if (isSA) {
        "reporting-unrecognised-sa-account"
      } else {
        "reporting-unrecognised-pt-account"
      },
      detail = Json.obj(
        ("NINO", JsString(NINO)),
        ("currentAccount", currentAccountDetails),
        ("reportedAccount", reportedAccountDetails)
      )
    )
  }

  "auditReportSuspiciousSAAccount" when {
    val requestWithMongoAndAccountType =
      requestWithAccountType(SA_ASSIGNED_TO_OTHER_USER)

    "the reported account has email and one mfaDetails" should {
      "return an audit event with the expected details" in {

        val reportedAccountDetails =
          getReportedAccountJson(accountDetailsWithOneMFADetails)
        val expectedAuditEvent =
          getExpectedAuditEvent(reportedAccountDetails, true)

        AuditEvent.auditReportSuspiciousSAAccount(
          accountDetailsWithOneMFADetails
        )(requestWithMongoAndAccountType) shouldEqual expectedAuditEvent
      }
    }

    "the reported account has no email or mfaDetails" should {
      "return an audit event with the expected details" in {
        val accountDetailsNoEmailOrMFA = accountDetailsWithOneMFADetails
          .copy(email = None, mfaDetails = Seq.empty)
        val reportedAccountDetails =
          getReportedAccountJson(accountDetailsNoEmailOrMFA)
        val expectedAuditEvent =
          getExpectedAuditEvent(reportedAccountDetails, true)

        AuditEvent.auditReportSuspiciousSAAccount(accountDetailsNoEmailOrMFA)(
          requestWithMongoAndAccountType
        ) shouldEqual expectedAuditEvent
      }
    }

    "the reported account has no email" should {
      "return an audit event with the expected details" in {
        val accountDetailsNoEmail = accountDetailsWithOneMFADetails
          .copy(email = None)
        val reportedAccountDetails =
          getReportedAccountJson(accountDetailsNoEmail)
        val expectedAuditEvent =
          getExpectedAuditEvent(reportedAccountDetails, true)

        AuditEvent.auditReportSuspiciousSAAccount(accountDetailsNoEmail)(
          requestWithMongoAndAccountType
        ) shouldEqual expectedAuditEvent
      }
    }

    "the reported account has no mfaDetails" should {
      "return an audit event with the expected details" in {
        val accountDetailsNoMFA = accountDetailsWithOneMFADetails
          .copy(mfaDetails = Seq.empty)
        val reportedAccountDetails = getReportedAccountJson(accountDetailsNoMFA)
        val expectedAuditEvent =
          getExpectedAuditEvent(reportedAccountDetails, true)

        AuditEvent.auditReportSuspiciousSAAccount(accountDetailsNoMFA)(
          requestWithMongoAndAccountType
        ) shouldEqual expectedAuditEvent
      }
    }

    "the reported account has email and three mfaDetails" should {
      "return an audit event with the expected details" in {
        val accountDetailsWithThreeMFADetails = accountDetailsWithOneMFADetails
          .copy(
            mfaDetails = Seq(
              MFADetails("mfaDetails.text", "24321"),
              MFADetails("mfaDetails.voice", "24322"),
              MFADetails("mfaDetails.totp", "TEST")
            )
          )
        val reportedAccountDetails =
          getReportedAccountJson(accountDetailsWithThreeMFADetails)
        val expectedAuditEvent =
          getExpectedAuditEvent(reportedAccountDetails, true)

        AuditEvent.auditReportSuspiciousSAAccount(
          accountDetailsWithThreeMFADetails
        )(requestWithMongoAndAccountType) shouldEqual expectedAuditEvent
      }
    }
  }

  "auditReportSuspiciousPTAccount" when {
    val requestWithMongoAndAccountType =
      requestWithAccountType(PT_ASSIGNED_TO_OTHER_USER)

    "the reported account has email and one mfaDetails" should {
      "return an audit event with the expected details" in {

        val reportedAccountDetails =
          getReportedAccountJson(accountDetailsWithOneMFADetails)
        val expectedAuditEvent =
          getExpectedAuditEvent(reportedAccountDetails, false)

        AuditEvent.auditReportSuspiciousPTAccount(
          accountDetailsWithOneMFADetails
        )(requestWithMongoAndAccountType) shouldEqual expectedAuditEvent
      }
    }

    "the reported account has no email or mfaDetails" should {
      "return an audit event with the expected details" in {
        val accountDetailsNoEmailOrMFA = accountDetailsWithOneMFADetails
          .copy(email = None, mfaDetails = Seq.empty)
        val reportedAccountDetails =
          getReportedAccountJson(accountDetailsNoEmailOrMFA)
        val expectedAuditEvent =
          getExpectedAuditEvent(reportedAccountDetails, false)

        AuditEvent.auditReportSuspiciousPTAccount(accountDetailsNoEmailOrMFA)(
          requestWithMongoAndAccountType
        ) shouldEqual expectedAuditEvent
      }
    }

    "the reported account has no email" should {
      "return an audit event with the expected details" in {
        val accountDetailsNoEmail = accountDetailsWithOneMFADetails
          .copy(email = None)
        val reportedAccountDetails =
          getReportedAccountJson(accountDetailsNoEmail)
        val expectedAuditEvent =
          getExpectedAuditEvent(reportedAccountDetails, false)

        AuditEvent.auditReportSuspiciousPTAccount(accountDetailsNoEmail)(
          requestWithMongoAndAccountType
        ) shouldEqual expectedAuditEvent
      }
    }

    "the reported account has no mfaDetails" should {
      "return an audit event with the expected details" in {
        val accountDetailsNoMFA = accountDetailsWithOneMFADetails
          .copy(mfaDetails = Seq.empty)
        val reportedAccountDetails = getReportedAccountJson(accountDetailsNoMFA)
        val expectedAuditEvent =
          getExpectedAuditEvent(reportedAccountDetails, false)

        AuditEvent.auditReportSuspiciousPTAccount(accountDetailsNoMFA)(
          requestWithMongoAndAccountType
        ) shouldEqual expectedAuditEvent
      }
    }

    "the reported account has email and three mfaDetails" should {
      "return an audit event with the expected details" in {
        val accountDetailsWithThreeMFADetails = accountDetailsWithOneMFADetails
          .copy(
            mfaDetails = Seq(
              MFADetails("mfaDetails.text", "24321"),
              MFADetails("mfaDetails.voice", "24322"),
              MFADetails("mfaDetails.totp", "TEST")
            )
          )
        val reportedAccountDetails =
          getReportedAccountJson(accountDetailsWithThreeMFADetails)
        val expectedAuditEvent =
          getExpectedAuditEvent(reportedAccountDetails, false)

        AuditEvent.auditReportSuspiciousPTAccount(
          accountDetailsWithThreeMFADetails
        )(requestWithMongoAndAccountType) shouldEqual expectedAuditEvent
      }
    }
  }
}
