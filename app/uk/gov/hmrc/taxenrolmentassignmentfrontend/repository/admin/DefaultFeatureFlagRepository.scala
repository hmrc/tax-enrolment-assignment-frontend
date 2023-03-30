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

package uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.admin

import org.mongodb.scala.model.Filters._
import org.mongodb.scala.model._
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.{Codecs, PlayMongoRepository}
import uk.gov.hmrc.mongo.transaction.{TransactionConfiguration, Transactions}
import uk.gov.hmrc.play.http.logging.Mdc
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.admin.{FeatureFlag, FeatureFlagName}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class DefaultFeatureFlagRepository @Inject()(
  val mongoComponent: MongoComponent
)(implicit
  ec: ExecutionContext
) extends PlayMongoRepository[FeatureFlag](
      collectionName = "admin-feature-flags",
      mongoComponent = mongoComponent,
      domainFormat = FeatureFlag.format,
      indexes = Seq(
        IndexModel(
          keys = Indexes.ascending("name"),
          indexOptions = IndexOptions()
            .name("name")
            .unique(true)
        )
      ),
      extraCodecs = Codecs.playFormatSumCodecs(FeatureFlagName.formats)
    )
    with Transactions with FeatureFlagRepository {

  private implicit val tc: TransactionConfiguration = TransactionConfiguration.strict

  def deleteFeatureFlag(name: FeatureFlagName): Future[Boolean] =
    collection
      .deleteOne(Filters.equal("name", name.toString))
      .map(_.wasAcknowledged())
      .toSingle()
      .toFuture()

  def getFeatureFlag(name: FeatureFlagName): Future[Option[FeatureFlag]] =
    Mdc.preservingMdc(
      collection
        .find(Filters.equal("name", name.toString))
        .headOption()
    )

  def setFeatureFlag(name: FeatureFlagName, enabled: Boolean): Future[Boolean] =
    Mdc.preservingMdc(
      collection
        .replaceOne(
          filter = equal("name", name),
          replacement = FeatureFlag(name, enabled, name.description),
          options = ReplaceOptions().upsert(true)
        )
        .map(_.wasAcknowledged())
        .toSingle()
        .toFuture()
    )
}

trait FeatureFlagRepository {
  def deleteFeatureFlag(name: FeatureFlagName): Future[Boolean]

  def getFeatureFlag(name: FeatureFlagName): Future[Option[FeatureFlag]]

  def setFeatureFlag(name: FeatureFlagName, enabled: Boolean): Future[Boolean]
}
