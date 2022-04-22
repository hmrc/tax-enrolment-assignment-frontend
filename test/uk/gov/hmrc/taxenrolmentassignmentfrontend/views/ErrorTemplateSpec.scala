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

import play.api.test.FakeRequest
import play.twirl.api.HtmlFormat
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.TestFixture
import uk.gov.hmrc.taxenrolmentassignmentfrontend.messages.ErrorTemplateMessages

class ErrorTemplateSpec extends TestFixture {

  val result: HtmlFormat.Appendable = errorView()(FakeRequest(), testMessages)

  "The Error Page" should {
    "contain the correct title" in {
      doc(result).title shouldBe ErrorTemplateMessages.title
    }

    "contain the correct header" in {
      doc(result).getElementsByClass("govuk-heading-xl").text shouldBe ErrorTemplateMessages.heading
    }

    "contain the correct paragraph" in {
      doc(result).getElementsByClass("govuk-body").get(0).text shouldBe ErrorTemplateMessages.paragraph1
      doc(result).getElementsByClass("govuk-body").get(1).text shouldBe ErrorTemplateMessages.paragraph2Text
    }

    "contain the right link" in {
      doc(result).getElementById("techSupportLink").attr("href") shouldBe ErrorTemplateMessages.paragraph2Link
      doc(result).getElementById("techSupportLink").text() shouldBe ErrorTemplateMessages.paragraph2LinkText
    }
  }
}
