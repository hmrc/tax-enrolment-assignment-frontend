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

import akka.Done
import org.scalamock.scalatest.MockFactory
import org.scalatest.OneInstancePerTest
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.Application
import play.api.cache.AsyncCacheApi
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.admin.{FeatureFlagName, PtNinoMismatchCheckerToggle}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.admin.DefaultFeatureFlagRepository

import scala.concurrent.Future

class FeatureFlagServiceSpec extends AnyWordSpec with ScalaFutures with MockFactory with Matchers with OneInstancePerTest {

  val mockFeatureFlagRepository = mock[DefaultFeatureFlagRepository]
  val mockCache = mock[AsyncCacheApi]


  implicit lazy val app: Application = GuiceApplicationBuilder()
    .overrides(
      bind[DefaultFeatureFlagRepository].toInstance(mockFeatureFlagRepository),
      bind[AsyncCacheApi].toInstance(mockCache)
    )
    .build()

  val featureFlagService: FeatureFlagService = app.injector.instanceOf[FeatureFlagService]

  "set" must {
    "set a feature flag" in {
      (mockCache.remove(_: String)).expects(*).returning(Future.successful(Done)).twice
      (mockFeatureFlagRepository
        .setFeatureFlag(_: FeatureFlagName, _: Boolean)
        ).expects(PtNinoMismatchCheckerToggle, true)
        .returning(Future.successful(true)).once

      val result = featureFlagService.set(PtNinoMismatchCheckerToggle, true).futureValue

      result shouldBe true
    }
  }
}
