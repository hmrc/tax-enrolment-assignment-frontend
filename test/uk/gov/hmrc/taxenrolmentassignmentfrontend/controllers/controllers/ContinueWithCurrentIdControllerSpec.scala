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

import play.api.http.Status.OK
import play.api.test.Helpers.{contentAsString, defaultAwaitTimeout, status}
import uk.gov.hmrc.auth.core.Enrolments
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve.{Credentials, Retrieval, ~}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.ContinueWithCurrentIdController
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.helpers.TestData.{buildFakeRequestWithSessionId, predicates, retrievalResponse, retrievals}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.helpers.TestFixture
import uk.gov.hmrc.taxenrolmentassignmentfrontend.views.html.ContinueWithCurrentId

import scala.concurrent.{ExecutionContext, Future}

class ContinueWithCurrentIdControllerSpec extends TestFixture {
  val view: ContinueWithCurrentId = app.injector.instanceOf[ContinueWithCurrentId]
  val controller =
    new ContinueWithCurrentIdController(mockAuthAction, mcc,view)

  "view" should {
    "present the current ID confirmation page" in {

      (mockAuthConnector
        .authorise(
          _: Predicate,
          _: Retrieval[((Option[String] ~ Option[Credentials]) ~ Enrolments) ~ Option[String]]
        )(_: HeaderCarrier, _: ExecutionContext))
        .expects(predicates, retrievals, *, *)
        .returning(Future.successful(retrievalResponse()))


      val result = controller.view().apply(buildFakeRequestWithSessionId("GET", "Not Used"))

      status(result) shouldBe OK
      contentAsString(result) should include("continueWithCurrentId.title")
    }
  }
}