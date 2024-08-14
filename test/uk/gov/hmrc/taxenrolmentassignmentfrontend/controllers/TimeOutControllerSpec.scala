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

package uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers

import org.jsoup.Jsoup
import org.mockito.MockitoSugar.when
import play.api.Application
import play.api.inject.{Binding, bind}
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.ControllersBaseSpec
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.TestData.{predicates, retrievalResponse, retrievals}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.TEASessionCache
import uk.gov.hmrc.taxenrolmentassignmentfrontend.views.html.TimedOutView
import org.mockito.ArgumentMatchers.{any, eq => ameq}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

class TimeOutControllerSpec extends ControllersBaseSpec {

  override lazy val overrides: Seq[Binding[TEASessionCache]] = Seq(
    bind[TEASessionCache].toInstance(mockTeaSessionCache)
  )

  override implicit lazy val app: Application = localGuiceApplicationBuilder()
    .overrides(
      bind[AuthConnector].toInstance(mockAuthConnector)
    )
    .build()

  lazy val controller: TimeOutController = app.injector.instanceOf[TimeOutController]

  val view: TimedOutView = app.injector.instanceOf[TimedOutView]

  def fakeReq(method: String, url: String = "N/A"): FakeRequest[AnyContentAsEmpty.type] =
    FakeRequest(method, url)
      .withSession(
        "sessionId"    -> "FAKE_SESSION_ID",
        "X-Request-ID" -> "FakeOtherID"
      )

  "keepAlive" should {
    "extend the session and return no content" in {
      when(mockAuthConnector.authorise(ameq(predicates), ameq(retrievals))(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future.successful(retrievalResponse()))

      when(mockTeaSessionCache.extendSession())
        .thenReturn(Future.successful(true))

      val result = controller.keepAlive().apply(fakeReq("GET"))

      status(result) shouldBe NO_CONTENT
    }
  }

  "timeout" should {
    "render the timeout view" in {
      val result = controller.timeout().apply(fakeReq("GET"))

      status(result) shouldBe OK
      val page = Jsoup
        .parse(contentAsString(result))
      page.body().text() should include(messages("timedout.heading"))
    }
  }
}
