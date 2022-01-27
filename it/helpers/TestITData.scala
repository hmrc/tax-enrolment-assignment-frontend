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
import uk.gov.hmrc.auth.core.AuthProvider.GovernmentGateway
import uk.gov.hmrc.auth.core.retrieve.Credentials
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.IVNinoStoreEntry

object TestITData {

  val NINO: String = "JT872173A"
  val CREDENTIAL_ID: String = "credId123"
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
  val csrfContent: (String, String) = "Csrf-Token" -> "nocheck"

  def authoriseResponseJson(optNino: Option[String] = Some(NINO),
                            optCreds: Option[Credentials] = Some(creds),
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

    ninoJson ++ credentialsJson ++ enrolmentsJson
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
      |"confidenceLevel":50,
      |"createdAt":{"$date":1638526944017},
      |"updatedAt":{"$date":1641388390858}},
      |{"credId":"8316291481001919",
      |"nino":"JT872173A",
      |"confidenceLevel":200,
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
      |{"credId":"2884521810163541",
      |"nino":"JT872173A",
      |"confidenceLevel":200,
      |"createdAt":{"$date":1638531686457},
      |"updatedAt":{"$date":1638531686525}}]""".stripMargin

  val ivNinoStoreEntry1: IVNinoStoreEntry =
    IVNinoStoreEntry("6902202884164548", Some(CL50))
  val ivNinoStoreEntry2: IVNinoStoreEntry =
    IVNinoStoreEntry("8316291481001919", Some(CL200))
  val ivNinoStoreEntry3: IVNinoStoreEntry =
    IVNinoStoreEntry("0493831301037584", Some(CL200))
  val ivNinoStoreEntry4: IVNinoStoreEntry =
    IVNinoStoreEntry("2884521810163541", Some(CL200))

  val multiIVCreds = List(
    ivNinoStoreEntry1,
    ivNinoStoreEntry2,
    ivNinoStoreEntry3,
    ivNinoStoreEntry4
  )
}
