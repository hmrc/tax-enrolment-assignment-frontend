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
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.messages.LandingPageMessages
import uk.gov.hmrc.taxenrolmentassignmentfrontend.views.html.LandingPage

class LandingPageSpec extends TestFixture {

  val landingPageView: LandingPage = app.injector.instanceOf[LandingPage]
  val result: HtmlFormat.Appendable = landingPageView()(FakeRequest(), testMessages)

  "The Landing Page" should {
    "contain the correct title" in {
      doc(result).title shouldBe LandingPageMessages.title
    }

    "contain the correct header" in {
      doc(result).getElementsByClass("govuk-heading-xl").text shouldBe LandingPageMessages.heading
    }

    "contain the correct paragraph" in {
      doc(result).getElementsByClass("govuk-body").text shouldBe LandingPageMessages.paragraph
    }

    "contain the correct button" in {
      doc(result).getElementsByClass("govuk-button").text shouldBe LandingPageMessages.button
    }
  }
}
