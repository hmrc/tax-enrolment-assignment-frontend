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

package uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.views

import play.api.test.FakeRequest
import play.twirl.api.HtmlFormat
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.helpers.TestFixture
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.messages.SABlueInterruptMessages
import uk.gov.hmrc.taxenrolmentassignmentfrontend.views.html.SABlueInterrupt

class SABlueInterruptSpec extends TestFixture {

  val sABlueInterruptView: SABlueInterrupt = app.injector.instanceOf[SABlueInterrupt]
  val result: HtmlFormat.Appendable = sABlueInterruptView()(FakeRequest(), testMessages)

  "SABlueInterrupt" when {
    "user has an SA Enrolment associated to the credentials on another account" should {
      "contain the correct title" in {
        doc(result).title shouldBe SABlueInterruptMessages.selfAssessTitle
      }

      "contain the correct header" in {
        doc(result).getElementsByClass("interrupt-card h1").text shouldBe SABlueInterruptMessages.selfAssessHeading
      }

      "contain the correct paragraph" in {
        doc(result).getElementsByClass("interrupt-card-p").text shouldBe SABlueInterruptMessages.selfAssessParagraph
      }

      "contain the correct button" in {
        doc(result).getElementsByClass("govuk-button").text shouldBe SABlueInterruptMessages.selfAssessButton
      }
    }
  }


}
