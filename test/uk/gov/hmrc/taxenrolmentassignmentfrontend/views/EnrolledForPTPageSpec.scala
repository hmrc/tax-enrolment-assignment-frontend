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
import play.twirl.api.HtmlFormat
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.routes
import uk.gov.hmrc.taxenrolmentassignmentfrontend.messages.EnrolledForPTPageMessages
import uk.gov.hmrc.taxenrolmentassignmentfrontend.views.html.EnrolledForPTPage

class EnrolledForPTPageSpec extends ViewSpecHelper {

  val enrolledForPTPage: EnrolledForPTPage =
    app.injector.instanceOf[EnrolledForPTPage]
  val userId = "3214"
  val htmlWithSA: HtmlFormat.Appendable =
    enrolledForPTPage(userId, true, routes.EnrolledForPTWithSAController.continue)(FakeRequest(), testMessages)
  val htmlWithNoSA: HtmlFormat.Appendable =
    enrolledForPTPage(userId, false, routes.EnrolledForPTController.continue)(FakeRequest(), testMessages)
  val documentWithSA = doc(htmlWithSA)
  val documentWithNoSA = doc(htmlWithNoSA)

  object Selectors {
    val heading = "govuk-heading-xl"
    val body = "govuk-body"
    val saHeading = "govuk-heading-m"
    val button = "govuk-button"
    val form = "form"
  }

  "EnrolledForPTPage" when {
    "the user has SA" should {
      "contain the correct title" in {
        documentWithSA.title shouldBe EnrolledForPTPageMessages.title
      }
      "contain the correct first header" in {
        documentWithSA
          .getElementsByClass(Selectors.heading)
          .text shouldBe EnrolledForPTPageMessages.heading
      }

      validateTimeoutDialog(documentWithSA)
      validateTechnicalHelpLinkPresent(documentWithSA)
      validateAccessibilityStatementLinkPresent(documentWithSA)

      "contain the correct body" in {
        documentWithSA
          .getElementsByClass(Selectors.body)
          .text shouldBe EnrolledForPTPageMessages.paragraphSA
      }

      "contain the correct second header" in {
        documentWithSA
          .getElementsByClass(Selectors.saHeading)
          .text shouldBe EnrolledForPTPageMessages.heading2
      }

      "contain the correct button" in {
        documentWithSA
          .getElementsByClass(Selectors.button)
          .text shouldBe EnrolledForPTPageMessages.button
      }

      "contains a form with the correct action" in {
        documentWithSA
          .select(Selectors.form)
          .attr("action") shouldBe EnrolledForPTPageMessages.saAction
      }
    }

    "the user has no SA" should {
      "contain the correct title" in {
        documentWithNoSA.title shouldBe EnrolledForPTPageMessages.title
      }
      "contain the correct first header" in {
        documentWithNoSA
          .getElementsByClass("govuk-heading-xl")
          .text shouldBe EnrolledForPTPageMessages.heading
      }

      validateTimeoutDialog(documentWithNoSA)
      validateTechnicalHelpLinkPresent(documentWithNoSA)

      "contain the correct body" in {
        documentWithNoSA
          .getElementsByClass("govuk-body")
          .text shouldBe EnrolledForPTPageMessages.paragraphNoSA
      }
      "contain the correct button" in {
        documentWithNoSA
          .getElementsByClass("govuk-button")
          .text shouldBe EnrolledForPTPageMessages.button
      }

      "contains a form with the correct action" in {
        documentWithSA
          .select(Selectors.form)
          .attr("action") shouldBe EnrolledForPTPageMessages.saAction
      }
    }
  }
}
