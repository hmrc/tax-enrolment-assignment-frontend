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
import uk.gov.hmrc.taxenrolmentassignmentfrontend.messages.ReportSuspiciousIDMessages
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.{
  AccountDetails,
  MFADetails
}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.views.html.ReportSuspiciousID

class ReportSuspiciousIDSpec extends ViewSpecHelper {

  val reportSuspiciousIdView: ReportSuspiciousID =
    app.injector.instanceOf[ReportSuspiciousID]

  object Selectors {
    val backLink = "govuk-back-link"
    val heading = "govuk-heading-xl"
    val body = "govuk-body"
    val summaryListRow = "govuk-summary-list__row"
    val summaryListKey = "govuk-summary-list__key"
    val summaryListValue = "govuk-summary-list__value"
    val links = "govuk-link"
  }

  val mfaDetails = Seq(MFADetails("mfaDetails.text", "28923"))

  val accountDetails =
    AccountDetails("4533", Some("email1@test.com"), "Yesterday", mfaDetails)

  val view: HtmlFormat.Appendable =
    reportSuspiciousIdView(accountDetails)(FakeRequest(), testMessages)

  val viewSA: HtmlFormat.Appendable =
    reportSuspiciousIdView(accountDetails, true)(FakeRequest(), testMessages)

  val document = doc(view)
  val documentSA = doc(viewSA)

  "The Report suspicious ID Page" should {
    "have a back link" that {
      val backLink =
        document.getElementsByClass(Selectors.backLink)
      "has the text back" in {
        backLink.text() shouldBe "Back"
      }
    }

    validateTimeoutDialog(document)
    "contains the correct title" in {
      document.title shouldBe ReportSuspiciousIDMessages.title
    }
    "contains the correct heading" in {
      document
        .getElementsByClass(Selectors.heading)
        .text() shouldBe ReportSuspiciousIDMessages.heading
    }
    "contains a suspicious userId summary details" that {
      val suspiciousIdDetailsRows =
        document.getElementsByClass(Selectors.summaryListRow)
      "includes the userId field with correct value" in {
        suspiciousIdDetailsRows
          .get(0)
          .getElementsByClass(Selectors.summaryListKey)
          .text() shouldBe "User ID"
        suspiciousIdDetailsRows
          .get(0)
          .getElementsByClass(Selectors.summaryListValue)
          .text() shouldBe s"Ending with ${accountDetails.userId}"
      }
      "includes the email field with correct value" in {
        suspiciousIdDetailsRows
          .get(1)
          .getElementsByClass(Selectors.summaryListKey)
          .text() shouldBe "Email"
        suspiciousIdDetailsRows
          .get(1)
          .getElementsByClass(Selectors.summaryListValue)
          .text() shouldBe accountDetails.email.get
      }
      "includes the last signed in date" in {
        suspiciousIdDetailsRows
          .get(2)
          .getElementsByClass(Selectors.summaryListKey)
          .text() shouldBe "Last signed in"
        suspiciousIdDetailsRows
          .get(2)
          .getElementsByClass(Selectors.summaryListValue)
          .text() shouldBe accountDetails.lastLoginDate
      }
      "includes the text message field with correct value" in {
        suspiciousIdDetailsRows
          .get(3)
          .getElementsByClass(Selectors.summaryListKey)
          .text() shouldBe "Text message"
        suspiciousIdDetailsRows
          .get(3)
          .getElementsByClass(Selectors.summaryListValue)
          .text() shouldBe s"Ending with ${accountDetails.mfaDetails.head.factorValue}"
      }
    }

    "contains a valid paragraph details" in {
      val paragraph = document.select("p." + Selectors.body)

      paragraph
        .get(0)
        .text() shouldBe ReportSuspiciousIDMessages.paragraph1
    }

    "contains the contact UK telephone details " in {
      val telephoneBlock = document.select("#telephone dt")

      telephoneBlock
        .get(0)
        .text() shouldBe ReportSuspiciousIDMessages.telephone(0)

      telephoneBlock
        .get(1)
        .text() shouldBe ReportSuspiciousIDMessages.telephone(1)
    }

    "contains the outside UK contact details " in {
      val outsideUKBlock = document.select("#outsideUk-telephone dt")

      outsideUKBlock
        .get(0)
        .text() shouldBe ReportSuspiciousIDMessages.outsideUK(0)

      outsideUKBlock
        .get(1)
        .text() shouldBe ReportSuspiciousIDMessages.outsideUK(1)
    }

    "contains the details block with valid details with in" that {
      val detailsBlockParagraphs = document
        .select("details p")

      "correct title" in {
        document
          .select(".govuk-details__summary")
          .text() shouldBe ReportSuspiciousIDMessages.informationBlock(0)
      }
      "correct information" in {
        detailsBlockParagraphs
          .get(0)
          .text() shouldBe ReportSuspiciousIDMessages.informationBlock(1)
        detailsBlockParagraphs
          .get(1)
          .text() shouldBe ReportSuspiciousIDMessages.informationBlock(2)
        detailsBlockParagraphs
          .get(2)
          .text() shouldBe ReportSuspiciousIDMessages.informationBlock(3)
      }
      "correct gov-uk link target and link text for Relay UK link" in {
        document
          .getElementsByClass(Selectors.links)
          .get(1)
          .attr("target") shouldBe "_blank"

        document
          .select("details a")
          .text() shouldBe ReportSuspiciousIDMessages.detailBlockLink

        document
          .select("details a")
          .attr("href") shouldBe ReportSuspiciousIDMessages.relayUkLinkUrl
      }
    }

    "not display the continue button when no SA identified" in {
      document
        .body()
        .text()
        .contains(ReportSuspiciousIDMessages.saPText) shouldBe false

      document
        .select("govuk-button")
        .size() shouldBe 0
    }

    "only display the continue button when SA identified" in {
      documentSA
        .select("p." + Selectors.body)
        .get(4)
        .text() shouldBe ReportSuspiciousIDMessages.saPText

      documentSA
        .getElementsByClass("govuk-button")
        .text shouldBe ReportSuspiciousIDMessages.button
    }

    "contains a form with the correct action" in {
      documentSA
        .select("form")
        .attr("action") shouldBe ReportSuspiciousIDMessages.action
    }
  }
}
