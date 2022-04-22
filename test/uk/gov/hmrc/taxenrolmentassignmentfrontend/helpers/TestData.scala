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

package uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers

import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import uk.gov.hmrc.auth.core.AuthProvider.GovernmentGateway
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals.{allEnrolments, credentials, groupIdentifier, nino}
import uk.gov.hmrc.auth.core.retrieve.{Credentials, Retrieval, ~}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes.{MULTIPLE_ACCOUNTS, PT_ASSIGNED_TO_CURRENT_USER, PT_ASSIGNED_TO_OTHER_USER, SA_ASSIGNED_TO_CURRENT_USER, SA_ASSIGNED_TO_OTHER_USER, SINGLE_ACCOUNT}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.auth.UserDetailsFromSession
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.{Enrolment => _, _}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.{IVNinoStoreEntry, IdentifiersOrVerifiers, UserEnrolment, UsersAssignedEnrolment}

object TestData {

  val NINO = "testNino"
  val GROUP_ID = "D37DB2E1-CF03-42E8-B151-E17300FFCF78"
  val CREDENTIAL_ID = "credId123"
  val CREDENTIAL_ID_1 = "6102202884164541"
  val UTR = "1234567890"
  val creds = Credentials(CREDENTIAL_ID, GovernmentGateway.toString)
  val noEnrolments = Enrolments(Set.empty[Enrolment])
  val saEnrolmentOnly = Enrolments(
    Set(
      Enrolment(
        "IR-SA",
        Seq(EnrolmentIdentifier("UTR", "123456789")),
        "Activated",
        None
      )
    )
  )
  val ptEnrolmentOnly = Enrolments(
    Set(
      Enrolment(
        "HMRC-PT",
        Seq(EnrolmentIdentifier("NINO", NINO)),
        "Activated",
        None
      )
    )
  )
  val saAndptEnrolments = Enrolments(
    Set(
      Enrolment(
        "HMRC-PT",
        Seq(EnrolmentIdentifier("NINO", NINO)),
        "Activated",
        None
      ),
      Enrolment(
        "IR-SA",
        Seq(EnrolmentIdentifier("UTR", "123456789")),
        "Activated",
        None
      )
    )
  )

  //AuthAction
  val predicates: Predicate =
    AuthProviders(GovernmentGateway) and ConfidenceLevel.L200

  val retrievals: Retrieval[
    Option[String] ~ Option[Credentials] ~ Enrolments ~ Option[String]
  ] =
    nino and credentials and allEnrolments and groupIdentifier

  def retrievalResponse(
    optNino: Option[String] = Some(NINO),
    optCredentials: Option[Credentials] = Some(creds),
    enrolments: Enrolments = noEnrolments,
    optGroupId: Option[String] = Some(GROUP_ID)
  ): (((Option[String] ~ Option[Credentials]) ~ Enrolments) ~ Option[String]) =
    new ~(new ~(new ~(optNino, optCredentials), enrolments), optGroupId)

  val userDetailsNoEnrolments =
    UserDetailsFromSession(
      CREDENTIAL_ID,
      NINO,
      GROUP_ID,
      hasPTEnrolment = false,
      hasSAEnrolment = false
    )
  val userDetailsWithPTEnrolment =
    UserDetailsFromSession(
      CREDENTIAL_ID,
      NINO,
      GROUP_ID,
      hasPTEnrolment = true,
      hasSAEnrolment = false
    )
  val userDetailsWithSAEnrolment =
    UserDetailsFromSession(
      CREDENTIAL_ID,
      NINO,
      GROUP_ID,
      hasPTEnrolment = false,
      hasSAEnrolment = true
    )
  val userDetailsWithPTAndSAEnrolment = {
    UserDetailsFromSession(
      CREDENTIAL_ID,
      NINO,
      GROUP_ID,
      hasPTEnrolment = true,
      hasSAEnrolment = true
    )
  }

  val ivNinoStoreEntryCurrent = IVNinoStoreEntry(CREDENTIAL_ID, Some(200))
  val ivNinoStoreEntry1 = IVNinoStoreEntry("6902202884164548", Some(50))
  val ivNinoStoreEntry2 = IVNinoStoreEntry("8316291481001919", Some(200))
  val ivNinoStoreEntry3 = IVNinoStoreEntry("0493831301037584", Some(200))
  val ivNinoStoreEntry4 = IVNinoStoreEntry("2884521810163541", Some(200))

  val UsersAssignedEnrolmentCurrentCred =
    UsersAssignedEnrolment(Some(CREDENTIAL_ID))
  val UsersAssignedEnrolment1 =
    UsersAssignedEnrolment(Some(CREDENTIAL_ID_1))
  val UsersAssignedEnrolmentEmpty =
    UsersAssignedEnrolment(None)

  val multiIVCreds = List(
    ivNinoStoreEntryCurrent,
    ivNinoStoreEntry1,
    ivNinoStoreEntry2,
    ivNinoStoreEntry3,
    ivNinoStoreEntry4
  )

  // accountDetails

  val accountDetails: AccountDetails = AccountDetails(
    userId = "6037",
    email = Some("email1@test.com"),
    lastLoginDate = "27 February 2022 at 12:00 PM",
    mfaDetails = List(MFADetails("mfaDetails.text", "24321"))
  )

  val accountDetailsOtherUser: AccountDetails = AccountDetails(
    userId = "5046",
    email = Some("email.otherUser@test.com"),
    lastLoginDate = "27 February 2022 at 12:00 PM",
    mfaDetails = List(MFADetails("mfaDetails.text", "26543"))
  )


  // userGroupSearchResponse

  val usersGroupSearchResponse = UsersGroupResponse(
    obfuscatedUserId = "********6037",
    email = Some("email1@test.com"),
    lastAccessedTimestamp = "2022-02-27T12:00:27Z",
    additionalFactors = Some(List(AdditonalFactors("sms", Some("07783924321"))))
  )

  val multiCL200IVCreds = List(
    ivNinoStoreEntry2,
    ivNinoStoreEntry3,
    ivNinoStoreEntry4,
    ivNinoStoreEntry2,
    ivNinoStoreEntry3,
    ivNinoStoreEntry4,
    ivNinoStoreEntry2,
    ivNinoStoreEntry3,
    ivNinoStoreEntry4,
    ivNinoStoreEntry2,
    ivNinoStoreEntry3,
    ivNinoStoreEntry4
  )

  val multiOptionalIVCreds = Seq(
    Some(ivNinoStoreEntry1),
    Some(ivNinoStoreEntry2),
    None,
    Some(ivNinoStoreEntry3),
    None,
    None,
    Some(ivNinoStoreEntry4)
  )

  val identifierTxNum = IdentifiersOrVerifiers("TaxOfficeNumber", "123")
  val identifierTxRef =
    IdentifiersOrVerifiers("TaxOfficeReference", "XYZ9876543")
  val identifierUTR = IdentifiersOrVerifiers("UTR", "1234567890")

  val userEnrolmentIRSA = UserEnrolment(
    service = "IR-SA",
    state = "NotYetActivated",
    friendlyName = "",
    failedActivationCount = 0,
    identifiers = Seq(identifierUTR)
  )

  val userEnrolmentIRPAYE = UserEnrolment(
    service = "IR-PAYE",
    state = "Activated",
    friendlyName = "Something",
    failedActivationCount = 1,
    identifiers = Seq(identifierTxNum, identifierTxRef)
  )

  def buildFakeRequestWithSessionId(
    method: String,
    url: String = ""
  ): FakeRequest[AnyContentAsEmpty.type] =
    FakeRequest(method, url).withSession("sessionId" -> "FAKE_SESSION_ID")

  val all_account_types = List(
    SINGLE_ACCOUNT,
    PT_ASSIGNED_TO_OTHER_USER,
    PT_ASSIGNED_TO_CURRENT_USER,
    MULTIPLE_ACCOUNTS,
    SA_ASSIGNED_TO_CURRENT_USER,
    SA_ASSIGNED_TO_OTHER_USER
  )
}
