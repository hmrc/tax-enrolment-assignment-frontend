/*
 * Copyright 2023 HM Revenue & Customs
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

import org.jsoup.nodes.Document
import play.api.data.Form
import play.api.test.FakeRequest
import uk.gov.hmrc.taxenrolmentassignmentfrontend.forms.KeepAccessToSAThroughPTAForm
import uk.gov.hmrc.taxenrolmentassignmentfrontend.messages.KeepAccessToSAMessages
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.forms.KeepAccessToSAThroughPTA
import uk.gov.hmrc.taxenrolmentassignmentfrontend.views.html.KeepAccessToSA

class KeepAccessToSASpec extends ViewSpecHelper {

  lazy val view: KeepAccessToSA = inject[KeepAccessToSA]

  val form: Form[KeepAccessToSAThroughPTA] = KeepAccessToSAThroughPTAForm.keepAccessToSAThroughPTAForm

  def documentPopForm(isYes: Boolean = true): Document = {
    val popForm = KeepAccessToSAThroughPTAForm.keepAccessToSAThroughPTAForm
      .fill(KeepAccessToSAThroughPTA(isYes))
    val popView = view(popForm, testAccountDetails, testAccountDetailsWithSA)(FakeRequest(), testMessages)
    doc(popView)
  }

  object Selectors {
    val heading           = "govuk-fieldset__heading"
    val radios            = "govuk-radios__item"
    val radioInput        = "govuk-radios__input"
    val radioLabels       = "govuk-label govuk-radios__label"
    val body              = "govuk-body"
    val errorSummaryTitle = "govuk-error-summary__title"
    val errorSummaryList  = "govuk-list govuk-error-summary__list"
    val button            = "govuk-button"
    val form              = "form"
  }

  "KeepAccessToSA" when {
    "the form is not prepopulated and has no error" should {
      val html     =
        view(form, testAccountDetails, testAccountDetailsWithSA)(FakeRequest(), testMessages)
      val document = doc(html)
      "have the expected title" in {
        document.title() shouldBe KeepAccessToSAMessages.title
      }

      "have the expected heading" in {
        document
          .getElementsByClass(Selectors.heading)
          .text shouldBe KeepAccessToSAMessages.heading
      }

      validateTimeoutDialog(document)
      validateTechnicalHelpLinkPresent(document)
      validateAccessibilityStatementLinkPresent(document)

      "have radio buttons" that {
        val radioButtons = document.getElementsByClass(Selectors.radios)
        "have the option to select Yes and is unchecked" in {
          print(document.outerHtml())
          val radioButton1 = radioButtons
            .get(0)
          radioButton1
            .getElementsByClass(Selectors.radioLabels)
            .text()             shouldBe KeepAccessToSAMessages.radioYes
          radioButton1
            .getElementsByClass(Selectors.radioInput)
            .attr("value")      shouldBe "yes"
          radioButton1
            .getElementsByClass(Selectors.radioInput)
            .hasAttr("checked") shouldBe false
        }
        "have the option to select No" in {
          val radioButton2 = radioButtons
            .get(1)
          radioButton2
            .getElementsByClass(Selectors.radioLabels)
            .text()             shouldBe KeepAccessToSAMessages.radioNo
          radioButton2
            .getElementsByClass(Selectors.radioInput)
            .attr("value")      shouldBe "no"
          radioButton2
            .getElementsByClass(Selectors.radioInput)
            .hasAttr("checked") shouldBe false
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

    "the form is prepopulated and has no error" should {
      val documentYes = documentPopForm()
      val documentNo  = documentPopForm(false)
      "have the expected title" in {
        documentYes.title() shouldBe KeepAccessToSAMessages.title
        documentNo.title()  shouldBe KeepAccessToSAMessages.title
      }

      "have the expected heading" in {
        documentYes
          .getElementsByClass(Selectors.heading)
          .text shouldBe KeepAccessToSAMessages.heading
        documentNo
          .getElementsByClass(Selectors.heading)
          .text shouldBe KeepAccessToSAMessages.heading
      }

      "have radio buttons" that {
        val radioButtonsYes = documentYes.getElementsByClass(Selectors.radios)
        val radioButtonsNo  = documentNo.getElementsByClass(Selectors.radios)
        "have the option to select Yes and is checked" when {
          "the form is populated with yes" in {
            val radioButton1 = radioButtonsYes
              .get(0)
            radioButton1
              .getElementsByClass(Selectors.radioLabels)
              .text()             shouldBe KeepAccessToSAMessages.radioYes
            radioButton1
              .getElementsByClass(Selectors.radioInput)
              .attr("value")      shouldBe "yes"
            radioButton1
              .getElementsByClass(Selectors.radioInput)
              .hasAttr("checked") shouldBe true
          }
        }
        "have the option to select No and is unchecked" when {
          "the form is populated with yes" in {
            val radioButton2 = radioButtonsYes
              .get(1)
            radioButton2
              .getElementsByClass(Selectors.radioLabels)
              .text()             shouldBe KeepAccessToSAMessages.radioNo
            radioButton2
              .getElementsByClass(Selectors.radioInput)
              .attr("value")      shouldBe "no"
            radioButton2
              .getElementsByClass(Selectors.radioInput)
              .hasAttr("checked") shouldBe false
          }
        }
        "have the option to select Yes and is unchecked" when {
          "the form is populated with no" in {
            val radioButton1 = radioButtonsNo
              .get(0)
            radioButton1
              .getElementsByClass(Selectors.radioLabels)
              .text()             shouldBe KeepAccessToSAMessages.radioYes
            radioButton1
              .getElementsByClass(Selectors.radioInput)
              .attr("value")      shouldBe "yes"
            radioButton1
              .getElementsByClass(Selectors.radioInput)
              .hasAttr("checked") shouldBe false
          }
        }
        "have the option to select No and is checked" when {
          "the form is populated with yes" in {
            val radioButton2 = radioButtonsNo
              .get(1)
            radioButton2
              .getElementsByClass(Selectors.radioLabels)
              .text()             shouldBe KeepAccessToSAMessages.radioNo
            radioButton2
              .getElementsByClass(Selectors.radioInput)
              .attr("value")      shouldBe "no"
            radioButton2
              .getElementsByClass(Selectors.radioInput)
              .hasAttr("checked") shouldBe true
          }
        }
      }

      "contains a link for not having SA" that {
        val textElementYes = documentYes
          .getElementsByClass(Selectors.body)
          .get(0)
        val textElementNo  = documentYes
          .getElementsByClass(Selectors.body)
          .get(0)
        "has the correct text" in {
          textElementYes.text() shouldBe KeepAccessToSAMessages.noSALink
          textElementNo.text()  shouldBe KeepAccessToSAMessages.noSALink
        }

        "has the link to fraud reporting" in {
          textElementYes
            .select("a")
            .attr("href") shouldBe KeepAccessToSAMessages.fraudReportingUrl
          textElementNo
            .select("a")
            .attr("href") shouldBe KeepAccessToSAMessages.fraudReportingUrl
        }
      }
      "contain the correct button" in {
        documentYes
          .getElementsByClass(Selectors.button)
          .text shouldBe KeepAccessToSAMessages.button
        documentNo
          .getElementsByClass(Selectors.button)
          .text shouldBe KeepAccessToSAMessages.button
      }

      "contains a form with the correct action" in {
        documentYes
          .select(Selectors.form)
          .attr("action") shouldBe KeepAccessToSAMessages.action
        documentNo
          .select(Selectors.form)
          .attr("action") shouldBe KeepAccessToSAMessages.action
      }
    }

    "there are form errors" should {
      val formWithErrors =
        KeepAccessToSAThroughPTAForm.keepAccessToSAThroughPTAForm.bind(
          Map("select-continue" -> "")
        )
      val html           =
        view(formWithErrors, testAccountDetails, testAccountDetailsWithSA)(FakeRequest(), testMessages)
      val document       = doc(html)

      "have a page title containing error" in {
        document.title() shouldBe s"Error - ${KeepAccessToSAMessages.title}"
      }

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
            .attr("href")     shouldBe "#select-continue"
          errorSummary.text() shouldBe KeepAccessToSAMessages.errorMessage
        }
      }
    }
  }
}
