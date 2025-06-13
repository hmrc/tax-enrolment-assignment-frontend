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

package uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.actions

import org.mockito.ArgumentMatchers.{any, eq as ameq}
import org.mockito.Mockito.when
import org.scalatest.matchers.must.Matchers.mustBe
import play.api.Application
import play.api.inject.{Binding, bind}
import play.api.libs.json.{JsString, Json}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes.SINGLE_ACCOUNT
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.BaseSpec
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.TestData.*
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.CacheMap
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.SessionKeys.{ACCOUNT_TYPE, REDIRECT_URL}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.TEASessionCache

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}

class AuthJourneySpec extends BaseSpec {
  private val mockTeaSessionCache: TEASessionCache           = mock[TEASessionCache]
  private val mockAuthConnector: AuthConnector               = mock[AuthConnector]
  override lazy val overrides: Seq[Binding[TEASessionCache]] = Seq(
    bind[TEASessionCache].toInstance(mockTeaSessionCache)
  )

  override implicit lazy val app: Application = localGuiceApplicationBuilder()
    .overrides(
      bind[AuthConnector].toInstance(mockAuthConnector)
    )
    .build()

  "accountDetailsFromMongo" must {
    "provide a request with account details from Mongo" in {
      val cacheMap =
        CacheMap("id", Map(ACCOUNT_TYPE -> Json.toJson(randomAccountType), REDIRECT_URL -> JsString("foo")))
      when(mockTeaSessionCache.fetch()(any[RequestWithUserDetailsFromSession[_]]))
        .thenReturn(Future.successful(Some(cacheMap)))
      when(mockAuthConnector.authorise(ameq(predicates), ameq(retrievals))(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future.successful(retrievalResponse()))

      val sut = app.injector.instanceOf[AuthJourney]

      val result = Await.result(
        sut.accountDetailsFromMongo(requestWithUserDetails()),
        Duration.Inf
      )

      result.map(_.userDetails) mustBe Right(userDetailsNoEnrolments)
      result.map(_.sessionID) mustBe Right("sessionId")

      val expAccountDetailsFromMongo =
        AccountDetailsFromMongo(
          SINGLE_ACCOUNT,
          "foo",
          Map(
            "ACCOUNT_TYPE" -> JsString("SINGLE_ACCOUNT"),
            "redirectURL"  -> JsString("foo")
          )
        )(crypto.crypto)
      result.map(_.accountDetailsFromMongo) mustBe Right(expAccountDetailsFromMongo)
    }
  }
}
