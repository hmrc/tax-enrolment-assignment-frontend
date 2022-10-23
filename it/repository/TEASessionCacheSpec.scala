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

package repository

import helpers.IntegrationSpecBase
import helpers.TestITData._
import play.api.libs.json.JsString
import play.api.mvc.{AnyContent, Request}
import play.api.test.FakeRequest
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.actions.RequestWithUserDetailsFromSession
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.TEASessionCacheImpl

class TEASessionCacheSpec extends IntegrationSpecBase {

  val teaSessionCache =
    new TEASessionCacheImpl(sessionRepository, cascadeUpsert)

  implicit val request: RequestWithUserDetailsFromSession[AnyContent] =
    new RequestWithUserDetailsFromSession[AnyContent](
      FakeRequest().asInstanceOf[Request[AnyContent]],
      userDetailsNoEnrolments,
      sessionId
    )

  "save" when {
    "there is no record in the database" should {
      "upsert the data to mongo" in {
        val dataToUpsert = CacheMap(sessionId, Map("test" -> JsString("abc")))

        val res = teaSessionCache.save("test", "abc")

        whenReady(res) { result =>
          result shouldBe dataToUpsert
        }
      }
    }

    "there is data in database" should {
      "overrride the value then upsert" when {
        "the key already exists" in {
          val initialData = CacheMap(sessionId, Map("test" -> JsString("abc")))
          val dataToUpsert = CacheMap(sessionId, Map("test" -> JsString("efg")))

          val res = for {
            _ <- sessionRepository().upsert(initialData)
            updated <- teaSessionCache.save("test", "efg")
          } yield updated

          whenReady(res) { result =>
            result shouldBe dataToUpsert
          }
        }
      }

      "add the value to map and then upsert" when {
        "the key does not already exists" in {
          val initialData = CacheMap(sessionId, Map("test" -> JsString("abc")))
          val dataToUpsert = CacheMap(
            sessionId,
            Map("test" -> JsString("abc"), "test1" -> JsString("efg"))
          )

          val res = for {
            _ <- sessionRepository().upsert(initialData)
            updated <- teaSessionCache.save("test1", "efg")
          } yield updated

          whenReady(res) { result =>
            result shouldBe dataToUpsert
          }
        }
      }
    }
  }

  "remove" when {
    "the session cache is empty" should {
      "return false" in {

        val res = teaSessionCache.remove("test")

        whenReady(res) { result =>
          result shouldBe false
        }
      }
    }

    "the session cache contains multiple keys" should {
      "remove the selected key and return true" in {
        val data = CacheMap(
          sessionId,
          Map("test" -> JsString("abc"), "test1" -> JsString("efg"))
        )
        val dataWithKeyRemoved =
          CacheMap(sessionId, Map("test1" -> JsString("efg")))
        val res = for {
          _ <- sessionRepository().upsert(data)
          remove <- teaSessionCache.remove("test")
          fetched <- fetch(sessionId)
        } yield (remove, fetched)

        whenReady(res) {
          case (remove, fetched) =>
            remove shouldBe true
            fetched shouldBe Some(dataWithKeyRemoved)
        }
      }
    }

    "the session cache contains only the selected key" should {
      "remove the selected key only and return true" in {
        val data = CacheMap(sessionId, Map("test" -> JsString("abc")))
        val res = for {
          _ <- sessionRepository().upsert(data)
          remove <- teaSessionCache.remove("test")
          fetched <- fetch(sessionId)
        } yield (remove, fetched)

        whenReady(res) {
          case (remove, fetched) =>
            remove shouldBe true
            fetched shouldBe Some(CacheMap(sessionId, Map()))
        }
      }
    }
  }

  "removeRecord" should {
    "remove all the data for session id and return true" in {
      val data = CacheMap(sessionId, Map("test" -> JsString("abc")))
      val res = for {
        _ <- sessionRepository().upsert(data)
        remove <- teaSessionCache.removeRecord
        fetched <- fetch(sessionId)
      } yield (remove, fetched)

      whenReady(res) {
        case (remove, fetched) =>
          remove shouldBe true
          fetched shouldBe None
      }
    }
    "remove all the data for session id and return true but leave another record with another sessionid" in {
      val data = CacheMap("fooboochoo", Map("test" -> JsString("abc")))
      val dataToBeDeleted = CacheMap(sessionId, Map("test" -> JsString("abc")))
      val res = for {
        _ <- sessionRepository().upsert(data)
        _ <- sessionRepository().upsert(dataToBeDeleted)
        remove <- teaSessionCache.removeRecord
        fetched <- fetch("fooboochoo")
      } yield (remove, fetched)

      whenReady(res) {
        case (remove, fetched) =>
          remove shouldBe true
          fetched shouldBe Some(data)
      }
    }
    "no data exists return false" in {
      val res = for {
        remove <- teaSessionCache.removeRecord
        fetched <- fetch(sessionId)
      } yield (remove, fetched)

      whenReady(res) {
        case (remove, fetched) =>
          remove shouldBe false
          fetched shouldBe None
      }
    }
  }

  "fetch" when {
    "the session cache is empty" should {
      "return None" in {
        val res = teaSessionCache.fetch()

        whenReady(res) { result =>
          result shouldBe None
        }
      }
    }

    "the session cache contains data" should {
      "return the data" in {
        val data = CacheMap(
          sessionId,
          Map("test" -> JsString("abc"), "test1" -> JsString("efg"))
        )
        val res = for {
          _ <- sessionRepository().upsert(data)
          fetched <- teaSessionCache.fetch()
        } yield fetched

        whenReady(res) { result =>
          result shouldBe Some(data)
        }
      }
    }
  }

  "extendSession" when {
    "there is no record in the database" should {
      "upsert empty data to mongo and return true" in {
        val res = teaSessionCache.extendSession()

        whenReady(res) { result =>
          result shouldBe true
        }
      }
    }

    "there is data in database" should {
      "just override the last login date" in {
        val data = CacheMap(sessionId, Map("test" -> JsString("abc")))
        val res = for {
          _ <- sessionRepository().upsert(data)
          updated <- teaSessionCache.extendSession()
        } yield updated

        whenReady(res) { result =>
          result shouldBe true
        }
      }
    }
  }
}
