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
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.SignOutController
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.helpers.TestFixture
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.messages.SignInAgainMessages
import uk.gov.hmrc.taxenrolmentassignmentfrontend.views.html.SignInAgain

class SignInAgainSpec extends TestFixture {

  val testTeaSessionCache = new TestTeaSessionCache
  val signOutController = new SignOutController(mockAuthAction,mcc,testAppConfig,testTeaSessionCache)
  val SignInAgainPage: SignInAgain = app.injector.instanceOf[SignInAgain]
  val result: HtmlFormat.Appendable = SignInAgainPage(signOutController)(FakeRequest(), testMessages)

  "The SignInAgain Page" should {
    "contain the correct title" in {
      doc(result).title shouldBe SignInAgainMessages.title
    }

    "contain the correct header" in {
      doc(result).getElementsByClass("govuk-heading-xl").text shouldBe SignInAgainMessages.heading
    }

    "contain the correct paragraph" in {
      doc(result).getElementsByClass("govuk-body").text shouldBe SignInAgainMessages.paragraph
    }

    "contain the correct back link" in {
      doc(result).getElementsByClass("govuk-back-link").text shouldBe SignInAgainMessages.backLink
    }
    "contain the correct sign in again link" in {
      doc(result).getElementsByClass("govuk-link").attr("href") shouldBe SignInAgainMessages.link
    }
  }
}
