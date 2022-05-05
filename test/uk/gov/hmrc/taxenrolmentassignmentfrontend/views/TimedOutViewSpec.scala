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

package uk.gov.hmrc.taxenrolmentassignmentfrontend.views

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import uk.gov.hmrc.taxenrolmentassignmentfrontend.messages.TimedOutMessages
import uk.gov.hmrc.taxenrolmentassignmentfrontend.views.html.TimedOutView

class TimedOutViewSpec extends ViewSpecHelper {

  lazy val view: TimedOutView = inject[TimedOutView]

  lazy val document: Document =
    Jsoup.parse(view()(fakeRequest, messages).toString)

  object Selectors {
    val heading = "govuk-heading-xl"
    val button = "govuk-button"
  }

  "TimedOutView" must {
    "Have the correct page title" in {
      document
        .title() shouldBe (TimedOutMessages.title)
    }

    "Have the correct heading" in {
      document
        .getElementsByClass(Selectors.heading)
        .text shouldBe TimedOutMessages.heading
    }

    "have the correct button" in {
      document
        .getElementsByClass(Selectors.button)
        .text shouldBe TimedOutMessages.button
    }

    validateNoTimeoutDialog(document)
    validateTechnicalHelpLinkPresent(document)

  }

}
