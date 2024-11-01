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

import org.mockito.ArgumentMatchers.any
import org.mockito.MockitoSugar.{mock, times, verify, when}
import org.mockito.stubbing.ScalaOngoingStubbing
import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.actions.RequestWithUserDetailsFromSession
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.TestData._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.CacheMap
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.SessionKeys.ACCOUNT_TYPE
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.TEASessionCache

import scala.concurrent.Future

trait ControllersBaseSpec extends BaseSpec {

  lazy val mockAuthConnector: AuthConnector = mock[AuthConnector]
  lazy val mockTeaSessionCache: TEASessionCache = mock[TEASessionCache]

  def mockGetDataFromCacheForActionNoRedirectUrl: ScalaOngoingStubbing[Future[Option[CacheMap]]] = {
    val data = Map(ACCOUNT_TYPE -> Json.toJson(randomAccountType))
    val cacheMap = CacheMap("id", data)

    when(mockTeaSessionCache.fetch()(any[RequestWithUserDetailsFromSession[_]]))
      .thenReturn(Future.successful(Some(cacheMap)))
  }

  def mockGetDataFromCacheForActionSuccess(
    accountType: AccountTypes.Value,
    redirectUrl: String = "foo",
    additionCacheData: Map[String, JsValue] = Map()
  ): ScalaOngoingStubbing[Future[Option[CacheMap]]] = {
    val data = generateBasicCacheData(accountType, redirectUrl) ++ additionCacheData
    val cacheMap = CacheMap("id", data)

    when(mockTeaSessionCache.fetch()(any[RequestWithUserDetailsFromSession[_]]))
      .thenReturn(Future.successful(Some(cacheMap)))
  }

  def mockDeleteDataFromCacheWhen: ScalaOngoingStubbing[Future[Boolean]] =
    when(mockTeaSessionCache.removeRecord(any()))
      .thenReturn(Future.successful(true))

  def mockDeleteDataFromCacheVerify: Future[Boolean] =
    verify(mockTeaSessionCache, times(1)).removeRecord(any())
}
