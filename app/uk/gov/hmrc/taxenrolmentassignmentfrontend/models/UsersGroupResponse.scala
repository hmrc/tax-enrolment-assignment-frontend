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

package uk.gov.hmrc.taxenrolmentassignmentfrontend.models

import play.api.libs.json.{Format, Json}

case class AdditonalFactors(factorType: String, phoneNumber: Option[String] = None, name: Option[String] = None) {
  val FIVE = 5
  def trimmedPhoneNumber: String = phoneNumber.fold("")(_.trim.takeRight(FIVE))
}

case class UsersGroupResponse(
  obfuscatedUserId: Option[String],
  email: Option[String],
  lastAccessedTimestamp: Option[String],
  additionalFactors: Option[List[AdditonalFactors]]
)

object AdditonalFactors {
  implicit val format: Format[AdditonalFactors] = Json.format[AdditonalFactors]
}

object UsersGroupResponse {
  implicit val format: Format[UsersGroupResponse] =
    Json.format[UsersGroupResponse]
}
