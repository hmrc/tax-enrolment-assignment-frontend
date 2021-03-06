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
import uk.gov.hmrc.taxenrolmentassignmentfrontend.messages.UnderConstructionMessages
import uk.gov.hmrc.taxenrolmentassignmentfrontend.views.html.UnderConstructionView

class UnderConstructionViewSpec extends ViewSpecHelper {

  lazy val view: UnderConstructionView = inject[UnderConstructionView]

  lazy val document: Document =
    Jsoup.parse(view()(fakeRequest, messages).toString)

  object Selectors {
    val title = "title"
    val heading = "h1"
    val p = "p"
  }

  "UnderConstructionView()" must {
    "Have the correct page title" in {
      document
        .select(Selectors.title)
        .text shouldBe (UnderConstructionMessages.title)
    }

    "Have the correct heading" in {
      document
        .select(Selectors.heading)
        .text shouldBe UnderConstructionMessages.heading
    }

    "have the correct p" in {
      document.select(Selectors.p).text shouldBe UnderConstructionMessages.p
    }

    validateTimeoutDialog(document)
    validateTechnicalHelpLinkPresent(document)
    validateAccessibilityStatementLinkPresent(document)
  }

}
