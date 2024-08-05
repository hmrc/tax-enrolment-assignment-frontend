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

package uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers

import org.scalamock.handlers.{CallHandler1, CallHandler2}
import play.api.libs.json.{JsObject, JsValue, Json}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.TestData._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.UserAnswers
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.JourneyCacheRepository
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.SessionKeys.ACCOUNT_TYPE

import scala.concurrent.Future

trait ControllersBaseSpec extends BaseSpec {

  lazy val mockAuthConnector: AuthConnector = mock[AuthConnector]
  lazy val mockRepository: JourneyCacheRepository = mock[JourneyCacheRepository]

  def mockGetDataFromCacheForActionNoRedirectUrl: CallHandler1[UserAnswers, Future[Boolean]] = {
    val data = Map(ACCOUNT_TYPE -> Json.toJson(randomAccountType))
    val userAnswers: UserAnswers = UserAnswers(
      request.sessionID,
      generateNino.nino,
      Json.toJson(data).as[JsObject]
    )
    (mockRepository
      .set(_: UserAnswers))
      .expects(userAnswers)
      .returning(Future.successful(true))
      .once()
  }

  def mockGetDataFromCacheForActionSuccess(
    accountType: AccountTypes.Value,
    redirectUrl: String = "foo",
    additionCacheData: Map[String, JsValue] = Map()
  ): CallHandler1[UserAnswers, Future[Boolean]] = {
    val data = generateBasicCacheData(accountType, redirectUrl) ++ additionCacheData
    val userAnswers: UserAnswers = UserAnswers(
      request.sessionID,
      generateNino.nino,
      Json.toJson(data).as[JsObject]
    )

    (mockRepository
      .set(_: UserAnswers))
      .expects(userAnswers)
      .returning(Future.successful(true))

  }

  def mockDeleteDataFromCache: CallHandler2[String, String, Future[Boolean]] =
    (mockRepository
      .clear(_: String, _: String))
      .expects(*, *)
      .returning(Future.successful(true))
      .once()
}
