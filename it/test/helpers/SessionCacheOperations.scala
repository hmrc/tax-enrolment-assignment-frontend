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

package helpers

import org.mongodb.scala.bson.BsonDocument
import org.mongodb.scala.model.Filters
import play.api.libs.json.{Format, JsString, JsValue}
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.DatedCacheMap
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.{CascadeUpsert, DefaultTEASessionCache}

import java.time.Instant
import scala.concurrent.Future

trait SessionCacheOperations extends DefaultPlayMongoRepositorySupport[DatedCacheMap] {
  _: IntegrationSpecBase =>

  override protected lazy val optSchema: Option[BsonDocument] = Some(BsonDocument("""
      { bsonType: "object"
      , required: [ "_id", "data", "lastUpdated" ]
      , properties:
        { _id       : { bsonType: "objectId" }
        , data      : { bsonType: "object" }
        , lastUpdated : { bsonType: "date" }
        }
      }
    """))

  lazy val sessionRepository: DefaultTEASessionCache = app.injector.instanceOf[DefaultTEASessionCache]
  lazy val cascadeUpsert: CascadeUpsert = app.injector.instanceOf[CascadeUpsert]
  lazy val repository: DefaultTEASessionCache = inject[DefaultTEASessionCache]

  def save[T](sessionID: String, key: String, value: T)(implicit
    fmt: Format[T]
  ): Future[CacheMap] =
    sessionRepository.get(sessionID).flatMap { optionalCacheMap =>
      val updatedCacheMap = cascadeUpsert(
        key,
        value,
        optionalCacheMap.getOrElse(CacheMap(sessionID, Map()))
      )
      sessionRepository.upsert(updatedCacheMap).map { _ =>
        updatedCacheMap
      }
    }

  def recordExistsInMongo: Boolean =
    sessionRepository.collection.find(Filters.empty()).headOption().map(_.isDefined).futureValue

  def save(sessionId: String, dataMap: Map[String, JsValue]): Future[Boolean] =
    sessionRepository.upsert(CacheMap(sessionId, dataMap))

  def removeAll(sessionID: String): Future[Boolean] =
    sessionRepository.upsert(CacheMap(sessionID, Map("" -> JsString(""))))

  def fetch(sessionID: String): Future[Option[CacheMap]] =
    sessionRepository.get(sessionID)

  def getEntry[A](sessionID: String, key: String)(implicit fmt: Format[A]): Future[Option[A]] =
    fetch(sessionID).map { optionalCacheMap =>
      optionalCacheMap.flatMap { cacheMap =>
        cacheMap.getEntry(key)
      }
    }

  def getLastLoginDateTime(sessionID: String): Instant =
    sessionRepository.collection
      .find(Filters.equal("id", sessionID))
      .first()
      .toFuture()
      .map(_.lastUpdated)
      .futureValue
}
