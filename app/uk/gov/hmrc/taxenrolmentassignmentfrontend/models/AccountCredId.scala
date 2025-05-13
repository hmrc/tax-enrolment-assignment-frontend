/*
 * Copyright 2025 HM Revenue & Customs
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

package uk.gov.hmrc.taxenrolmentassignmentfrontend.models

import play.api.libs.json.{JsArray, JsPath, JsSuccess, JsValue, Reads}

case class IdentityProviderWithCredId(
  credId: String,
  identityProviderType: IdentityProviderType
)

object IdentityProviderWithCredId {

  implicit val reads: Reads[IdentityProviderWithCredId] = for {
    credId <- (JsPath \ "credId").read[String]
    identityProviderType <-
      (JsPath \ "identityProviderType").read[IdentityProviderType](IdentityProviderTypeFormat.reads)
  } yield IdentityProviderWithCredId(credId, identityProviderType)

  private val credIdsWithId: Reads[Seq[IdentityProviderWithCredId]] = (json: JsValue) => {

    val credIds: Seq[JsValue] = json.validate[JsArray] match {
      case JsSuccess(arr, _) => arr.value.toSeq
      case _                 => Nil
    }

    JsSuccess(credIds.map(_.as[IdentityProviderWithCredId](reads)))
  }

  implicit val readList: Reads[Seq[IdentityProviderWithCredId]] =
    (JsPath \ "credIds").read[Seq[IdentityProviderWithCredId]](credIdsWithId)
}
