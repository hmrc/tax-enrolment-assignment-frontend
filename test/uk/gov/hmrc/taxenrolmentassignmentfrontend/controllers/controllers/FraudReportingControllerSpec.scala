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

package uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.controllers

import org.jsoup.Jsoup
import play.api.i18n.Messages
import play.api.test.Helpers.{status, _}
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve.{Credentials, Retrieval, ~}
import uk.gov.hmrc.auth.core.{Enrolments, SessionRecordNotFound}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.helpers.TestData._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.helpers.TestFixture
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.{
  FraudReportingController,
  testOnly
}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.views.html.UnderConstructionView

import scala.concurrent.{ExecutionContext, Future}

class FraudReportingControllerSpec extends TestFixture {

  val underconstructionView: UnderConstructionView =
    inject[UnderConstructionView]

  val controller =
    new FraudReportingController(mockAuthAction, mcc, underconstructionView)

  "accountNotRecognised" when {
    "a the user is authenticated" should {
      "return OK and render the underconstructionView" in {
        implicit lazy val request =
          buildFakeRequestWithSessionId("GET", "Not Used")
        implicit lazy val messages: Messages = messagesApi.preferred(request)

        (mockAuthConnector
          .authorise(
            _: Predicate,
            _: Retrieval[(Option[String] ~ Option[Credentials]) ~ Enrolments]
          )(_: HeaderCarrier, _: ExecutionContext))
          .expects(predicates, retrievals, *, *)
          .returning(Future.successful(retrievalResponse()))

        val result = controller
          .accountNotRecognised(
            testOnly.routes.TestOnlyController.successfulCall.url
          )
          .apply(request)

        status(result) shouldBe OK
        val page = Jsoup.parse(contentAsString(result))
        page.title shouldBe "multipleAccounts.title"
        page
          .select("h1")
          .text() shouldBe "multipleAccounts.accountNotRecognised.heading"
        page
          .select("p")
          .text() shouldBe "multipleAccounts.accountNotRecognised.text"
      }
    }

    "the user is unauthenticated" should {
      "return unauthorized" in {
        implicit lazy val request =
          buildFakeRequestWithSessionId("GET", "Not Used")

        (mockAuthConnector
          .authorise(
            _: Predicate,
            _: Retrieval[(Option[String] ~ Option[Credentials]) ~ Enrolments]
          )(_: HeaderCarrier, _: ExecutionContext))
          .expects(predicates, retrievals, *, *)
          .returning(Future.failed(SessionRecordNotFound("FAILED")))

        val result = controller
          .accountNotRecognised(
            testOnly.routes.TestOnlyController.successfulCall.url
          )
          .apply(request)

        status(result) shouldBe UNAUTHORIZED
      }
    }
  }
}
