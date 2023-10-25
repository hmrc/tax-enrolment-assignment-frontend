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

package uk.gov.hmrc.taxenrolmentassignmentfrontend.models.testOnly

import play.api.libs.functional.syntax.toFunctionalBuilderOps
import play.api.libs.json.{JsObject, JsPath, Json, OWrites, Reads, Writes}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.IdentifiersOrVerifiers

case class SimpleEnrolment(
  serviceName: String,
  identifiers: List[IdentifiersOrVerifiers],
  verifiers: List[IdentifiersOrVerifiers]
)

object SimpleEnrolment {
  implicit val reads: Reads[SimpleEnrolment] = (
    (JsPath \ "serviceName").read[String] and
      (JsPath \ "identifiers").readNullable[List[IdentifiersOrVerifiers]].map(_.getOrElse(List.empty)) and
      (JsPath \ "verifiers").readNullable[List[IdentifiersOrVerifiers]].map(_.getOrElse(List.empty))
  )(SimpleEnrolment.apply _)

  implicit val writes: Writes[SimpleEnrolment] = Json.writes[SimpleEnrolment]
}

final case class KnownFactPersona(service: String, knownFacts: List[EacdKnownFact])

object KnownFactPersona {
  implicit val write: OWrites[KnownFactPersona] = Json.writes[KnownFactPersona]
}

case class EacdKnownFact(key: String, value: String, kfType: String)

object EacdKnownFact {
  implicit val writes: OWrites[EacdKnownFact] = Json.writes[EacdKnownFact]
}

case class AccountDescription(
  nino: Nino,
  credId: String,
  groupId: String,
  userEnrolments: List[SimpleEnrolment],
  groupEnrolments: List[SimpleEnrolment]
) {
  def knownFactsPayloads: List[JsObject] =
    getKnownFacts.map(Json.toJsObject(_))

  private def getKnownFacts: List[KnownFactPersona] =
    for {
      enrolment <- userEnrolments ++ groupEnrolments
      serviceName = enrolment.serviceName
      identifiers = enrolment.identifiers.map { ident =>
                      EacdKnownFact(ident.key, ident.value, "identifier")
                    }
      verifiers = enrolment.verifiers.map { vf =>
                    EacdKnownFact(vf.key, vf.value, "verifier")
                  }
    } yield KnownFactPersona(serviceName, identifiers ++ verifiers)

  def postTokenPayloadMap: Map[String, Seq[String]] =
    Map(
      "grant_type" -> Seq("password"),
      "username"   -> Seq(credId),
      "password"   -> Seq("password")
    )

  def postMfaAccountPayload: JsObject =
    Json.obj(
      "sub"          -> credId,
      "userId"       -> Json.obj("createdDate" -> "2016-10-16T14:40:25Z"),
      "recoveryWord" -> true,
      "password"     -> Json.obj("authType" -> "password", "status" -> "not locked"),
      "additionalFactors" -> Json.arr(
        Json.obj(
          "factorType"  -> "sms",
          "phoneNumber" -> "1234",
          "source"      -> "SCP",
          "details"     -> Json.obj("id" -> "KDPQap8t-nHQT-tfri-BZoV-FFpVWoHxiEMq", "createdDate" -> "2016-10-16T14:40:25Z")
        )
      )
    )

  def putNinoStorePayload: JsObject =
    Json.obj(
      "credId"          -> credId,
      "nino"            -> nino,
      "confidenceLevel" -> 200
    )

}

object AccountDescription {
  implicit val reads: Reads[AccountDescription] = (
    (JsPath \ "nino").read[Nino] and
      (JsPath \ "credId").read[String] and
      (JsPath \ "groupId").read[String] and
      (JsPath \ "userEnrolments").readNullable[List[SimpleEnrolment]].map(_.getOrElse(List.empty)) and
      (JsPath \ "groupEnrolments").readNullable[List[SimpleEnrolment]].map(_.getOrElse(List.empty))
  )(AccountDescription.apply _)

  implicit val writes: Writes[AccountDescription] = Json.writes[AccountDescription]
}
