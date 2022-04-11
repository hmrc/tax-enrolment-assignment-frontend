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

import play.api.data.Form
import play.api.test.FakeRequest
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.helpers.TestFixture
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.messages.{
  KeepAccessToSAMessages,
  LandingPageMessages
}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.forms.{
  EnrolCurrentUserIdForm,
  KeepAccessToSAThroughPTAForm
}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.views.html.{
  EnrolCurrentUser,
  KeepAccessToSA
}

class KeepAccessToSASpec extends TestFixture {

  lazy val view: KeepAccessToSA = inject[KeepAccessToSA]

  val form = KeepAccessToSAThroughPTAForm.keepAccessToSAThroughPTAForm

  object Selectors {
    val heading = "govuk-fieldset__heading"
    val radios = "govuk-radios__item"
    val radioInput = "govuk-radios__input"
    val radioLables = "govuk-label govuk-radios__label"
    val body = "govuk-body"
    val errorSummaryTitle = "govuk-error-summary__title"
    val errorSummaryList = "govuk-list govuk-error-summary__list"
    val button = "govuk-button"
    val form = "form"
  }

  "KeepAccessToSA" when {
    "the form has no errors" should {
      val html =
        view(form)(FakeRequest(), testMessages)
      val document = doc(html)
      "have the expected title" in {
        document.title() shouldBe KeepAccessToSAMessages.title
      }

      "have the expected heading" in {
        document
          .getElementsByClass(Selectors.heading)
          .text shouldBe KeepAccessToSAMessages.title
      }
      "have radio buttons" that {
        val radioButtons = document.getElementsByClass(Selectors.radios)
        "have the option to select Yes" in {
          val radioButton1 = radioButtons
            .get(0)
          radioButton1
            .getElementsByClass(Selectors.radioLables)
            .text() shouldBe KeepAccessToSAMessages.radioYes
          radioButton1
            .getElementsByClass(Selectors.radioInput)
            .attr("value") shouldBe "yes"
        }
        "have the option to select No" in {
          val radioButton2 = radioButtons
            .get(1)
          radioButton2
            .getElementsByClass(Selectors.radioLables)
            .text() shouldBe KeepAccessToSAMessages.radioNo
          radioButton2
            .getElementsByClass(Selectors.radioInput)
            .attr("value") shouldBe "no"
        }
      }

      "contains a link for not having SA" that {
        val textElement = document
          .getElementsByClass(Selectors.body)
          .get(0)
        "has the correct text" in {
          textElement.text() shouldBe KeepAccessToSAMessages.noSALink
        }

        "has the link to fraud reporting" in {
          textElement
            .select("a")
            .attr("href") shouldBe KeepAccessToSAMessages.fraudReportingUrl
        }
      }
      "contain the correct button" in {
        document
          .getElementsByClass(Selectors.button)
          .text shouldBe KeepAccessToSAMessages.button
      }

      "contains a form with the correct action" in {
        document
          .select(Selectors.form)
          .attr("action") shouldBe KeepAccessToSAMessages.action
      }
    }

    "there are form errors" should {
      val formWithErrors =
        KeepAccessToSAThroughPTAForm.keepAccessToSAThroughPTAForm.bind(
          Map("keepAccessToSAThroughPTA" -> "")
        )
      val html =
        view(formWithErrors)(FakeRequest(), testMessages)
      val document = doc(html)

      "have an error summary" that {
        "has title" in {
          document
            .getElementsByClass(Selectors.errorSummaryTitle)
            .text() shouldBe KeepAccessToSAMessages.errorTitle
        }
        "contains a message that links to field with error" in {
          val errorSummary = document
            .getElementsByClass(Selectors.errorSummaryList)
            .first()
          errorSummary
            .select("a")
            .attr("href") shouldBe "#select-continue"
          errorSummary.text() shouldBe KeepAccessToSAMessages.errorMessage
        }
      }
    }
  }
}
