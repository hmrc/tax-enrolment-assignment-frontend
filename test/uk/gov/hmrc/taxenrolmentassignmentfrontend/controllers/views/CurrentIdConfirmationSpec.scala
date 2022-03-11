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

import play.api.i18n.Messages
import play.api.test.FakeRequest
import play.twirl.api.HtmlFormat
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.helpers.TestFixture
import uk.gov.hmrc.taxenrolmentassignmentfrontend.views.html.{CurrentIdConfirmation}

class CurrentIdConfirmationSpec extends TestFixture {

  val currentIdConfirmation: CurrentIdConfirmation = app.injector.instanceOf[CurrentIdConfirmation]
  private val testNino = "testNino"
  private val saCred = "SaCred"
  val result: HtmlFormat.Appendable = currentIdConfirmation(testNino,Some(saCred))(FakeRequest(), testMessages)

  "The Current ID Confirmation Page" should {
    "contain the correct title" in {
      doc(result).title shouldBe Messages("currentIdConfirmation.title")
    }

    "contain the correct header" in {
      doc(result).getElementsByClass("govuk-heading-xl").text shouldBe Messages("currentIdConfirmation.heading")
    }

    "contain the correct paragraph" in {
      doc(result).getElementsByClass("govuk-body").text shouldBe
        Messages(
          "You are currently signed in with the Government Gateway user ID " + testNino + ". From now on, you can only access your personal tax information with this user ID. To access Self Assessment you can still use the ID " + saCred + ", however this user ID will no longer access your personal tax account."
        )
    }

    "contain the correct button" in {
      doc(result).getElementsByClass("govuk-button").text shouldBe Messages("currentIdConfirmation.continue")
    }
  }
}
