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

import org.mockito.ArgumentMatchers.{any, eq => ameq}
import org.mockito.MockitoSugar.{mock, when}
import play.api.Application
import play.api.inject.{Binding, bind}
import play.api.libs.json.JsString
import play.api.mvc.{AnyContentAsEmpty, BodyParsers}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.actions.RequestWithUserDetailsFromSession
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.TestData.{predicates, retrievalResponse, retrievals}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.{ControllersBaseSpec, UrlPaths}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.orchestrators.{AccountCheckOrchestrator, MultipleAccountsOrchestrator}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.reporting.AuditHandler
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.SessionKeys.REDIRECT_URL
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.TEASessionCache
import uk.gov.hmrc.taxenrolmentassignmentfrontend.services.SilentAssignmentService

import java.net.URLEncoder
import scala.concurrent.{ExecutionContext, Future}

class SignOutControllerSpec extends ControllersBaseSpec {

  lazy val mockSilentAssignmentService: SilentAssignmentService = mock[SilentAssignmentService]
  lazy val mockAccountCheckOrchestrator: AccountCheckOrchestrator = mock[AccountCheckOrchestrator]
  lazy val mockAuditHandler: AuditHandler = mock[AuditHandler]

  lazy val testBodyParser: BodyParsers.Default = mock[BodyParsers.Default]
  lazy val mockMultipleAccountsOrchestrator: MultipleAccountsOrchestrator = mock[MultipleAccountsOrchestrator]

  override lazy val overrides: Seq[Binding[TEASessionCache]] = Seq(
    bind[TEASessionCache].toInstance(mockTeaSessionCache)
  )

  override implicit lazy val app: Application = localGuiceApplicationBuilder()
    .overrides(
      bind[SilentAssignmentService].toInstance(mockSilentAssignmentService),
      bind[AccountCheckOrchestrator].toInstance(mockAccountCheckOrchestrator),
      bind[AuditHandler].toInstance(mockAuditHandler),
      bind[AuthConnector].toInstance(mockAuthConnector),
      bind[BodyParsers.Default].toInstance(testBodyParser),
      bind[MultipleAccountsOrchestrator].toInstance(mockMultipleAccountsOrchestrator)
    )
    .build()

  lazy val controller: SignOutController = app.injector.instanceOf[SignOutController]

  def fakeReq(method: String, url: String = "N/A"): FakeRequest[AnyContentAsEmpty.type] =
    FakeRequest(method, url)
      .withSession(
        "sessionId"    -> "FAKE_SESSION_ID",
        "X-Request-ID" -> "FakeOtherID"
      )

  "signOut" when {
    "the session contains a redirectUrl" should {
      "clear down the user's data and redirect to signOut with continueUrl" in {

        when(mockAuthConnector.authorise(ameq(predicates), ameq(retrievals))(any[HeaderCarrier], any[ExecutionContext]))
          .thenReturn(Future.successful(retrievalResponse()))

        when(mockTeaSessionCache.fetch()(any[RequestWithUserDetailsFromSession[_]]))
          .thenReturn(Future.successful(Some(CacheMap("id", Map(REDIRECT_URL -> JsString(UrlPaths.returnUrl))))))

        when(mockTeaSessionCache.removeRecord(any())).thenReturn(Future.successful(true))

        val result = controller.signOut().apply(fakeReq("GET"))

        status(result) shouldBe SEE_OTHER
        headers(result).contains("X-Request-ID") shouldBe false
        redirectLocation(result) shouldBe Some(
          s"http://localhost:9553/bas-gateway/sign-out-without-state?continue=${URLEncoder.encode(UrlPaths.returnUrl, "UTF-8")}"
        )
      }
    }

    "the session exists but does not contain the redirectUrl" should {
      "clear down the user's data and redirect to signOut without continueUrl" in {
        when(mockAuthConnector.authorise(ameq(predicates), ameq(retrievals))(any[HeaderCarrier], any[ExecutionContext]))
          .thenReturn(Future.successful(retrievalResponse()))

        when(mockTeaSessionCache.fetch()(any[RequestWithUserDetailsFromSession[_]]))
          .thenReturn(Future.successful(Some(CacheMap("id", Map()))))

        when(mockTeaSessionCache.removeRecord(any())).thenReturn(Future.successful(true))

        val result = controller.signOut().apply(fakeReq("GET"))

        status(result) shouldBe SEE_OTHER
        headers(result).contains("X-Request-ID") shouldBe false
        redirectLocation(result) shouldBe Some(
          s"http://localhost:9553/bas-gateway/sign-out-without-state"
        )
      }
    }

    "the session does not exists" should {
      "redirect to signOut without continueUrl" in {
        when(mockAuthConnector.authorise(ameq(predicates), ameq(retrievals))(any[HeaderCarrier], any[ExecutionContext]))
          .thenReturn(Future.successful(retrievalResponse()))

        when(mockTeaSessionCache.fetch()(any[RequestWithUserDetailsFromSession[_]])).thenReturn(Future.successful(None))

        when(mockTeaSessionCache.removeRecord(any())).thenReturn(Future.successful(true))

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
