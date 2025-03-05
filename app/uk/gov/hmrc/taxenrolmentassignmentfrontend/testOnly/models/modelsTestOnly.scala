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

package uk.gov.hmrc.taxenrolmentassignmentfrontend.testOnly.models

import play.api.libs.functional.syntax.toFunctionalBuilderOps
import play.api.libs.json._
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.{AdditonalFactors, IdentifiersOrVerifiers}

case class SubmitLogin(
  user: String,
  accountNumber: Int
)

case class UserTestOnly(
  credId: String,
  name: String,
  email: String
)

object UserTestOnly {
  implicit val reads: Reads[UserTestOnly] = Json.reads[UserTestOnly]

  implicit val writes: Writes[UserTestOnly] = new Writes[UserTestOnly] {
    override def writes(o: UserTestOnly): JsValue = Json.toJson(
      Map(
        "credId"         -> JsString(o.credId),
        "name"           -> JsString(o.name),
        "email"          -> JsString(o.email),
        "credentialRole" -> JsString("Admin"),
        "description"    -> JsString("Description")
      )
    )
  }

}

case class AccountDetailsTestOnly(
  identityProviderType: String,
  groupId: String,
  nino: Nino,
  affinityGroup: String = "Individual",
  user: UserTestOnly,
  enrolments: List[EnrolmentDetailsTestOnly],
  additionalFactors: List[AdditonalFactors]
) {

  def individualContextUpdateRequestBody(caUserId: String) =
    Json.obj(
      "caUserId" -> caUserId,
      "nino"     -> nino
    )
  def identityProviderAccountContextRequestBody: JsObject =
    Json.obj(
      "eacdUserId"           -> user.credId,
      "identityProviderId"   -> user.credId,
      "identityProviderType" -> identityProviderType,
      "email"                -> user.email
    )
  def enrolmentStoreStubAccountDetailsRequestBody(credId: String): JsObject =
    Json.obj(
      "groupId"       -> groupId,
      "nino"          -> nino,
      "affinityGroup" -> affinityGroup,
      "users"         -> List(user),
      "enrolments"    -> enrolments.map(_.enrolmentStoreStubEnrolmentDetails(credId))
    )

  def basStubAccountDetailsRequestBody: JsObject =
    Json.obj(
      "credId"        -> user.credId,
      "userId"        -> user.credId,
      "isAdmin"       -> true,
      "accountType"   -> "Individual",
      "email"         -> user.email,
      "emailVerified" -> true,
      "profile"       -> "/profile",
      "groupId"       -> groupId,
      "groupProfile"  -> "/group/profile",
      "trustId"       -> "trustId",
      "name"          -> "Name",
      "suspended"     -> false
    )

  def mfaAccountRequestBody: JsObject =
    Json.obj(
      "sub"          -> user.credId,
      "userId"       -> Json.obj("createdDate" -> "2016-10-16T14:40:25Z"),
      "recoveryWord" -> true,
      "password"     -> Json.obj("authType" -> "password", "status" -> "not locked"),
      "additionalFactors" -> additionalFactors.map { additionalFactor =>
        Json.obj(
          "factorType"  -> additionalFactor.factorType,
          "phoneNumber" -> additionalFactor.phoneNumber,
          "name"        -> additionalFactor.name,
          "source"      -> "SCP",
          "details"     -> Json.obj("id" -> "KDPQap8t-nHQT-tfri-BZoV-FFpVWoHxiEMq", "createdDate" -> "2016-10-16T14:40:25Z")
        )
      }
    )
}

object AccountDetailsTestOnly {
  implicit val reads: Reads[AccountDetailsTestOnly] = (
    (JsPath \ "identityProviderType").readNullable[String].map(_.getOrElse("SCP")) and
      (JsPath \ "groupId").read[String] and
      (JsPath \ "nino").read[Nino] and
      (JsPath \ "affinityGroup").read[String] and
      (JsPath \ "user").read[UserTestOnly] and
      (JsPath \ "enrolments").readNullable[List[EnrolmentDetailsTestOnly]].map(_.getOrElse(List.empty)) and
      (JsPath \ "additionalFactors").readNullable[List[AdditonalFactors]].map(_.getOrElse(List.empty))
  )(AccountDetailsTestOnly.apply _)

  implicit val writes: OWrites[AccountDetailsTestOnly] = Json.writes[AccountDetailsTestOnly]
}

case class EnrolmentDetailsTestOnly(
  serviceName: String,
  identifiers: IdentifiersOrVerifiers,
  verifiers: List[IdentifiersOrVerifiers],
  enrolmentFriendlyName: String,
  state: String,
  enrolmentType: String
) {
  def enrolmentStoreStubEnrolmentDetails(credId: String): JsObject =
    Json.obj(
      "serviceName"           -> serviceName,
      "identifiers"           -> List(identifiers),
      "verifiers"             -> verifiers,
      "assignedUserCreds"     -> List(credId),
      "assignedToAll"         -> false,
      "enrolmentFriendlyName" -> enrolmentFriendlyName,
      "state"                 -> state,
      "enrolmentType"         -> enrolmentType
    )
}
object EnrolmentDetailsTestOnly {
  implicit val format: Format[EnrolmentDetailsTestOnly] = Json.format[EnrolmentDetailsTestOnly]
}
