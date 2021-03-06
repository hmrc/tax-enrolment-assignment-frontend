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

package uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers

import play.api.libs.json.JsString
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve.{Credentials, Retrieval, ~}
import uk.gov.hmrc.auth.core.{AffinityGroup, Enrolments}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.actions.RequestWithUserDetailsFromSession
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.TestData.{predicates, retrievalResponse, retrievals}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.{TestFixture, UrlPaths}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.SessionKeys.REDIRECT_URL

import java.net.URLEncoder
import scala.concurrent.{ExecutionContext, Future}

class SignOutControllerSpec extends TestFixture {

  val controller =
    new SignOutController(mockAuthAction, mcc, appConfig, mockTeaSessionCache, logger)
  def fakeReq(method: String,
              url: String = "N/A"): FakeRequest[AnyContentAsEmpty.type] = {
    FakeRequest(method, url)
      .withSession(
        "sessionId" -> "FAKE_SESSION_ID",
        "X-Request-ID" -> "FakeOtherID"
      )
  }

  "signOut" when {
    "the session contains a redirectUrl" should {
      "clear down the user's data and redirect to signout with continueUrl" in {
        (mockAuthConnector
          .authorise(
            _: Predicate,
            _: Retrieval[
              ((Option[String] ~ Option[Credentials]) ~ Enrolments) ~ Option[
                String
              ] ~ Option[AffinityGroup]
            ]
          )(_: HeaderCarrier, _: ExecutionContext))
          .expects(predicates, retrievals, *, *)
          .returning(Future.successful(retrievalResponse()))

        (mockTeaSessionCache
          .fetch()(
            _: RequestWithUserDetailsFromSession[_]))
          .expects(*)
          .returning(Future.successful(Some(CacheMap("id", Map(REDIRECT_URL -> JsString(UrlPaths.returnUrl))))))

        (mockTeaSessionCache.removeAll()(
          _: RequestWithUserDetailsFromSession[_]))
          .expects(*)
          .returning(Future.successful(true))

        val result = controller.signOut().apply(fakeReq("GET"))

        status(result) shouldBe SEE_OTHER
        headers(result).contains("X-Request-ID") shouldBe false
        redirectLocation(result) shouldBe Some(
          s"http://localhost:9553/bas-gateway/sign-out-without-state?continueUrl=${URLEncoder.encode(UrlPaths.returnUrl, "UTF-8")}"
        )
      }
    }

    "the session exists but does not contain the redirectUrl" should {
      "clear down the user's data and redirect to signout without continueUrl" in {
        (mockAuthConnector
          .authorise(
            _: Predicate,
            _: Retrieval[
              ((Option[String] ~ Option[Credentials]) ~ Enrolments) ~ Option[
                String
              ] ~ Option[AffinityGroup]
            ]
          )(_: HeaderCarrier, _: ExecutionContext))
          .expects(predicates, retrievals, *, *)
          .returning(Future.successful(retrievalResponse()))

        (mockTeaSessionCache
          .fetch()(
            _: RequestWithUserDetailsFromSession[_]))
          .expects(*)
          .returning(Future.successful(Some(CacheMap("id", Map()))))

        (mockTeaSessionCache.removeAll()(
          _: RequestWithUserDetailsFromSession[_]))
          .expects(*)
          .returning(Future.successful(true))

        val result = controller.signOut().apply(fakeReq("GET"))

        status(result) shouldBe SEE_OTHER
        headers(result).contains("X-Request-ID") shouldBe false
        redirectLocation(result) shouldBe Some(
          s"http://localhost:9553/bas-gateway/sign-out-without-state"
        )
      }
    }

    "the session does not exists" should {
      "redirect to signout without continueUrl" in {
        (mockAuthConnector
          .authorise(
            _: Predicate,
            _: Retrieval[
              ((Option[String] ~ Option[Credentials]) ~ Enrolments) ~ Option[
                String
              ] ~ Option[AffinityGroup]
            ]
          )(_: HeaderCarrier, _: ExecutionContext))
          .expects(predicates, retrievals, *, *)
          .returning(Future.successful(retrievalResponse()))

        (mockTeaSessionCache
          .fetch()(
            _: RequestWithUserDetailsFromSession[_]))
          .expects(*)
          .returning(Future.successful(None))

        (mockTeaSessionCache.removeAll()(
          _: RequestWithUserDetailsFromSession[_]))
          .expects(*)
          .returning(Future.successful(true))

        val result = controller.signOut().apply(fakeReq("GET"))

        status(result) shouldBe SEE_OTHER
        headers(result).contains("X-Request-ID") shouldBe false
        redirectLocation(result) shouldBe Some(
          s"http://localhost:9553/bas-gateway/sign-out-without-state"
        )
      }
    }
  }

}
