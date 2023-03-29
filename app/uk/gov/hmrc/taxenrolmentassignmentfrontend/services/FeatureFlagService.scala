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

package uk.gov.hmrc.taxenrolmentassignmentfrontend.services

import play.api.Logging
import play.api.cache.AsyncCacheApi
import uk.gov.hmrc.taxenrolmentassignmentfrontend.config.AppConfig
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.admin.{FeatureFlag, FeatureFlagName}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.admin.FeatureFlagRepository

import javax.inject.{Inject, Singleton}
import scala.concurrent.duration.{Duration, FiniteDuration, SECONDS => Seconds}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class FeatureFlagService @Inject()(
  appConfig: AppConfig,
  featureFlagRepository: FeatureFlagRepository,
  cache: AsyncCacheApi
)(implicit
  ec: ExecutionContext
) extends Logging {
  lazy val cacheValidFor: FiniteDuration   =
    Duration(appConfig.ehCacheTtlInSeconds, Seconds)
  private val allFeatureFlagsCacheKey = "*$*$allFeatureFlags*$*$"

  def set(flagName: FeatureFlagName, enabled: Boolean): Future[Boolean] =
    for {
      _      <- cache.remove(flagName.toString)
      _      <- cache.remove(allFeatureFlagsCacheKey)
      result <- featureFlagRepository.setFeatureFlag(flagName, enabled)
      //blocking thread to let time to other containers to update their cache
      _      <- Future.successful(Thread.sleep(appConfig.ehCacheTtlInSeconds * 1000))
    } yield result

  def get(flagName: FeatureFlagName): Future[FeatureFlag] =
    cache.getOrElseUpdate(flagName.toString, cacheValidFor) {
      featureFlagRepository
        .getFeatureFlag(flagName)
        .map(_.getOrElse(FeatureFlag(flagName, false)))
    }
}
