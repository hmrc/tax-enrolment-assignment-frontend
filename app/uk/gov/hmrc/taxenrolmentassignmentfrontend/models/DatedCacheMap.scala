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

import play.api.libs.json.{Format, JsValue, Json, OFormat}
import uk.gov.hmrc.mongo.play.json.formats.{MongoFormats, MongoJavatimeFormats}

import java.time.Instant

case class CacheMap(id: String, data: Map[String, JsValue])

case class DatedCacheMap(id: String, data: Map[String, JsValue], lastUpdated: Instant = Instant.now())
    extends MongoFormats {

  def toCacheMap: CacheMap =
    CacheMap(this.id, this.data)

}

object DatedCacheMap {
  implicit val dateFormat: Format[Instant]     = MongoJavatimeFormats.instantFormat
  implicit val formats: OFormat[DatedCacheMap] = Json.format[DatedCacheMap]
}
