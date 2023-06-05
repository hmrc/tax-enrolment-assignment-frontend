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

package uk.gov.hmrc.taxenrolmentassignmentfrontend.services.admin

//import akka.Done
//import org.scalamock.scalatest.MockFactory
//import org.scalatest.OneInstancePerTest
//import org.scalatest.concurrent.ScalaFutures
//import org.scalatest.matchers.should.Matchers
//import org.scalatest.wordspec.AnyWordSpec
//import play.api.Application
//import play.api.cache.AsyncCacheApi
//import play.api.inject.bind
//import play.api.inject.guice.GuiceApplicationBuilder
//import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.admin.{FeatureFlagName, PtNinoMismatchCheckerToggle}
//import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.admin.FeatureFlagRepository

import akka.Done
import org.scalamock.scalatest.MockFactory
import org.scalatest.OneInstancePerTest
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Span}
import org.scalatest.wordspec.AnyWordSpec
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.{Application, inject}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.admin.{FeatureFlagName, PtNinoMismatchCheckerToggle}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.admin.FeatureFlagRepository

import scala.concurrent.Future

class FeatureFlagServiceSpec
    extends AnyWordSpec with ScalaFutures with MockFactory with Matchers with OneInstancePerTest {

  val mockFeatureFlagRepository = mock[FeatureFlagRepository]
  val mockCache = mock[AsyncCacheApi]

  implicit lazy val app: Application = GuiceApplicationBuilder()
    .overrides(
      inject.bind[FeatureFlagRepository].toInstance(mockFeatureFlagRepository),
      inject.bind[AsyncCacheApi].toInstance(mockCache)
    )
    .configure("ehCache.ttlInSeconds" -> "300")
    .build()

  val featureFlagService: FeatureFlagService = app.injector.instanceOf[FeatureFlagService]

  "set" should {
    "set a feature flag" in {
      (mockCache.remove(_: String)).expects(*).returning(Future.successful(Done)).twice
      (mockFeatureFlagRepository
        .setFeatureFlag(_: FeatureFlagName, _: Boolean))
        .expects(PtNinoMismatchCheckerToggle, true)
        .returning(Future.successful(true))
        .once

      whenReady(featureFlagService.set(PtNinoMismatchCheckerToggle, enabled = true)) { result =>
        result shouldBe true
      }(PatienceConfig.apply(timeout = scaled(Span(15000, Millis)), interval = scaled(Span(15, Millis))), implicitly)
    }
  }
}
