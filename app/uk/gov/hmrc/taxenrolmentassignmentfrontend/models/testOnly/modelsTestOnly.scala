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

import play.api.libs.json.{Format, Json}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.IdentifiersOrVerifiers

case class Users(
  credId: String,
  name: String = "Default User",
  email: String = "default@example.com",
  credentialRole: String = "Admin",
  description: String = "User Description"
)

case class AccountDetailsTestOnly(
  groupId: String,
  affinityGroup: String = "Individual",
  users: List[Users],
  enrolments: List[EnrolmentDetailsTestOnly] = List.empty
)

object AccountDetailsTestOnly {
  def apply(groupId: String, credId: String): AccountDetailsTestOnly = new AccountDetailsTestOnly(
    groupId = groupId,
    users = List(Users(credId))
  )

  implicit val format: Format[AccountDetailsTestOnly] = Json.format[AccountDetailsTestOnly]
}

object Users {
  implicit val format: Format[Users] = Json.format[Users]
}

case class EnrolmentDetailsTestOnly(
  serviceName: String,
  identifiers: List[IdentifiersOrVerifiers],
  verifiers: List[IdentifiersOrVerifiers],
  assignedUserCreds: List[String],
  assignedToAll: Boolean,
  enrolmentFriendlyName: String,
  state: String,
  enrolmentType: String
)
object EnrolmentDetailsTestOnly {
  implicit val format: Format[EnrolmentDetailsTestOnly] = Json.format[EnrolmentDetailsTestOnly]
}
