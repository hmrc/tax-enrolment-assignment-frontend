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

package helpers

import play.api.libs.json._
import uk.gov.hmrc.auth.core.AffinityGroup.Individual
import uk.gov.hmrc.auth.core.AuthProvider.GovernmentGateway
import uk.gov.hmrc.auth.core.retrieve.Credentials
import uk.gov.hmrc.auth.core.{AffinityGroup, Enrolment, EnrolmentIdentifier, Enrolments}
import uk.gov.hmrc.crypto.Sensitive.SensitiveString
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.http.SessionKeys
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.actions.UserDetailsFromSession
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models._

object TestITData {

  val NINO: Nino         = new Generator().nextNino
  val mismatchNino: Nino = new Generator().nextNino

  val PROVIDER_TYPE                     = "GovernmentGateway"
  val CURRENT_USER_EMAIL                = "foobarwizz"
  val CREDENTIAL_ID: String             = "6902202884164548"
  val CREDENTIAL_ID_2: String           = "8316291481001919"
  val CREDENTIAL_ID_3: String           = "0493831301037584"
  val CREDENTIAL_ID_4: String           = "2884521810163541"
  val USER_ID                           = "6037"
  val GROUP_ID: String                  = "GROUPID123"
  val UTR                               = "1234567890"
  val CL50                              = 50
  val CL200                             = 200
  val creds: Credentials                =
    Credentials(CREDENTIAL_ID, GovernmentGateway.toString)
  val noEnrolments: JsValue             = Json.arr()
  val saEnrolmentOnly: JsValue          =
    Json.arr(createEnrolmentJson("IR-SA", "UTR", "123456789"))
  val saEnrolmentAsCaseClass: Enrolment = Enrolment("IR-SA", Seq(EnrolmentIdentifier("UTR", "123456789")), "Activated")
  val ptEnrolmentOnly: JsValue          =
    Json.arr(createEnrolmentJson("HMRC-PT", "NINO", NINO.nino))
  val mismatchPtEnrolmentOnly: JsArray  =
    Json.arr(createEnrolmentJson("HMRC-PT", "NINO", mismatchNino.nino))
  val saAndptEnrolments: JsArray        = Json.arr(
    createEnrolmentJson("HMRC-PT", "NINO", NINO.nino),
    createEnrolmentJson("IR-SA", "UTR", "123456789")
  )
  val AUTHORIZE_HEADER_VALUE            =
    "Bearer BXQ3/Treo4kQCZvVcCqKPhhpBYpRtQQKWTypn1WBfRHWUopu5V/IFWF5phY/fymAP1FMqQR27MmCJxb50Hi5GD6G3VMjMtSLu7TAAIuqDia6jByIpXJpqOgLQuadi7j0XkyDVkl0Zp/zbKtHiNrxpa0nVHm3+GUC4H2h4Ki8OjP9KwIkeIPK/mMlBESjue4V"

  def createEnrolmentJson(key: String, identifierKey: String, identifierValue: String): JsValue =
    Json.obj(
      fields = "key" -> JsString(key),
      "identifiers"     -> Json
        .arr(
          Json.obj(
            "key"   -> JsString(identifierKey),
            "value" -> JsString(identifierValue)
          )
        ),
      "state"           -> JsString("Activated"),
      "confidenceLevel" -> JsNumber(CL200)
    )

  val sessionId                    = "sessionId-eb3158c2-0aff-4ce8-8d1b-f2208ace52fe"
  val xSessionId: (String, String) = SessionKeys.sessionId -> sessionId
  val xAuthToken: (String, String) = SessionKeys.authToken -> "Bearer 1"

  def authoriseResponseJson(
    optNino: Option[String] = Some(NINO.nino),
    optCreds: Option[Credentials] = Some(creds),
    optGroupId: Option[String] = Some(GROUP_ID),
    affinityGroup: AffinityGroup = Individual,
    enrolments: JsValue = noEnrolments
  ): JsValue = {

    val enrolmentsJson  = Json.obj("allEnrolments" -> enrolments)
    val ninoJson        = optNino.fold[JsObject](Json.obj())(nino => Json.obj("nino" -> JsString(nino)))
    val credentialsJson = optCreds.fold[JsObject](Json.obj())(creds =>
      Json.obj(
        "optionalCredentials" -> Json.obj(
          "providerId"   -> JsString(creds.providerId),
          "providerType" -> JsString(PROVIDER_TYPE)
        )
      )
    )
    val groupIdJson     = optGroupId.fold[JsObject](Json.obj())(groupId => Json.obj("groupIdentifier" -> JsString(groupId)))

    val affinityGroupJson = affinityGroup.toJson.as[JsObject]

    val email = Json.obj("email" -> JsString(CURRENT_USER_EMAIL))

    ninoJson ++ credentialsJson ++ enrolmentsJson ++ groupIdJson ++ affinityGroupJson ++ email
  }

  def authoriseResponseWithPTEnrolment(
    optNino: Option[String] = Some(NINO.nino),
    optCreds: Option[Credentials] = Some(creds),
    optGroupId: Option[String] = Some(GROUP_ID),
    affinityGroup: AffinityGroup = Individual,
    hasSA: Boolean = false
  ): JsValue = {
    val enrolments = if (hasSA) saAndptEnrolments else ptEnrolmentOnly
    authoriseResponseJson(optNino, optCreds, optGroupId, affinityGroup, enrolments)
  }

  val sessionNotFound             = "SessionRecordNotFound"
  val insufficientConfidenceLevel = "InsufficientConfidenceLevel"

  val eacdExampleError: String =
    """
      |{
      |  Code: "INVALID_CREDENTIAL_ID",
      |  Message: "Invalid credential ID"
      |}
      |""".stripMargin

  val ivResponseMultiCredsJsonString: String =
    """[
      |{
      |"credId":"6902202884164548",
      |"nino":"JT872173A",
      |"confidenceLevel":200,
      |"createdAt":{"$date":1638526944017},
      |"updatedAt":{"$date":1641388390858}},
      |{"credId":"8316291481001919",
      |"nino":"JT872173A",
      |"confidenceLevel":50,
      |"createdAt":{"$date":1638527029415},
      |"updatedAt":{"$date":1638527029478}},
      |{"credId":"0493831301037584",
      |"nino":"JT872173A",
      |"confidenceLevel":200,
      |"createdAt":{"$date":1638527081626},
      |"updatedAt":{"$date":1638527081689}},
      |{"credId":"2884521810163541",
      |"nino":"JT872173A",
      |"confidenceLevel":200,
      |"createdAt":{"$date":1638531686457},
      |"updatedAt":{"$date":1638531686525}}]""".stripMargin

  val ivResponseSingleCredsJsonString: String =
    """[
      |{"credId":"6902202884164548",
      |"nino":"JT872173A",
      |"confidenceLevel":200,
      |"createdAt":{"$date":1638531686457},
      |"updatedAt":{"$date":1638531686525}}]""".stripMargin

  val usersGroupSearchResponse: UsersGroupResponse = UsersGroupResponse(
    identityProviderType = SCP,
    obfuscatedUserId = Some("********6037"),
    email = Some("email1@test.com"),
    lastAccessedTimestamp = Some("2022-01-16T14:40:05Z"),
    additionalFactors = Some(List(AdditonalFactors("sms", Some("07783924321"))))
  )

  def accountDetailsUserFriendly(
    credId: String,
    userId: String = USER_ID
  ): AccountDetails =
    AccountDetails(
      identityProviderType = SCP,
      credId,
      userId,
      Some(SensitiveString("email1@test.com")),
      Some("16 January 2022 at 2:40PM"),
      List(MFADetails("mfaDetails.text", "24321")),
      None
    )

  def usersGroupSearchResponsePTEnrolment(userId: String = "********1234"): UsersGroupResponse =
    usersGroupSearchResponse.copy(obfuscatedUserId = Some(userId))

  def accountDetailsUnUserFriendly(credId: String): AccountDetails =
    AccountDetails(
      identityProviderType = SCP,
      credId,
      "********6037",
      Some(SensitiveString("email1@test.com")),
      Some("2022-01-16T14:40:05Z"),
      List(MFADetails("mfaDetails.text", "24321")),
      None
    )

  val usersGroupSearchResponseSAEnrolment: UsersGroupResponse =
    usersGroupSearchResponse.copy(obfuscatedUserId = Some("********1243"))

  def additionalFactorsJson(additionalFactors: List[AdditonalFactors]): JsArray =
    additionalFactors.foldLeft[JsArray](Json.arr()) { (a, b) =>
      val jsObject = if (b.factorType == "totp") {
        Json.obj(
          ("factorType", JsString(b.factorType)),
          ("name", JsString(b.name.getOrElse("")))
        )
      } else {
        Json.obj(
          ("factorType", JsString(b.factorType)),
          ("phoneNumber", JsString(b.phoneNumber.getOrElse("")))
        )
      }
      a.append(jsObject)
    }

  def usergroupsResponseJson(
    usersGroupResponse: UsersGroupResponse = usersGroupSearchResponse
  ): JsObject = {
    val compulsaryJson = Json.obj(
      "identityProviderType"  -> usersGroupResponse.identityProviderType.toString,
      "obfuscatedUserId"      -> usersGroupResponse.obfuscatedUserId,
      "email"                 -> usersGroupResponse.email.get,
      "lastAccessedTimestamp" -> Some(usersGroupResponse.lastAccessedTimestamp)
    )
    usersGroupResponse.additionalFactors.fold(compulsaryJson) { additionFactors =>
      compulsaryJson ++ Json.obj(
        ("additionalFactors", additionalFactorsJson(additionFactors))
      )
    }
  }

  val eacdUserEnrolmentsJson1: String =
    """
      |{
      |    "startRecord": 1,
      |    "totalRecords": 2,
      |    "enrolments": [
      |        {
      |           "service": "IR-PAYE",
      |           "state": "Activated",
      |           "friendlyName": "My First Client's PAYE Enrolment",
      |           "enrolmentDate": "2018-10-05 14:48:00.000Z",
      |           "failedActivationCount": 1,
      |           "activationDate": "2018-10-13 17:36:00.000Z",
      |           "enrolmentTokenExpiryDate": "2018-10-13 17:36:00.000Z",
      |           "identifiers": [
      |              {
      |                 "key": "TaxOfficeNumber",
      |                 "value": "120"
      |              },
      |              {
      |                 "key": "TaxOfficeReference",
      |                 "value": "ABC1234567"
      |              }
      |           ]
      |        },
      |        {
      |           "service": "IR-PAYE",
      |           "state": "Activated",
      |           "friendlyName": "My Second Client's PAYE Enrolment",
      |           "enrolmentDate": "2017-06-25 12:24:00.000Z",
      |           "failedActivationCount": 1,
      |           "activationDate": "2017-07-01 09:52:00.000Z",
      |           "enrolmentTokenExpiryDate": "2017-10-13 17:36:00.000Z",
      |           "identifiers": [
      |              {
      |                 "key": "TaxOfficeNumber",
      |                 "value": "123"
      |              },
      |              {
      |                 "key": "TaxOfficeReference",
      |                 "value": "XYZ9876543"
      |              }
      |           ]
      |        }
      |    ]
      |}
      |""".stripMargin

  val eacdUserEnrolmentsJson2: String =
    """
      |{
      |    "startRecord":1,
      |    "totalRecords":1,
      |    "enrolments":[
      |                {
      |                    "service":"IR-SA",
      |                    "state":"NotYetActivated",
      |                    "friendlyName":"",
      |                    "enrolmentDate":"2019-10-17 09:26:27.568",
      |                    "failedActivationCount":0,
      |                    "enrolmentTokenExpiryDate":"2019-11-16 09:26:27.568",
      |                    "identifiers": [
      |                            {
      |                                "key":"UTR",
      |                                "value":"1234567890"
      |                            }
      |                    ]
      |                }
      |    ]
      |}
      |""".stripMargin

  val identifierTxNum: IdentifiersOrVerifiers = IdentifiersOrVerifiers("TaxOfficeNumber", "123")
  val identifierTxRef: IdentifiersOrVerifiers =
    IdentifiersOrVerifiers("TaxOfficeReference", "XYZ9876543")
  val identifierUTR: IdentifiersOrVerifiers   = IdentifiersOrVerifiers("UTR", "1234567890")

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

  val es0ResponseMatchingCred: String =
    """
      |{
      |    "principalUserIds": [
      |       "6902202884164548"
      |    ],
      |    "delegatedUserIds": []
      |}
      |""".stripMargin

  val es0ResponseNotMatchingCred: String =
    """
      |{
      |    "principalUserIds": [
      |    "ABCEDEFGI1234567"],
      |    "delegatedUserIds": []
      |}
      |""".stripMargin

  val es0ResponseNoRecordCred: String =
    """
      |{
      |    "principalUserIds": [],
      |    "delegatedUserIds": []
      |}
      |""".stripMargin

  val es0GroupsResponseNoRecordCred: String =
    """
      |{
      |    "principalGroupIds": [],
      |    "delegatedGroupIds": []
      |}
      |""".stripMargin

  val es3GroupsResponseRecordCred: String =
    """
      |{
      |    "principalGroupIds": ["ABCDEFG123456"],
      |    "delegatedGroupIds": ["QWERTYU12346", "POIUYTT09876"]
      |}
      |""".stripMargin

  val saUsers: UsersAssignedEnrolment = UsersAssignedEnrolment(Some(CREDENTIAL_ID_2))

  val underConstructionTruePageTitle    =
    "Tax Enrolment Assignment Frontend - Enrolment Present"
  val enrolledPTPageTitle               =
    "Only your current user ID can access your personal tax account from now on"
  val ptEnrolledOnOtherAccountPageTitle =
    "We have found your personal tax account under a different Government Gateway user ID"
  val reportSuspiciousIDPageTitle       =
    "You need to contact us"

  val keepAccessToSAPageTitle =
    "Do you want to keep access to Self Assessment from your personal tax account?"

  val signInAgainPageTitle =
    "You need to sign in again with your Self Assessment user ID"

  val userDetailsNoEnrolments: UserDetailsFromSession =
    UserDetailsFromSession(
      CREDENTIAL_ID,
      PROVIDER_TYPE,
      NINO,
      GROUP_ID,
      Some(CURRENT_USER_EMAIL),
      Individual,
      Enrolments(Set.empty[Enrolment]),
      hasPTEnrolment = false,
      hasSAEnrolment = false
    )

  val userDetailsWithMismatchNino: UserDetailsFromSession =
    UserDetailsFromSession(
      CREDENTIAL_ID,
      PROVIDER_TYPE,
      mismatchNino,
      GROUP_ID,
      Some(CURRENT_USER_EMAIL),
      Individual,
      enrolments = Enrolments(
        Set(
          Enrolment(
            "HMRC-PT",
            Seq(EnrolmentIdentifier("NINO", NINO.nino)),
            "Activated",
            None
          )
        )
      ),
      hasPTEnrolment = true,
      hasSAEnrolment = false
    )

  val accountDetails: AccountDetails = AccountDetails(
    identityProviderType = SCP,
    credId = CREDENTIAL_ID_2,
    userId = USER_ID,
    email = Some(SensitiveString("email1@test.com")),
    lastLoginDate = Some("27 February 2022 at 12:00PM"),
    mfaDetails = List(MFADetails("mfaDetails.text", "24321"))
  )

  val eacdResponse: String = s"""{
                                |    "service": "IR-SA",
                                |    "enrolments": [{
                                |        "identifiers": [{
                                |            "key": "UTR",
                                |            "value": "1234567890"
                                |        }],
                                |        "verifiers": [{
                                |            "key": "NINO",
                                |            "value": "AB112233D"
                                |        },
                                |        {
                                |            "key": "Postcode",
                                |            "value": "SW1A 2AA"
                                |        }]
                                |    }]
                                |}""".stripMargin

  val userGroupSearchCredIdsResponse =
    """
      |{
      |    "credIds": [
      |        {
      |            "credId": "12345678989012",
      |            "identityProviderType": "SCP",
      |            "identityProviderId": "12345678989012"
      |        },
      |        {
      |            "credId": "098765432109",
      |            "identityProviderType": "ONE_LOGIN",
      |            "identityProviderId": "12345"
      |        },
      |        {
      |            "credId": "543210987654",
      |            "identityProviderType": "SCP",
      |            "identityProviderId": "543210987654"
      |        }
      |    ]
      |}
      |""".stripMargin

  val userGroupSearchCredIdsResponseOneId =
    """
      |{
      |    "credIds": [
      |        {
      |            "credId": "12345678989012",
      |            "identityProviderType": "SCP",
      |            "identityProviderId": "12345678989012"
      |        }
      |    ]
      |}
      |""".stripMargin

  val userGroupSearchCredIds =
    List(
      IdentityProviderWithCredId("12345678989012", SCP),
      IdentityProviderWithCredId("098765432109", ONE_LOGIN),
      IdentityProviderWithCredId("543210987654", SCP)
    )

}
