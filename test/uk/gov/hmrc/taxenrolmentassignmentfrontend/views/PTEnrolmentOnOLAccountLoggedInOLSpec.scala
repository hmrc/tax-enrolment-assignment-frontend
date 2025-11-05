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

import play.api.test.FakeRequest
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.TestData._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.messages.PTEnrolmentOtherAccountMessagesBothOL
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.MFADetails
import uk.gov.hmrc.taxenrolmentassignmentfrontend.views.html.PTEnrolmentOnOLAccountLoggedInOL

class PTEnrolmentOnOLAccountLoggedInOLSpec extends ViewSpecHelper {

  lazy val view: PTEnrolmentOnOLAccountLoggedInOL =
    inject[PTEnrolmentOnOLAccountLoggedInOL]

  object Selectors {
    val heading          = "govuk-heading-l"
    val body             = "govuk-body"
    val summaryListRow   = "govuk-summary-list__row"
    val summaryListKey   = "govuk-summary-list__key"
    val summaryListValue = "govuk-summary-list__value"
    val saHeading        = "govuk-heading-m"
  }

  val elementsToMFADetails: Map[Int, MFADetails] = Map(
    2 -> MFADetails("Text message", "Ending with 28923"),
    3 -> MFADetails("Phone number", "Ending with 53839"),
    4 -> MFADetails("Authenticator app", "HMRC APP")
  )

  val htmlWithSA =
    view(ptEnrolmentDataModelOL(Some(CREDENTIAL_ID_1), testAccountDetailsWithSAOL), false)(
      FakeRequest(),
      testMessages
    )

  val htmlWithSAAndMtdit =
    view(ptEnrolmentDataModelOL(Some(CREDENTIAL_ID_1), testAccountDetailsWithSA), true)(
      FakeRequest(),
      testMessages
    )

  val documentWithSAAndMtdit = doc(htmlWithSAAndMtdit)

  val documentWithSA = doc(htmlWithSA)

  "PTEnrolmentOnAnotherAccount" should {
    "have the expected title" in {
      documentWithSA.title() shouldBe PTEnrolmentOtherAccountMessagesBothOL.title
    }
    "have the expected heading" in {
      documentWithSA
        .getElementsByClass(Selectors.heading)
        .text shouldBe PTEnrolmentOtherAccountMessagesBothOL.heading
    }

    validateTimeoutDialog(documentWithSA)
    validateTechnicalHelpLinkPresent(documentWithSA)
    validateAccessibilityStatementLinkPresent(documentWithSA)

    "have a body" that {
      val textElement = documentWithSA
        .getElementsByClass(Selectors.body)
        .get(0)
      "has the expected text" in {
        textElement.text shouldBe PTEnrolmentOtherAccountMessagesBothOL.text1
      }
    }
    "contain a summary list" that {
      val summaryListRows = documentWithSA
        .getElementsByClass(Selectors.summaryListRow)
      "includes the email" when {
        "the email is present in accountDetails" in {
          summaryListRows
            .get(0)
            .getElementsByClass(Selectors.summaryListKey)
            .text() shouldBe "Email"
          summaryListRows
            .get(0)
            .getElementsByClass(Selectors.summaryListValue)
            .text() shouldBe testAccountDetails.emailObfuscated.get
        }
      }

      "includes the last signed in date" in {
        summaryListRows
          .get(1)
          .getElementsByClass(Selectors.summaryListKey)
          .text() shouldBe "Last signed in"
        summaryListRows
          .get(1)
          .getElementsByClass(Selectors.summaryListValue)
          .text() shouldBe testAccountDetails.lastLoginDate.get
      }
      elementsToMFADetails.foreach { case (elementNumber, mfaDetails) =>
        s"include the ${mfaDetails.factorNameKey}" in {
          summaryListRows
            .get(elementNumber)
            .getElementsByClass(Selectors.summaryListKey)
            .text() shouldBe mfaDetails.factorNameKey
          summaryListRows
            .get(elementNumber)
            .getElementsByClass(Selectors.summaryListValue)
            .text() shouldBe mfaDetails.factorValue
        }
      }
    }
    "contains a link for if userId not recognised" that {
      val textElement = documentWithSA
      "has the correct text" in {
        documentWithSA.body.text() should include(PTEnrolmentOtherAccountMessagesBothOL.notMyUserId)
      }
      "contains a link to report suspicious Id" in {
        textElement
          .select("a.govuk-link")
          .get(2)
          .attr("href") should include(PTEnrolmentOtherAccountMessagesBothOL.fraudReportingUrl)
      }
    }
  }
}
