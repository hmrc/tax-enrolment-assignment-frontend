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

package uk.gov.hmrc.taxenrolmentassignmentfrontend.repositories.admin

import org.mongodb.scala.MongoWriteException
import org.mongodb.scala.bson.BsonDocument
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.TestFixture
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.admin.{FeatureFlag, PtNinoMismatchCheckerToggle}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.admin.FeatureFlagRepository

class FeatureFlagRepositorySpec extends TestFixture with DefaultPlayMongoRepositorySupport[FeatureFlag] {

  override protected lazy val optSchema = Some(BsonDocument("""
      { bsonType: "object"
      , required: [ "_id", "name", "isEnabled" ]
      , properties:
        { _id       : { bsonType: "objectId" }
        , name      : { bsonType: "string" }
        , isEnabled : { bsonType: "bool" }
        , description : { bsonType: "string" }
        }
      }
    """))

  val configValues: Map[String, Any] =
    Map(
      "cookie.encryption.key" -> "gvBoGdgzqG1AarzF1LY0zQ==",
      "sso.encryption.key" -> "gvBoGdgzqG1AarzF1LY0zQ==",
      "queryParameter.encryption.key" -> "gvBoGdgzqG1AarzF1LY0zQ==",
      "json.encryption.key" -> "gvBoGdgzqG1AarzF1LY0zQ==",
      "metrics.enabled" -> false,
      "auditing.enabled" -> false
    )

  override implicit lazy val app = GuiceApplicationBuilder()
    .configure(Map("mongodb.uri" -> mongoUri) ++ configValues)
    .build()

  lazy val repository = inject[FeatureFlagRepository]

  "getFlag" must {
    "return None if there is no record" in {
      val result = repository.getFeatureFlag(PtNinoMismatchCheckerToggle).futureValue

      result shouldBe None
    }
  }

  "setFeatureFlag and getFeatureFlag" must {
    "insert and read a record in mongo" in {

      val result = (for {
        _      <- repository.setFeatureFlag(PtNinoMismatchCheckerToggle, true)
        result <- findAll()
      } yield result).futureValue

      result shouldBe List(
        FeatureFlag(PtNinoMismatchCheckerToggle, true, PtNinoMismatchCheckerToggle.description)
      )
    }
  }

  "setFeatureFlag" must {
    "replace a record not create a new one" in {
      val result = (for {
        _      <- repository.setFeatureFlag(PtNinoMismatchCheckerToggle, true)
        _      <- repository.setFeatureFlag(PtNinoMismatchCheckerToggle, false)
        result <- findAll()
      } yield result).futureValue

      result shouldBe List(
        FeatureFlag(PtNinoMismatchCheckerToggle, false, PtNinoMismatchCheckerToggle.description)
      )
    }
  }

  "deleteFeatureFlag" must {
    "delete a mongo record" in {
      val allFlags: Boolean = (for {
        _      <- insert(FeatureFlag(PtNinoMismatchCheckerToggle, true, PtNinoMismatchCheckerToggle.description))
        result <- repository.deleteFeatureFlag(PtNinoMismatchCheckerToggle)
      } yield result).futureValue

      allFlags shouldBe true
      findAll().futureValue.length shouldBe 0
    }
  }

  "Collection" must {
    "not allow duplicates" in {
      val result = intercept[MongoWriteException] {
        await(for {
          _ <- insert(FeatureFlag(PtNinoMismatchCheckerToggle, true))
          _ <- insert(FeatureFlag(PtNinoMismatchCheckerToggle, false))
        } yield true)
      }
      result.getCode shouldBe 11000
      result.getError.getMessage shouldBe s"""E11000 duplicate key error collection: $databaseName.admin-feature-flags index: name dup key: { name: "$PtNinoMismatchCheckerToggle" }"""
    }
  }
}
