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

package controllers

import helpers.TestITData.{authoriseResponseJson, sessionId, xAuthToken, xSessionId}
import helpers.messages.TimedOutMessages
import helpers.{IntegrationSpecBase, ItUrlPaths}
import org.jsoup.Jsoup
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.mockito.stubbing.OngoingStubbing
import play.api.http.Status.{NO_CONTENT, OK}
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, contentAsString, defaultAwaitTimeout, route, status, writeableOf_AnyContentAsEmpty}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.UserAnswers
import uk.gov.hmrc.taxenrolmentassignmentfrontend.pages.RedirectUrlPage

import scala.concurrent.Future

class TimeOutControllerISpec extends IntegrationSpecBase {

  val urlPathKeepAlive: String = ItUrlPaths.keepAlive
  val urlPathTimeout: String = ItUrlPaths.timeout

  def saveToSessionAndGetLastLoginDate: OngoingStubbing[Future[Option[UserAnswers]]] = {
    val mockUserAnswers = UserAnswers(sessionId, generateNino.nino)
      .setOrException(RedirectUrlPage, returnUrl)

    when(mockJourneyCacheRepository.get(any(), any()))
      .thenReturn(Future.successful(Some(mockUserAnswers)))
  }

  s"GET $urlPathKeepAlive" should {
    "extend the session cache and return NoContent" in {
      saveToSessionAndGetLastLoginDate
      stubPost(s"/write/.*", OK, """{"x":2}""")
      val authResponse = authoriseResponseJson()
      stubAuthorizePost(OK, authResponse.toString())
      Thread.sleep(2000)

      val request = FakeRequest(GET, "/protect-tax-info" + urlPathKeepAlive)
        .withSession(xAuthToken, xSessionId)
      val result = route(app, request).get

      status(result) shouldBe NO_CONTENT
    }
  }

  s"GET $urlPathTimeout" should {
    "return OK and render the timeout page" in {
      val request = FakeRequest(GET, "/protect-tax-info" + urlPathTimeout)
        .withSession(xAuthToken, xSessionId)
      val result = route(app, request).get

      status(result) shouldBe OK
      val page = Jsoup.parse(contentAsString(result))
      page.title should include(TimedOutMessages.title)

    }
  }
}
