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

case class PersonalTaxEnrolment(userId: String,
                                friendlyName: String,
                                `type`: String,
                                verifiers: List[IdentifiersOrVerifiers]) {
  def this(credId: String, nino: String) = this(
    userId = credId,
    friendlyName = "My Personal Tax Enrolment",
    `type` = "principal",
    verifiers = List(IdentifiersOrVerifiers("NINO1", nino))
  )
}

object PersonalTaxEnrolment {
  implicit val format: Format[PersonalTaxEnrolment] =
    Json.format[PersonalTaxEnrolment]
}
