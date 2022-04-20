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

import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.Enrolments
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve.{Credentials, Retrieval, ~}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.TestData.{predicates, retrievalResponse, retrievals}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.TestFixture

import scala.concurrent.{ExecutionContext, Future}

class SignOutControllerSpec extends TestFixture {

  val teaSessionCache = new TestTeaSessionCache
  val controller =
    new SignOutController(mockAuthAction, mcc, appConfig, teaSessionCache)
  def fakeReq(method: String,
              url: String = "N/A"): FakeRequest[AnyContentAsEmpty.type] = {
    FakeRequest(method, url)
      .withSession(
        "sessionId" -> "FAKE_SESSION_ID",
        "X-Request-ID" -> "FakeOtherID"
      )
  }

  "signOut" should {
    "clear down the user's data and sign them out" in {
      (mockAuthConnector
        .authorise(
          _: Predicate,
          _: Retrieval[
            ((Option[String] ~ Option[Credentials]) ~ Enrolments) ~ Option[
              String
            ]
          ]
        )(_: HeaderCarrier, _: ExecutionContext))
        .expects(predicates, retrievals, *, *)
        .returning(Future.successful(retrievalResponse()))

      val result = controller.signOut().apply(fakeReq("GET"))

      status(result) shouldBe SEE_OTHER
      headers(result).contains("X-Request-ID") shouldBe false
      redirectLocation(result) shouldBe Some(
        "http://localhost:9553/bas-gateway/sign-out-without-state"
      )
    }
  }

}
