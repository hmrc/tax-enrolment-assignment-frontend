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
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.messages.ContactSAMessages
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.{AccountDetails, MFADetails}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.views.html.ContactSA

class ContactSASpec extends TestFixture {

  val reportSuspiciousIdView: ContactSA =
    app.injector.instanceOf[ContactSA]

  object Selectors {
    val backLink = "govuk-back-link"
    val heading = "govuk-heading-xl"
    val body = "govuk-body"
    val summaryListRow = "govuk-summary-list__row"
    val summaryListKey = "govuk-summary-list__key"
    val summaryListValue = "govuk-summary-list__value"
    val links = "govuk-link"
  }

  val mfaDetails = Seq(
    MFADetails("Text message", "07390328923")
  )

  val accountDetails = AccountDetails(
    "Ending with 4533",
    Some("email1@test.com"),
    "Yesterday",
    mfaDetails
  )

  val result: HtmlFormat.Appendable =
    reportSuspiciousIdView(accountDetails)(FakeRequest(), testMessages)

  val resultSA: HtmlFormat.Appendable =
    reportSuspiciousIdView(accountDetails,true)(FakeRequest(), testMessages)

  "The Report suspicious ID Page" should {
    "have a back link" that {
      val backLink =
        doc(result).getElementsByClass(Selectors.backLink)
      "has the text back" in {
        backLink.text() shouldBe "Back"
      }
    }
    "contains the correct title" in {
      doc(result).title shouldBe ContactSAMessages.title
    }
    "contains the correct heading" in {
      doc(result).getElementsByClass(Selectors.heading)
        .text() shouldBe ContactSAMessages.heading
    }
    "contains a suspicious userId summary details" that {
      val suspiciousIdDetailsRows = doc(result).getElementsByClass(Selectors.summaryListRow)
      "includes the userId field with correct value" in {
        suspiciousIdDetailsRows
          .get(0)
          .getElementsByClass(Selectors.summaryListKey)
          .text() shouldBe "User ID"
        suspiciousIdDetailsRows
          .get(0)
          .getElementsByClass(Selectors.summaryListValue)
          .text() shouldBe accountDetails.userId
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
      "includes the text message field with correct value" in {
        suspiciousIdDetailsRows
          .get(2)
          .getElementsByClass(Selectors.summaryListKey)
          .text() shouldBe "Text message"
        suspiciousIdDetailsRows
          .get(2)
          .getElementsByClass(Selectors.summaryListValue)
          .text() shouldBe accountDetails.mfaDetails.head.factorValue
      }
    }

    "contains a valid paragraph details" in {
      val paragraph = doc(result).select("p." + Selectors.body)

      paragraph.get(0)
        .text() shouldBe ContactSAMessages.paragraph1
    }

    "contains the contact UK telephone details " in {
      val telephoneBlock = doc(result).select("#telephone dt")

      telephoneBlock.get(0)
        .text() shouldBe ContactSAMessages.telephone(0)

      telephoneBlock.get(1)
        .text() shouldBe ContactSAMessages.telephone(1)
    }

    "contains the outside UK contact details " in {
      val outsideUKBlock = doc(result).select("#outsideUk-telephone dt")

      outsideUKBlock.get(0)
        .text() shouldBe ContactSAMessages.outsideUK(0)

      outsideUKBlock.get(1)
        .text() shouldBe ContactSAMessages.outsideUK(1)
    }

    "contains the details block with valid details with in" that {
      val detailsBlockParagraphs = doc(result)
        .select("details p")

      "correct title" in {
        doc(result)
          .select(".govuk-details__summary")
          .text() shouldBe ContactSAMessages.informationBlock(0)
      }
      "correct information" in {
        detailsBlockParagraphs
          .get(0)
          .text() shouldBe ContactSAMessages.informationBlock(1)
        detailsBlockParagraphs
          .get(1)
          .text() shouldBe ContactSAMessages.informationBlock(2)
        detailsBlockParagraphs
          .get(2)
          .text() shouldBe ContactSAMessages.informationBlock(3)
      }
      "correct gov-uk link target and link text for Relay UK link" in {
        doc(result)
          .getElementsByClass(Selectors.links).get(1)
          .attr("target") shouldBe "_blank"

        doc(result)
          .select("details a")
          .text() shouldBe ContactSAMessages.detailBlockLink

        doc(result)
          .select("details a")
          .attr("href") shouldBe ContactSAMessages.relayUkLinkUrl
      }
    }

    "not display the continue button when no SA identified" in {
      doc(result)
        .body().text().contains(ContactSAMessages.saPText) shouldBe false

      doc(result).select(".govuk-button")
        .size() shouldBe 0
    }

    "only display the continue button when SA identified" in {
      doc(resultSA)
        .select("p." + Selectors.body)
        .get(4)
        .text() shouldBe ContactSAMessages.saPText

      doc(resultSA)
        .getElementsByClass("govuk-button").text shouldBe ContactSAMessages.button
    }




  }

}