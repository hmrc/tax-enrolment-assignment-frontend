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

package uk.gov.hmrc.taxenrolmentassignmentfrontend.repository

import play.api.libs.json._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.CacheMap

import javax.inject.Singleton

@Singleton
class CascadeUpsert {

  private val funcMap: Map[String, (JsValue, CacheMap) => CacheMap] = Map()

  def apply[A](key: String, value: A, originalCacheMap: CacheMap)(implicit fmt: Format[A]): CacheMap =
    funcMap.get(key).fold(store(key, value, originalCacheMap))(fn => fn(Json.toJson(value), originalCacheMap))

  private def store[A](key: String, value: A, cacheMap: CacheMap)(implicit fmt: Format[A]): CacheMap =
    cacheMap copy (data = cacheMap.data + (key -> Json.toJson(value)))
}
