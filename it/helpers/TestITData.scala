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

package helpers

import play.api.libs.json._
import play.api.mvc.Session
import uk.gov.hmrc.auth.core.AuthProvider.GovernmentGateway
import uk.gov.hmrc.auth.core.retrieve.Credentials
import uk.gov.hmrc.crypto.PlainText
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.auth.UserDetailsFromSession
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models._

object TestITData {

  val NINO: String = "JT872173A"
  val CREDENTIAL_ID: String = "6902202884164548"
  val CREDENTIAL_ID_2: String = "8316291481001919"
  val CREDENTIAL_ID_3: String = "0493831301037584"
  val CREDENTIAL_ID_4: String = "2884521810163541"
  val GROUP_ID: String = "GROUPID123"
  val CL50 = 50
  val CL200 = 200
  val creds: Credentials =
    Credentials(CREDENTIAL_ID, GovernmentGateway.toString)
  val noEnrolments: JsValue = Json.arr()
  val saEnrolmentOnly: JsValue =
    Json.arr(createEnrolmentJson("IR-SA", "UTR", "123456789"))
  val ptEnrolmentOnly: JsValue =
    Json.arr(createEnrolmentJson("HMRC-PT", "NINO", NINO))
  val saAndptEnrolments: JsArray = Json.arr(
    createEnrolmentJson("HMRC-PT", "NINO", NINO),
    createEnrolmentJson("IR-SA", "UTR", "123456789")
  )
  val AUTHORIZE_HEADER_VALUE =
    "Bearer BXQ3/Treo4kQCZvVcCqKPhhpBYpRtQQKWTypn1WBfRHWUopu5V/IFWF5phY/fymAP1FMqQR27MmCJxb50Hi5GD6G3VMjMtSLu7TAAIuqDia6jByIpXJpqOgLQuadi7j0XkyDVkl0Zp/zbKtHiNrxpa0nVHm3+GUC4H2h4Ki8OjP9KwIkeIPK/mMlBESjue4V"
  def createEnrolmentJson(key: String,
                          identifierKey: String,
                          identifierValue: String): JsValue = {
    Json.obj(
      fields =
        "key" -> JsString(key),
      "identifiers" -> Json
        .arr(
          Json.obj(
            "key" -> JsString(identifierKey),
            "value" -> JsString(identifierValue)
          )
        ),
      "state" -> JsString("Activated"),
      "confidenceLevel" -> JsNumber(CL200)
    )
  }

  val sessionId = "sessionId-eb3158c2-0aff-4ce8-8d1b-f2208ace52fe"
  val xSessionId: (String, String) = "X-Session-ID" -> sessionId
  val xRequestId: (String, String) = "X-Request-ID" -> sessionId
  val csrfContent: (String, String) = "Csrf-Token" -> "nocheck"

  def authoriseResponseJson(optNino: Option[String] = Some(NINO),
                            optCreds: Option[Credentials] = Some(creds),
                            optGroupId: Option[String] = Some(GROUP_ID),
                            enrolments: JsValue = noEnrolments): JsValue = {

    val enrolmentsJson = Json.obj("allEnrolments" -> enrolments)
    val ninoJson = optNino.fold[JsObject](Json.obj())(
      nino => Json.obj("nino" -> JsString(nino))
    )
    val credentialsJson = optCreds.fold[JsObject](Json.obj())(
      creds =>
        Json.obj(
          "optionalCredentials" -> Json.obj(
            "providerId" -> JsString(creds.providerId),
            "providerType" -> JsString("GovernmentGateway")
          )
      )
    )
    val groupIdJson = optGroupId.fold[JsObject](Json.obj())(
      groupId => Json.obj("groupIdentifier" -> JsString(groupId))
    )

    ninoJson ++ credentialsJson ++ enrolmentsJson ++ groupIdJson
  }

  val sessionNotFound = "SessionRecordNotFound"
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

  val ivNinoStoreEntry1: IVNinoStoreEntry =
    IVNinoStoreEntry(CREDENTIAL_ID, Some(CL200))
  val ivNinoStoreEntry2: IVNinoStoreEntry =
    IVNinoStoreEntry(CREDENTIAL_ID_2, Some(CL50))
  val ivNinoStoreEntry3: IVNinoStoreEntry =
    IVNinoStoreEntry(CREDENTIAL_ID_3, Some(CL200))
  val ivNinoStoreEntry4: IVNinoStoreEntry =
    IVNinoStoreEntry(CREDENTIAL_ID_4, Some(CL200))

  val multiIVCreds = List(
    ivNinoStoreEntry1,
    ivNinoStoreEntry2,
    ivNinoStoreEntry3,
    ivNinoStoreEntry4
  )

  // users group search

  val usersGroupSearchResponse = UsersGroupResponse(
    obfuscatedUserId = "********6037",
    email = Some("email1@test.com"),
    lastAccessedTimestamp = "2022-01-16T14:40:05Z",
    additionalFactors = List(AdditonalFactors("sms", Some("07783924321")))
  )

  def additionalFactorsJson(additionalFactors: List[AdditonalFactors]) =
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
  ) = {
    Json.obj(
      ("obfuscatedUserId", JsString(usersGroupResponse.obfuscatedUserId)),
      ("email", JsString(usersGroupResponse.email.get)),
      (
        "lastAccessedTimestamp",
        JsString(usersGroupResponse.lastAccessedTimestamp)
      ),
      (
        "additionalFactors",
        additionalFactorsJson(usersGroupResponse.additionalFactors)
      )
    )
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

  val es0ResponseMatchingCred =
    """
      |{
      |    "principalUserIds": [
      |       "6902202884164548"
      |    ],
      |    "delegatedUserIds": []
      |}
      |""".stripMargin

  val es0ResponseNotMatchingCred =
    """
      |{
      |    "principalUserIds": [
      |    "ABCEDEFGI1234567"],
      |    "delegatedUserIds": []
      |}
      |""".stripMargin

  val es0ResponseNoRecordCred =
    """
      |{
      |    "principalUserIds": [],
      |    "delegatedUserIds": []
      |}
      |""".stripMargin

  val underConstructionTruePageTitle =
    "Tax Enrolment Assignment Frontend - Enrolment Present"
  val landingPageTitle = "Landing Page"

  val userDetailsNoEnrolments =
    UserDetailsFromSession(
      CREDENTIAL_ID,
      NINO,
      GROUP_ID,
      hasPTEnrolment = false,
      hasSAEnrolment = false
    )

}
