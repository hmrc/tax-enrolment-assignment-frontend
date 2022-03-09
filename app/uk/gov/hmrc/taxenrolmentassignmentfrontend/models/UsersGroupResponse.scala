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

package uk.gov.hmrc.taxenrolmentassignmentfrontend.models

import play.api.libs.json.{Format, Json}

case class AdditonalFactors(factorType: String,
                            phoneNumber: Option[String] = None,
                            name: Option[String] = None)

case class UsersGroupResponse(obfuscatedUserId: String,
                              email: Option[String],
                              lastAccessedTimestamp: String,
                              additionalFactors: List[AdditonalFactors])

object AdditonalFactors {
  implicit val format: Format[AdditonalFactors] = Json.format[AdditonalFactors]
}

object UsersGroupResponse {
  implicit val format: Format[UsersGroupResponse] =
    Json.format[UsersGroupResponse]
}
