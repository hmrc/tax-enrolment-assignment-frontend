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
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.messages.EnrolledAfterReportingFraudMessages
import uk.gov.hmrc.taxenrolmentassignmentfrontend.views.html.EnrolledForPTAfterReportingFraud

class EnrolledForPTAfterReportingFraudSpec extends TestFixture {

  val view: EnrolledForPTAfterReportingFraud =
    app.injector.instanceOf[EnrolledForPTAfterReportingFraud]
  val userId = "********3214"
  val html: HtmlFormat.Appendable =
    view(userId)(FakeRequest(), testMessages)
  val document = doc(html)

  object Selectors {
    val heading = "govuk-heading-xl"
    val body = "govuk-body"
    val subHeading = "govuk-heading-m"
    val button = "govuk-button"
    val form = "form"
  }

  "EnroledForPTAfterReportingFraud" should {
    "contain the correct title" in {
      document.title shouldBe EnrolledAfterReportingFraudMessages.title
    }
    "contain the correct header" in {
      document
        .getElementsByClass("govuk-heading-xl")
        .text shouldBe EnrolledAfterReportingFraudMessages.heading
    }
    "contain the correct body" which {
      val subHeadings = document.getElementsByClass(Selectors.subHeading)
      "has a sub heading for reporting suspicious ID" in {
        subHeadings
          .get(0)
          .text() shouldBe EnrolledAfterReportingFraudMessages.heading2
      }
      "has a sub heading for other IDs" in {
        subHeadings
          .get(1)
          .text() shouldBe EnrolledAfterReportingFraudMessages.heading3
      }
      "have expected paragraphs" in {
        document
          .getElementsByClass("govuk-body")
          .text shouldBe EnrolledAfterReportingFraudMessages.paragraphs
      }
    }
    "contain the correct button" in {
      document
        .getElementsByClass("govuk-button")
        .text shouldBe EnrolledAfterReportingFraudMessages.button
    }

    "contains a form with the correct action" in {
      document
        .select(Selectors.form)
        .attr("action") shouldBe EnrolledAfterReportingFraudMessages.action
    }
  }
}
