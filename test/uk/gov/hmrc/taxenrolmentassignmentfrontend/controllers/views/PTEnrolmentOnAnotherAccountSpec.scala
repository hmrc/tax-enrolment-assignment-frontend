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
import uk.gov.hmrc.govukfrontend.views.viewmodels.summarylist.SummaryListRow
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.helpers.TestFixture
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.messages.EnrolCurrentUserMessages
import uk.gov.hmrc.taxenrolmentassignmentfrontend.forms.EnrolCurrentUserIdForm
import uk.gov.hmrc.taxenrolmentassignmentfrontend.views.html.{
  EnrolCurrentUser,
  PTEnrolmentOnAnotherAccount
}

class PTEnrolmentOnAnotherAccountSpec extends TestFixture {

  lazy val view: PTEnrolmentOnAnotherAccount =
    inject[PTEnrolmentOnAnotherAccount]

  object Selectors {
    val heading = "govuk-heading-xl"
    val body = "govuk-body"
  }

  val ptEnrolmentSummaryItemRows = Seq(SummaryListRow())

  "PTEnrolmentOnAnotherAccount" when {
    "the user has another account with self assessment" should {
      val htmlWithSA =
        view(form, fixedCurrentUserId, Some(fixedSAUserId))(
          FakeRequest(),
          testMessages
        )
      val documentWithSA = doc(htmlWithSA)
      "have the expected title" in {
        documentWithSA.title() shouldBe EnrolCurrentUserMessages.title
      }
      "have a back link" that {
        val backLink =
          documentWithSA.getElementsByClass(Selectors.backLink)
        "has the text back" in {
          backLink.text() shouldBe "Back"
        }
        "goes to the Landing page" in {
          backLink.attr("href") shouldBe "?"
        }
      }
      "have the expected heading" in {
        documentWithSA
          .getElementsByClass(Selectors.heading)
          .text shouldBe EnrolCurrentUserMessages.title
      }
      "have radio buttons" that {
        val radioButtons = documentWithSA.getElementsByClass(Selectors.radios)
        "have the option to choose to continue with current userId" in {
          val radioButton1 = radioButtons
            .get(0)
          radioButton1
            .getElementsByClass(Selectors.radioLables)
            .text() shouldBe EnrolCurrentUserMessages.radioCurrentUserId(
            fixedCurrentUserId
          )
          radioButton1
            .getElementsByClass(Selectors.radioInput)
            .attr("value") shouldBe "yes"
        }
        "allow you to choose to continue with different userId" in {
          val radioButton2 = radioButtons
            .get(1)
          radioButton2
            .getElementsByClass(Selectors.radioLables)
            .text() shouldBe EnrolCurrentUserMessages.radioOtherUserId
          radioButton2
            .getElementsByClass(Selectors.radioInput)
            .attr("value") shouldBe "no"
        }
      }
      "contain a warning message" in {
        val warningElements =
          documentWithSA.getElementsByClass(Selectors.warning)
        warningElements.size() shouldBe 1
        warningElements.text() shouldBe EnrolCurrentUserMessages.warning(
          fixedSAUserId
        )
      }
      "contain the correct button" in {
        documentWithSA
          .getElementsByClass(Selectors.button)
          .text shouldBe EnrolCurrentUserMessages.button
      }
    }

    "the user does not have another account with self assessment and no form errors" should {
      val htmlWithNoSA =
        view(form, fixedCurrentUserId, None)(FakeRequest(), testMessages)
      val documentWithNoSA = doc(htmlWithNoSA)
      "have the expected title" in {
        documentWithNoSA.title() shouldBe EnrolCurrentUserMessages.title
      }
      "have a back link" that {
        val backLink =
          documentWithNoSA.getElementsByClass(Selectors.backLink)
        "has the text back" in {
          backLink.text() shouldBe "Back"
        }
        "goes to the Landing page" in {
          backLink.attr("href") shouldBe "?"
        }
      }
      "have the expected heading" in {
        documentWithNoSA
          .getElementsByClass(Selectors.heading)
          .text shouldBe EnrolCurrentUserMessages.title
      }
      "have radio buttons" that {
        val radioButtons = documentWithNoSA.getElementsByClass(Selectors.radios)
        "have the option to choose to continue with current userId" in {
          val radioButton1 = radioButtons
            .get(0)
          radioButton1
            .getElementsByClass(Selectors.radioLables)
            .text() shouldBe EnrolCurrentUserMessages.radioCurrentUserId(
            fixedCurrentUserId
          )
          radioButton1
            .getElementsByClass(Selectors.radioInput)
            .attr("value") shouldBe "yes"
        }
        "allow you to choose to continue with different userId" in {
          val radioButton2 = radioButtons
            .get(1)
          radioButton2
            .getElementsByClass(Selectors.radioLables)
            .text() shouldBe EnrolCurrentUserMessages.radioOtherUserId
          radioButton2
            .getElementsByClass(Selectors.radioInput)
            .attr("value") shouldBe "no"
        }
      }
      "not contain a warning message" in {
        val warningElements =
          documentWithNoSA.getElementsByClass(Selectors.warning)
        warningElements.size() shouldBe 0
      }

      "contain the correct button" in {
        documentWithNoSA
          .getElementsByClass(Selectors.button)
          .text shouldBe EnrolCurrentUserMessages.button
      }
    }

    "there are form errors" should {
      val formWithErrors = EnrolCurrentUserIdForm.enrolCurrentUserIdForm.bind(
        Map("enrolCurrentUserId" -> "")
      )
      val htmlWithNoSA =
        view(formWithErrors, fixedCurrentUserId, None)(
          FakeRequest(),
          testMessages
        )
      val documentWithNoSA = doc(htmlWithNoSA)

      "have an error summary" that {
        "has title" in {
          documentWithNoSA
            .getElementsByClass(Selectors.errorSummaryTitle)
            .text() shouldBe EnrolCurrentUserMessages.errorTitle
        }
        "contains a message that links to field with error" in {
          val errorSummary = documentWithNoSA
            .getElementsByClass(Selectors.errorSummaryList)
            .first()
          errorSummary
            .select("a")
            .attr("href") shouldBe "#use-current-id-error"
          errorSummary.text() shouldBe EnrolCurrentUserMessages.errorMessage
        }
      }
    }
  }
}
