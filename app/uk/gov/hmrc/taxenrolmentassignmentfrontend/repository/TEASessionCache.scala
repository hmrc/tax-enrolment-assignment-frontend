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

import org.mongodb.scala.model.Filters.equal
import org.mongodb.scala.model.Indexes.ascending
import org.mongodb.scala.model.Updates.set
import org.mongodb.scala.model.{IndexModel, IndexOptions, Indexes, ReplaceOptions}
import play.api.Configuration
import play.api.libs.json.Format
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.actions.RequestWithUserDetailsFromSession
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.{CacheMap, DatedCacheMap}
import org.mongodb.scala.SingleObservableFuture

import java.time.{LocalDateTime, ZoneId}
import java.util.concurrent.TimeUnit
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class DefaultTEASessionCache @Inject() (
  config: Configuration,
  val mongo: MongoComponent,
  val cascadeUpsert: CascadeUpsert
)(implicit ec: ExecutionContext)
    extends PlayMongoRepository[DatedCacheMap](
      mongoComponent = mongo,
      collectionName = config.get[String]("appName"),
      domainFormat = DatedCacheMap.formats,
      indexes = Seq(
        IndexModel(
          ascending("lastUpdated"),
          IndexOptions()
            .name("userAnswersExpiry")
            .expireAfter(
              config.get[Int]("mongodb.timeToLiveInSeconds").toLong,
              TimeUnit.SECONDS
            )
        ),
        IndexModel(
          Indexes.ascending("id"),
          IndexOptions()
            .name("teaIdentifierIndex")
            .sparse(true)
            .unique(true)
            .background(true)
        )
      ),
      replaceIndexes = false
    )
    with TEASessionCache {

  def upsert(cm: CacheMap): Future[Boolean]            = {
    val cmUpdated = DatedCacheMap(cm.id, cm.data)
    val options   = ReplaceOptions().upsert(true)
    collection
      .replaceOne(equal("id", cm.id), cmUpdated, options)
      .toFuture()
      .map { result =>
        result.wasAcknowledged()
      }
  }
  def collectionDeleteOne(id: String): Future[Boolean] =
    collection.deleteOne(equal("id", id)).toFuture().map(_.getDeletedCount > 0)

  def get(id: String): Future[Option[CacheMap]] =
    collection.find(equal("id", id)).headOption().map { datedCacheMap =>
      datedCacheMap.map { cachedValue =>
        cachedValue.toCacheMap
      }
    }

  def updateLastUpdated(id: String): Future[Boolean] =
    collection
      .updateOne(
        equal("id", id),
        set("lastUpdated", LocalDateTime.now(ZoneId.of("UTC")))
      )
      .toFuture()
      .map { result =>
        result.wasAcknowledged()
      }

  def save[A](key: String, value: A)(implicit
    request: RequestWithUserDetailsFromSession[_],
    fmt: Format[A]
  ): Future[CacheMap] =
    get(request.sessionID).flatMap { optionalCacheMap =>
      val updatedCacheMap = cascadeUpsert(
        key,
        value,
        optionalCacheMap.getOrElse(CacheMap(request.sessionID, Map()))
      )
      upsert(updatedCacheMap).map { _ =>
        updatedCacheMap
      }
    }

  def removeRecord(implicit
    request: RequestWithUserDetailsFromSession[_]
  ): Future[Boolean] =
    collectionDeleteOne(request.sessionID)

  def fetch()(implicit
    request: RequestWithUserDetailsFromSession[_]
  ): Future[Option[CacheMap]] =
    get(request.sessionID)

  def extendSession()(implicit
    request: RequestWithUserDetailsFromSession[_]
  ): Future[Boolean] =
    updateLastUpdated(request.sessionID)
}

trait TEASessionCache {
  def upsert(cm: CacheMap): Future[Boolean]

  def collectionDeleteOne(id: String): Future[Boolean]

  def get(id: String): Future[Option[CacheMap]]

  def updateLastUpdated(id: String): Future[Boolean]

  def save[A](key: String, value: A)(implicit
    request: RequestWithUserDetailsFromSession[_],
    fmt: Format[A]
  ): Future[CacheMap]

  def removeRecord(implicit
    request: RequestWithUserDetailsFromSession[_]
  ): Future[Boolean]

  def fetch()(implicit
    request: RequestWithUserDetailsFromSession[_]
  ): Future[Option[CacheMap]]

  def extendSession()(implicit
    request: RequestWithUserDetailsFromSession[_]
  ): Future[Boolean]
}
