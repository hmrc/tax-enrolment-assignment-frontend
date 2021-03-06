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

import org.jsoup.Jsoup
import play.api.mvc.{AnyContent, AnyContentAsEmpty}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.{AffinityGroup, Enrolments}
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve.{Credentials, Retrieval, ~}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.actions.RequestWithUserDetailsFromSession
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.TestData.{
  predicates,
  retrievalResponse,
  retrievals
}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.TestFixture
import uk.gov.hmrc.taxenrolmentassignmentfrontend.views.html.TimedOutView

import scala.concurrent.{ExecutionContext, Future}

class TimeOutControllerSpec extends TestFixture {

  val view: TimedOutView = app.injector.instanceOf[TimedOutView]
  val controller =
    new TimeOutController(mockAuthAction, mcc, mockTeaSessionCache, view)

  def fakeReq(method: String,
              url: String = "N/A"): FakeRequest[AnyContentAsEmpty.type] = {
    FakeRequest(method, url)
      .withSession(
        "sessionId" -> "FAKE_SESSION_ID",
        "X-Request-ID" -> "FakeOtherID"
      )
  }

  "keepAlive" should {
    "extend the session and return no content" in {
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
        .extendSession()(_: RequestWithUserDetailsFromSession[AnyContent]))
        .expects(*)
        .returning(Future.successful(true))

      val result = controller.keepAlive().apply(fakeReq("GET"))

      status(result) shouldBe NO_CONTENT
    }
  }

  "timeout" should {
    "render the timeout view" in {
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
        .removeAll()(_: RequestWithUserDetailsFromSession[AnyContent]))
        .expects(*)
        .returning(Future.successful(true))

      val result = controller.timeout().apply(fakeReq("GET"))

      status(result) shouldBe OK
      val page = Jsoup
        .parse(contentAsString(result))
      page.body().text() should include("timedout.heading")
    }
  }

}
