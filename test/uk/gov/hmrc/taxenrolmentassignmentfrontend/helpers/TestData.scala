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

package uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers

import play.api.mvc.{AnyContentAsEmpty, AnyContentAsFormUrlEncoded}
import play.api.test.FakeRequest
import uk.gov.hmrc.auth.core.AffinityGroup.Individual
import uk.gov.hmrc.auth.core.AuthProvider.GovernmentGateway
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals._
import uk.gov.hmrc.auth.core.retrieve.{Credentials, Retrieval, ~}
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.crypto.Sensitive.SensitiveString
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes.{MULTIPLE_ACCOUNTS, PT_ASSIGNED_TO_CURRENT_USER, PT_ASSIGNED_TO_OTHER_USER, SA_ASSIGNED_TO_CURRENT_USER, SA_ASSIGNED_TO_OTHER_USER, SINGLE_ACCOUNT}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.actions.UserDetailsFromSession
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.{EACDEnrolment => _, _}

object TestData {

  val NINO: Nino = new Generator().nextNino
  val secondNino: Nino = new Generator().nextNino
  val GROUP_ID = "D37DB2E1-CF03-42E8-B151-E17300FFCF78"
  val CREDENTIAL_ID = "credId123"
  val PROVIDER_TYPE = "GovernmentGateway"
  val USER_ID = "6037"
  val PT_USER_ID = "2341"
  val NO_EMAIL_USER_ID = "9871"
  val CREDENTIAL_ID_1 = "6102202884164541"
  val CURRENT_USER_EMAIL = "foobarwizz"

  val CREDENTIAL_ID_2 = "6102202884164542"
  val UTR = "123456789"
  val creds: Credentials = Credentials(CREDENTIAL_ID, PROVIDER_TYPE)
  val noEnrolments: Enrolments = Enrolments(Set.empty[Enrolment])
  val saEnrolmentOnly: Enrolments = Enrolments(
    Set(
      Enrolment(
        "IR-SA",
        Seq(EnrolmentIdentifier("UTR", UTR)),
        "Activated",
        None
      )
    )
  )
  val ptEnrolmentOnly: Enrolments = Enrolments(
    Set(
      Enrolment(
        "HMRC-PT",
        Seq(EnrolmentIdentifier("NINO", NINO.nino)),
        "Activated",
        None
      )
    )
  )
  val saAndptEnrolments: Enrolments = Enrolments(
    Set(
      Enrolment(
        "HMRC-PT",
        Seq(EnrolmentIdentifier("NINO", NINO.nino)),
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

  val randomAccountType: AccountTypes.Value = SINGLE_ACCOUNT
  val predicates: Predicate =
    AuthProviders(GovernmentGateway) and ConfidenceLevel.L200

  val retrievals: Retrieval[
    Option[String] ~ Option[Credentials] ~ Enrolments ~ Option[String] ~ Option[
      AffinityGroup
    ] ~ Option[String]
  ] = nino and credentials and allEnrolments and groupIdentifier and affinityGroup and email

  def retrievalResponse(
    optNino: Option[String] = Some(NINO.nino),
    optCredentials: Option[Credentials] = Some(creds),
    enrolments: Enrolments = noEnrolments,
    optGroupId: Option[String] = Some(GROUP_ID),
    optAffinityGroup: Option[AffinityGroup] = Some(Individual),
    email: Option[String] = Some(CURRENT_USER_EMAIL)
  ): Option[String] ~ Option[Credentials] ~ Enrolments ~ Option[String] ~ Option[AffinityGroup] ~ Option[
    String
  ] =
    new ~(
      new ~(
        new ~(new ~(new ~(optNino, optCredentials), enrolments), optGroupId),
        optAffinityGroup
      ),
      email
    )

  def userDetails(hmrcPt: Boolean, irSa: Boolean): UserDetailsFromSession =
    UserDetailsFromSession(
      CREDENTIAL_ID,
      PROVIDER_TYPE,
      NINO,
      GROUP_ID,
      Some(CURRENT_USER_EMAIL),
      Individual,
      enrolments = Enrolments(Set.empty[Enrolment]),
      hasPTEnrolment = hmrcPt,
      hasSAEnrolment = irSa
    )

  val userDetailsNoEnrolments: UserDetailsFromSession =
    UserDetailsFromSession(
      CREDENTIAL_ID,
      PROVIDER_TYPE,
      NINO,
      GROUP_ID,
      Some(CURRENT_USER_EMAIL),
      Individual,
      enrolments = Enrolments(Set.empty[Enrolment]),
      hasPTEnrolment = false,
      hasSAEnrolment = false
    )
  val userDetailsWithPTEnrolment: UserDetailsFromSession =
    UserDetailsFromSession(
      CREDENTIAL_ID,
      PROVIDER_TYPE,
      NINO,
      GROUP_ID,
      Some(CURRENT_USER_EMAIL),
      Individual,
      enrolments = ptEnrolmentOnly,
      hasPTEnrolment = true,
      hasSAEnrolment = false
    )
  val userDetailsWithSAEnrolment: UserDetailsFromSession =
    UserDetailsFromSession(
      CREDENTIAL_ID,
      PROVIDER_TYPE,
      NINO,
      GROUP_ID,
      Some(CURRENT_USER_EMAIL),
      Individual,
      enrolments = saEnrolmentOnly,
      hasPTEnrolment = false,
      hasSAEnrolment = true
    )
  val userDetailsWithPTAndSAEnrolment: UserDetailsFromSession =
    UserDetailsFromSession(
      CREDENTIAL_ID,
      PROVIDER_TYPE,
      NINO,
      GROUP_ID,
      Some(CURRENT_USER_EMAIL),
      Individual,
      enrolments = saAndptEnrolments,
      hasPTEnrolment = true,
      hasSAEnrolment = true
    )

  val UsersAssignedEnrolmentCurrentCred: UsersAssignedEnrolment =
    UsersAssignedEnrolment(Some(CREDENTIAL_ID))
  val UsersAssignedEnrolment1: UsersAssignedEnrolment =
    UsersAssignedEnrolment(Some(CREDENTIAL_ID_1))
  val UsersAssignedEnrolmentEmpty: UsersAssignedEnrolment =
    UsersAssignedEnrolment(None)

  val accountDetails: AccountDetails = AccountDetails(
    identityProviderType = SCP,
    credId = CREDENTIAL_ID,
    userId = USER_ID,
    email = Some(SensitiveString("email1@test.com")),
    lastLoginDate = Some("2022-02-27T12:00:27Z"),
    mfaDetails = List(MFADetails("mfaDetails.text", "24321"))
  )

  val accountDetailsOL: AccountDetails = AccountDetails(
    identityProviderType = ONE_LOGIN,
    credId = CREDENTIAL_ID,
    userId = USER_ID,
    email = Some(SensitiveString("email1@test.com")),
    lastLoginDate = Some("2022-02-27T12:00:27Z"),
    mfaDetails = List(MFADetails("mfaDetails.text", "24321"))
  )

  val accountDetailsSA: AccountDetails = AccountDetails(
    identityProviderType = SCP,
    credId = CREDENTIAL_ID_1,
    userId = USER_ID,
    email = Some(SensitiveString("email1@test.com")),
    lastLoginDate = Some("27 February 2022 at 12:00PM"),
    mfaDetails = List(MFADetails("mfaDetails.text", "24321"))
  )

  val accountDetailsSAOL: AccountDetails = AccountDetails(
    identityProviderType = ONE_LOGIN,
    credId = CREDENTIAL_ID_1,
    userId = USER_ID,
    email = Some(SensitiveString("email1@test.com")),
    lastLoginDate = Some("27 February 2022 at 12:00PM"),
    mfaDetails = List(MFADetails("mfaDetails.text", "24321"))
  )

  val accountDetailsWithPT: AccountDetails = AccountDetails(
    identityProviderType = SCP,
    credId = CREDENTIAL_ID_1,
    userId = PT_USER_ID,
    email = Some(SensitiveString("email.otherUser@test.com")),
    lastLoginDate = Some("2022-02-27T12:00:27Z"),
    mfaDetails = List(MFADetails("mfaDetails.text", "26543")),
    hasSA = Some(true)
  )

  val accountDetailsWithPTOL: AccountDetails = AccountDetails(
    identityProviderType = ONE_LOGIN,
    credId = CREDENTIAL_ID_1,
    userId = PT_USER_ID,
    email = Some(SensitiveString("email.otherUser@test.com")),
    lastLoginDate = Some("2022-02-27T12:00:27Z"),
    mfaDetails = List(MFADetails("mfaDetails.text", "26543")),
    hasSA = Some(true)
  )

  def ptEnrolmentDataModel(
    saUserCred: Option[String],
    ptAccountDetails: AccountDetails = accountDetailsWithPT
  ): PTEnrolmentOnOtherAccount = PTEnrolmentOnOtherAccount(
    currentAccountDetails = accountDetails,
    ptAccountDetails = ptAccountDetails,
    saUserCred = saUserCred
  )

  def ptEnrolmentDataModelOL(
    saUserCred: Option[String],
    ptAccountDetails: AccountDetails = accountDetailsWithPTOL
  ): PTEnrolmentOnOtherAccount = PTEnrolmentOnOtherAccount(
    currentAccountDetails = accountDetailsOL,
    ptAccountDetails = ptAccountDetails,
    saUserCred = saUserCred
  )

  val usersGroupSearchResponse: UsersGroupResponse = UsersGroupResponse(
    identityProviderType = SCP,
    obfuscatedUserId = Some("********6037"),
    email = Some("email1@test.com"),
    lastAccessedTimestamp = Some("2022-02-27T12:00:27Z"),
    additionalFactors = Some(List(AdditonalFactors("sms", Some("07783924321"))))
  )

  val identifierTxNum: IdentifiersOrVerifiers = IdentifiersOrVerifiers("TaxOfficeNumber", "123")
  val identifierTxRef: IdentifiersOrVerifiers =
    IdentifiersOrVerifiers("TaxOfficeReference", "XYZ9876543")
  val identifierUTR: IdentifiersOrVerifiers = IdentifiersOrVerifiers("UTR", "1234567890")

  val userEnrolmentIRSA: UserEnrolment = UserEnrolment(
    service = "IR-SA",
    state = "NotYetActivated",
    friendlyName = "",
    failedActivationCount = 0,
    identifiers = Seq(identifierUTR)
  )

  val userEnrolmentIRPAYE: UserEnrolment = UserEnrolment(
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

  def buildFakePOSTRequestWithSessionId(
    data: Map[String, String]
  ): FakeRequest[AnyContentAsFormUrlEncoded] =
    FakeRequest("POST", "Not Used")
      .withSession("sessionId" -> "FAKE_SESSION_ID")
      .withFormUrlEncodedBody(data.toSeq: _*)

  val userDetailsWithMismatchNino: UserDetailsFromSession =
    UserDetailsFromSession(
      CREDENTIAL_ID,
      PROVIDER_TYPE,
      secondNino,
      GROUP_ID,
      Some(CURRENT_USER_EMAIL),
      Individual,
      enrolments = ptEnrolmentOnly,
      hasPTEnrolment = true,
      hasSAEnrolment = false
    )

  val all_account_types: Seq[AccountTypes.Value] = List(
    SINGLE_ACCOUNT,
    PT_ASSIGNED_TO_OTHER_USER,
    PT_ASSIGNED_TO_CURRENT_USER,
    MULTIPLE_ACCOUNTS,
    SA_ASSIGNED_TO_CURRENT_USER,
    SA_ASSIGNED_TO_OTHER_USER
  )

  val userGroupSearchCredIds: Seq[IdentityProviderWithCredId] =
    Seq(
      IdentityProviderWithCredId("12345678989012", SCP, "12345678989012"),
      IdentityProviderWithCredId("098765432109", ONE_LOGIN, "12345"),
      IdentityProviderWithCredId("543210987654", SCP, "543210987654")
    )

  val userGroupSearchOneCredId: Seq[IdentityProviderWithCredId] =
    Seq(
      IdentityProviderWithCredId("12345678989012", SCP, "12345678989012")
    )
}
