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

package uk.gov.hmrc.taxenrolmentassignmentfrontend.models.formats

import play.api.libs.json.{JsValue, Json, Writes}
import play.api.libs.ws.BodyWritable
import uk.gov.hmrc.auth.core.{Enrolment, EnrolmentIdentifier}

object EnrolmentsFormats {
  private implicit val enrolmentIdentifierWrites: Writes[EnrolmentIdentifier] = Json.writes[EnrolmentIdentifier]
  implicit val writes: Writes[Set[Enrolment]]                                 = Writes.set(Json.writes[Enrolment])
  implicit def jsonBodyWritable[T](implicit
    writes: Writes[T],
    jsValueBodyWritable: BodyWritable[JsValue]
  ): BodyWritable[T] = jsValueBodyWritable.map(writes.writes)
}
