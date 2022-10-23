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

package uk.gov.hmrc.taxenrolmentassignmentfrontend.repository

import com.google.inject.{ImplementedBy, Inject}
import play.api.libs.json.{Format, JsString}
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.actions.RequestWithUserDetailsFromSession

import scala.concurrent.{ExecutionContext, Future}

class TEASessionCacheImpl @Inject()(
  val sessionRepository: SessionRepository,
  val cascadeUpsert: CascadeUpsert
)(implicit val ec: ExecutionContext)
    extends TEASessionCache {

  val sessionRepo: MongoRepository = sessionRepository()



  def save[A](key: String, value: A)(
    implicit request: RequestWithUserDetailsFromSession[_],
    fmt: Format[A]
  ): Future[CacheMap] = {
    sessionRepo.get(request.sessionID).flatMap { optionalCacheMap =>
      val updatedCacheMap = cascadeUpsert(
        key,
        value,
        optionalCacheMap.getOrElse(CacheMap(request.sessionID, Map()))
      )
      sessionRepository().upsert(updatedCacheMap).map { _ =>
        updatedCacheMap
      }
    }
  }

  def remove(
    key: String
  )(implicit request: RequestWithUserDetailsFromSession[_]): Future[Boolean] = {
    sessionRepo.get(request.sessionID).flatMap { optionalCacheMap =>
      optionalCacheMap.fold(Future(false)) { cacheMap =>
        val newCacheMap = cacheMap copy (data = cacheMap.data - key)
        sessionRepo.upsert(newCacheMap)
      }
    }
  }

  def removeRecord(
    implicit request: RequestWithUserDetailsFromSession[_]
  ): Future[Boolean] = {
    sessionRepo.removeRecord(request.sessionID)
  }

  def fetch()(
    implicit request: RequestWithUserDetailsFromSession[_]
  ): Future[Option[CacheMap]] =
    sessionRepo.get(request.sessionID)

  def extendSession()(
    implicit request: RequestWithUserDetailsFromSession[_]
  ): Future[Boolean] = {
    sessionRepo.updateLastUpdated(request.sessionID)
  }
}

@ImplementedBy(classOf[TEASessionCacheImpl])
trait TEASessionCache {
  def save[A](key: String, value: A)(
    implicit request: RequestWithUserDetailsFromSession[_],
    fmt: Format[A]
  ): Future[CacheMap]

  def remove(key: String)(
    implicit request: RequestWithUserDetailsFromSession[_]
  ): Future[Boolean]

  def removeRecord(
    implicit request: RequestWithUserDetailsFromSession[_]
  ): Future[Boolean]

  def fetch()(
    implicit request: RequestWithUserDetailsFromSession[_]
  ): Future[Option[CacheMap]]

  def extendSession()(
    implicit request: RequestWithUserDetailsFromSession[_]
  ): Future[Boolean]
}
