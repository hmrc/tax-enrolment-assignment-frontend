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

import helpers.TestITData.{authoriseResponseJson, xAuthToken, xSessionId}
import helpers.{IntegrationSpecBase, ItUrlPaths}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import play.api.http.Status.{OK, SEE_OTHER}
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, defaultAwaitTimeout, redirectLocation, route, status, writeableOf_AnyContentAsEmpty}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.UserAnswers
import uk.gov.hmrc.taxenrolmentassignmentfrontend.pages.RedirectUrlPage

import java.net.URLEncoder
import scala.concurrent.Future

class SignOutControllerISpec extends IntegrationSpecBase {

  val url: String = ItUrlPaths.signout

  def saveRedirectUrlToSession(optRedirectUrl: Option[String]) = {
    val mockUserAnswers = UserAnswers("id", generateNino.nino)
    val mockUserAnswersUpdated: UserAnswers = optRedirectUrl match {
      case Some(redirectUrl) =>
        mockUserAnswers.setOrException(RedirectUrlPage, redirectUrl)
      case None => mockUserAnswers
    }
    when(mockJourneyCacheRepository.get(any(), any()))
      .thenReturn(Future.successful(Some(mockUserAnswersUpdated)))
  }

  s"GET $url" when {
    "the session cache contains a redirectUrl" should {
      "remove the session and redirect to gg signout with continue url" in {
        saveRedirectUrlToSession(Some(returnUrl))
        stubPost(s"/write/.*", OK, """{"x":2}""")
        val authResponse = authoriseResponseJson()
        stubAuthorizePost(OK, authResponse.toString())

        val request = FakeRequest(GET, "/protect-tax-info" + url)
          .withSession(xAuthToken, xSessionId)
        val result = route(app, request).get

        status(result) shouldBe SEE_OTHER
        redirectLocation(result).get should include(
          s"/bas-gateway/sign-out-without-state?continue=${URLEncoder.encode(returnUrl, "UTF-8")}"
        )
      }
    }

    "the session cache does not contain a redirectUrl" should {
      "remove the session and redirect to gg signout without continue url" in {
        saveRedirectUrlToSession(None)
        stubPost(s"/write/.*", OK, """{"x":2}""")
        val authResponse = authoriseResponseJson()
        stubAuthorizePost(OK, authResponse.toString())

        val request = FakeRequest(GET, "/protect-tax-info" + url)
          .withSession(xAuthToken, xSessionId)
        val result = route(app, request).get

        status(result) shouldBe SEE_OTHER
        redirectLocation(result).get should include(
          s"/bas-gateway/sign-out-without-state"
        )

      }
    }

    "the session cache does not contain a redirectUrl" should {
      "redirect to gg signout without continue url" in {
        saveRedirectUrlToSession(None)
        stubPost(s"/write/.*", OK, """{"x":2}""")
        val authResponse = authoriseResponseJson()
        stubAuthorizePost(OK, authResponse.toString())

        val request = FakeRequest(GET, "/protect-tax-info" + url)
          .withSession(xAuthToken, xSessionId)
        val result = route(app, request).get

        status(result) shouldBe SEE_OTHER
        redirectLocation(result).get should include(
          s"/bas-gateway/sign-out-without-state"
        )

      }
    }
  }
}
