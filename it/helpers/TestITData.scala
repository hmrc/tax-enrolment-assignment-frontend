/*
 * Copyright 2021 HM Revenue & Customs
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

import play.api.libs.json.{JsArray, JsObject, JsString, JsValue, Json}
import uk.gov.hmrc.auth.core.AuthProvider.GovernmentGateway
import uk.gov.hmrc.auth.core.{Enrolment, EnrolmentIdentifier, Enrolments}
import uk.gov.hmrc.auth.core.retrieve.Credentials

object TestITData {

  val NINO = "testNino"
  val CREDENTIAL_ID = "credId123"
  val creds = Credentials(CREDENTIAL_ID, GovernmentGateway.toString)
  val noEnrolments = Enrolments(Set.empty[Enrolment])
  val saEnrolmentOnly = Enrolments(Set(Enrolment("IR-SA", Seq(EnrolmentIdentifier("UTR", "123456789")), "Activated", None)))
  val ptEnrolmentOnly = Enrolments(Set(Enrolment("HMRC-PT", Seq(EnrolmentIdentifier("NINO", NINO)), "Activated", None)))
  val saAndptEnrolments = Enrolments(Set(
    Enrolment("HMRC-PT", Seq(EnrolmentIdentifier("NINO", NINO)), "Activated", None),
    Enrolment("IR-SA", Seq(EnrolmentIdentifier("UTR", "123456789")), "Activated", None)
  ))

  val sessionId = "sessionId-eb3158c2-0aff-4ce8-8d1b-f2208ace52fe"
  val xSessionId: (String, String) = "X-Session-ID" -> sessionId
  val csrfContent: (String, String) = "Csrf-Token" -> "nocheck"


  def authoriseResponseJson(optNino: Option[String] = Some(NINO),
                            optCreds: Option[Credentials] = Some(creds),
                            enrolments: Enrolments = noEnrolments): JsValue = {
    val enrolmentsJson = Json.obj(
      "allEnrolments" -> JsArray(enrolments.enrolments.map(_.toJson).toSeq)
    )
    val ninoJson = optNino.fold[JsObject](Json.obj())(nino => Json.obj("nino" -> JsString(nino)))
    val credentialsJson = optCreds.fold[JsObject](Json.obj())(creds => Json.obj("optionalCredentials" -> Json.obj(
      "providerId" -> JsString(creds.providerId),
      "providerType" -> JsString("GovernmentGateway")
    )))

    ninoJson ++ credentialsJson ++ enrolmentsJson
  }

  val sessionNotFound = "SessionRecordNotFound"
  val insufficientConfidenceLevel = "InsufficientConfidenceLevel"

}
