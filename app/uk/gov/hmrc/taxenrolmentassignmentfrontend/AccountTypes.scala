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

package uk.gov.hmrc.taxenrolmentassignmentfrontend

import play.api.libs.json.{Format, Json, Reads}

object AccountTypes extends Enumeration {
  val PT_ASSIGNED_TO_CURRENT_USER: AccountTypes.Value = Value(
    "PT_ASSIGNED_TO_CURRENT_USER"
  )
  val PT_ASSIGNED_TO_OTHER_USER: AccountTypes.Value   = Value(
    "PT_ASSIGNED_TO_OTHER_USER"
  )
  val SINGLE_ACCOUNT: AccountTypes.Value              = Value("SINGLE_ACCOUNT")
  val MULTIPLE_ACCOUNTS: AccountTypes.Value           = Value("MULTIPLE_ACCOUNTS")

  val SA_ASSIGNED_TO_CURRENT_USER: AccountTypes.Value = Value(
    "SA_ASSIGNED_TO_CURRENT_USER"
  )
  val SA_ASSIGNED_TO_OTHER_USER: AccountTypes.Value   = Value(
    "SA_ASSIGNED_TO_OTHER_USER"
  )
  implicit val read: Reads[AccountTypes.Value]        =
    Reads.enumNameReads(AccountTypes)

  implicit val format: Format[AccountTypes.Value] = Json.formatEnum(AccountTypes)
}
