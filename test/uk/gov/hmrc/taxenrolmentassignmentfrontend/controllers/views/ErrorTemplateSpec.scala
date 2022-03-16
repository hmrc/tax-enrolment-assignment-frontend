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
import uk.gov.hmrc.taxenrolmentassignmentfrontend.views.html.templates.ErrorTemplate

class ErrorTemplateSpec extends TestFixture {

  val title = "test title"
  val heading = "test heading"
  val body = "test boddy"

  val errorView: ErrorTemplate = app.injector.instanceOf[ErrorTemplate]
  val result: HtmlFormat.Appendable = errorView(title, heading, body)(FakeRequest(), testMessages)

  "The Error Page" should {
    "contain the correct title" in {
      doc(result).title shouldBe title
    }

    "contain the correct header" in {
      doc(result).getElementsByClass("govuk-heading-xl").text shouldBe heading
    }

    "contain the correct paragraph" in {
      doc(result).getElementsByClass("govuk-body").text shouldBe body
    }
  }
}
