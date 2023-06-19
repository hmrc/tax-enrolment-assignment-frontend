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

package uk.gov.hmrc.taxenrolmentassignmentfrontend.views

import play.api.test.FakeRequest
import uk.gov.hmrc.taxenrolmentassignmentfrontend.messages.SABlueInterruptMessages
import uk.gov.hmrc.taxenrolmentassignmentfrontend.views.html.SABlueInterrupt

class SABlueInterruptSpec extends ViewSpecHelper {

  val sABlueInterruptView: SABlueInterrupt =
    app.injector.instanceOf[SABlueInterrupt]
  val view =
    sABlueInterruptView()(FakeRequest(), testMessages)

  val document = doc(view)

  "SABlueInterrupt" when {
    "user has an SA Enrolment associated to the credentials on another account" should {
      "contain the correct title" in {
        document.title shouldBe SABlueInterruptMessages.title
      }

      "contain the correct header" in {
        document
          .getElementsByClass("govuk-heading-xl")
          .text shouldBe SABlueInterruptMessages.heading
      }

      validateTimeoutDialog(document)
      validateTechnicalHelpLinkPresent(document)
      validateAccessibilityStatementLinkPresent(document)

      "contain the correct paragraph" in {
        document
          .getElementsByClass("govuk-body")
          .get(0)
          .text shouldBe SABlueInterruptMessages.paragraph1
        document
          .getElementsByClass("govuk-body")
          .get(1)
          .text shouldBe SABlueInterruptMessages.paragraph2
        document
          .getElementsByClass("govuk-body")
          .get(2)
          .text shouldBe SABlueInterruptMessages.paragraph3
      }

      "contain the correct list text" in {
        document
          .getElementsByClass("govuk-list govuk-list--bullet")
          .text should include(SABlueInterruptMessages.listItem1)
        document
          .getElementsByClass("govuk-list govuk-list--bullet")
          .text should include(SABlueInterruptMessages.listItem2)
      }

      "contain the correct button" in {
        document
          .getElementsByClass("actions")
          .text shouldBe SABlueInterruptMessages.selfAssessButton
      }
    }
  }

}
