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
import play.api.mvc.AnyContent
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.auth.RequestWithUserDetails

import scala.concurrent.{ExecutionContext, Future}

class TEASessionCacheImpl @Inject()(
  val sessionRepository: SessionRepository,
  val cascadeUpsert: CascadeUpsert
)(implicit val ec: ExecutionContext)
    extends TEASessionCache {

  def save[A](key: String, value: A)(
    implicit request: RequestWithUserDetails[AnyContent],
    fmt: Format[A]
  ): Future[CacheMap] = {
    sessionRepository().get(request.sessionID).flatMap { optionalCacheMap =>
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
  )(implicit request: RequestWithUserDetails[AnyContent]): Future[Boolean] = {
    sessionRepository().get(request.sessionID).flatMap { optionalCacheMap =>
      optionalCacheMap.fold(Future(false)) { cacheMap =>
        val newCacheMap = cacheMap copy (data = cacheMap.data - key)
        sessionRepository().upsert(newCacheMap)
      }
    }
  }

  def removeAll()(
    implicit request: RequestWithUserDetails[AnyContent]
  ): Future[Boolean] = {
    sessionRepository().upsert(
      CacheMap(request.sessionID, Map("" -> JsString("")))
    )
  }

  def fetch()(
    implicit request: RequestWithUserDetails[AnyContent]
  ): Future[Option[CacheMap]] =
    sessionRepository().get(request.sessionID)

  def getEntry[A](key: String)(
    implicit request: RequestWithUserDetails[AnyContent],
    fmt: Format[A]
  ): Future[Option[A]] = {
    fetch().map { optionalCacheMap =>
      optionalCacheMap.flatMap { cacheMap =>
        cacheMap.getEntry(key)
      }
    }
  }
}

@ImplementedBy(classOf[TEASessionCacheImpl])
trait TEASessionCache {
  def save[A](key: String, value: A)(
    implicit request: RequestWithUserDetails[AnyContent],
    fmt: Format[A]
  ): Future[CacheMap]

  def remove(key: String)(
    implicit request: RequestWithUserDetails[AnyContent]
  ): Future[Boolean]

  def removeAll()(
    implicit request: RequestWithUserDetails[AnyContent]
  ): Future[Boolean]

  def fetch()(
    implicit request: RequestWithUserDetails[AnyContent]
  ): Future[Option[CacheMap]]

  def getEntry[A](key: String)(
    implicit request: RequestWithUserDetails[AnyContent],
    fmt: Format[A]
  ): Future[Option[A]]
}
