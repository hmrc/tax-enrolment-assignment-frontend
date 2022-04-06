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
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.messages.ContinueWithCurrentIdMessages
import uk.gov.hmrc.taxenrolmentassignmentfrontend.views.html.ContinueWithCurrentId

class ContinueWithCurrentIdSpec extends TestFixture {

  val continueWithCurrentId: ContinueWithCurrentId = app.injector.instanceOf[ContinueWithCurrentId]
  private val currentId = "testCurrentId"
  private val saCred = "TestSAId"
  val result: HtmlFormat.Appendable = continueWithCurrentId(currentId,Some(saCred))(FakeRequest(), testMessages)

  "The Current ID Confirmation Page" should {
    "contain the correct title" in {
      doc(result).title shouldBe ContinueWithCurrentIdMessages.title
    }

    "contain the correct main header" in {
      doc(result).getElementsByClass("govuk-heading-xl").text shouldBe ContinueWithCurrentIdMessages.heading
    }

    "contain the correct first paragraph, regarding in-session GG Id" in {
      doc(result).getElementsByClass("govuk-body").get(0).text shouldBe ContinueWithCurrentIdMessages.paragraph1
    }

   "contain the correct second paragraph, regarding locking PT to this account" in {
      doc(result).getElementsByClass("govuk-body").get(1).text shouldBe ContinueWithCurrentIdMessages.paragraph2
    }

    "contain the correct second header" in {
      doc(result).getElementsByClass("govuk-heading-m").text shouldBe ContinueWithCurrentIdMessages.heading2
    }

    "contain the correct third paragraph, regarding SA account associated with the account" in {
      doc(result).getElementsByClass("govuk-body").get(2).text shouldBe ContinueWithCurrentIdMessages.paragraph3
    }

   "contain the correct fourth paragraph, regarding losing access to other credentials" in {
      doc(result).getElementsByClass("govuk-body").get(3).text shouldBe ContinueWithCurrentIdMessages.paragraph4
    }

    "contain the correct button" in {
      doc(result).getElementsByClass("govuk-button").text shouldBe ContinueWithCurrentIdMessages.button
    }
  }
}
