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

import org.scalamock.handlers.{CallHandler1, CallHandler5}
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{Action, AnyContent}
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve.{Credentials, Retrieval, ~}
import uk.gov.hmrc.auth.core.{AffinityGroup, AuthConnector, Enrolment, Enrolments}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.service.TEAFResult
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.actions.RequestWithUserDetailsFromSession
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.UnexpectedError
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.TestData._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.SessionKeys.ACCOUNT_TYPE
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.TEASessionCache

import scala.concurrent.{ExecutionContext, Future}

trait ControllersBaseSpec extends BaseSpec {

  lazy val mockAuthConnector = mock[AuthConnector]
  lazy val mockTeaSessionCache = mock[TEASessionCache]

  def mockGetDataFromCacheForActionNoRedirectUrl = {
    val data = Map(ACCOUNT_TYPE -> Json.toJson(randomAccountType))
    val cacheMap = CacheMap("id", data)
    (mockTeaSessionCache
      .fetch()(
        _: RequestWithUserDetailsFromSession[_]
      ))
      .expects(*)
      .returning(Future.successful(Some(cacheMap)))
  }

  def mockGetDataFromCacheForActionSuccess(
    accountType: AccountTypes.Value,
    redirectUrl: String = "foo",
    additionCacheData: Map[String, JsValue] = Map()
  ) = {
    val data = generateBasicCacheData(accountType, redirectUrl) ++ additionCacheData
    val cacheMap = CacheMap("id", data)
    (mockTeaSessionCache
      .fetch()(
        _: RequestWithUserDetailsFromSession[_]
      ))
      .expects(*)
      .returning(Future.successful(Some(cacheMap)))
  }

  def mockDeleteDataFromCache: CallHandler1[RequestWithUserDetailsFromSession[_], Future[Boolean]] =
    (mockTeaSessionCache
      .removeRecord(_: RequestWithUserDetailsFromSession[_]))
      .expects(*)
      .returning(Future.successful(true))
      .once()

}
