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

package repository

import helpers.IntegrationSpecBase
import org.mongodb.scala.model.Filters
import play.api.libs.json.JsString
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.{CacheMap, DatedCacheMap}
import org.mongodb.scala.SingleObservableFuture

import java.time.{Duration, Instant}
import java.util.UUID

class repositoryISpec extends IntegrationSpecBase {

  "upsert" when {
    "there is no data in the cache" should {
      "create a new mongo record and return true" in {
        val sessionId = UUID.randomUUID().toString
        val KEY       = "testing"
        val data      = "example"

        val cacheMap = CacheMap(sessionId, Map(KEY -> JsString(data)))

        val res = for {
          saved   <- repository.upsert(cacheMap)
          fetched <- fetch(sessionId)
        } yield (saved, fetched)

        whenReady(res) { case (saved, fetched) =>
          saved   shouldBe true
          fetched shouldBe Some(cacheMap)
        }
      }
    }

    "there is already data in the cache" should {
      "override the current value and return true" in {
        val sessionId   = UUID.randomUUID().toString
        val KEY         = "testing"
        val currentData = "example"
        val newData     = "example 1"

        val initialCacheMap =
          CacheMap(sessionId, Map(KEY -> JsString(currentData)))
        val updatedCacheMap = CacheMap(sessionId, Map(KEY -> JsString(newData)))

        val res = for {
          _              <- save[String](sessionId, KEY, currentData)
          fetchedCurrent <- fetch(sessionId)
          updated        <- repository.upsert(updatedCacheMap)
          fetchedUpdated <- fetch(sessionId)
        } yield (fetchedCurrent, updated, fetchedUpdated)

        whenReady(res) { case (fetchedInitial, updated, fetchedUpdated) =>
          fetchedInitial shouldBe Some(initialCacheMap)
          updated        shouldBe true
          fetchedUpdated shouldBe Some(updatedCacheMap)
        }
      }
    }
  }

  "get" when {
    "there is no record" should {
      "return None" in {
        val sessionId = UUID.randomUUID().toString
        val res       = repository.get(sessionId)

        whenReady(res) { result =>
          result shouldBe None
        }
      }
    }

    "a record exists" should {
      "return the cacheMap" in {
        val sessionId = UUID.randomUUID().toString
        val KEY       = "testing"
        val data      = "example"

        val expectedCacheMap = CacheMap(sessionId, Map(KEY -> JsString(data)))

        val res = for {
          saved   <- save[String](sessionId, KEY, data)
          fetched <- repository.get(sessionId)
        } yield (saved, fetched)

        whenReady(res) { case (saved, fetched) =>
          saved   shouldBe expectedCacheMap
          fetched shouldBe Some(expectedCacheMap)
        }
      }
    }
  }

  "updateLastUpdated" when {
    "there is no record" should {
      "create a new record with no data" in {
        val sessionId = UUID.randomUUID().toString
        val res       = repository.updateLastUpdated(sessionId)

        whenReady(res) { result =>
          result shouldBe true
        }
      }
    }

    "a record exists" should {
      "update the lastLoginDate" in {
        val sessionId = UUID.randomUUID().toString
        val KEY       = "testing"
        val data      = "example"

        val oldDatetime    = Instant.now().minus(Duration.ofMinutes(1))
        val cacheMap       = CacheMap(sessionId, Map(KEY -> JsString(data)))
        val datedCachedMap =
          DatedCacheMap(sessionId, cacheMap.data, oldDatetime)
        val res            = for {
          _                 <- repository.collection
                                 .insertOne(datedCachedMap)
                                 .toFuture()
          getOriginal       <- repository.collection
                                 .find(Filters.equal("id", sessionId))
                                 .first()
                                 .toFuture()
          updateLastUpdated <- repository.updateLastUpdated(sessionId)
          getUpdated        <- repository.collection
                                 .find(Filters.equal("id", sessionId))
                                 .first()
                                 .toFuture()
        } yield (getOriginal, updateLastUpdated, getUpdated)

        whenReady(res) { case (getOriginal, updateLastUpdated, getUpdated) =>
          updateLastUpdated       shouldBe true
          getUpdated.lastUpdated shouldNot equal(getOriginal.lastUpdated)
        }
      }
    }
  }
}
