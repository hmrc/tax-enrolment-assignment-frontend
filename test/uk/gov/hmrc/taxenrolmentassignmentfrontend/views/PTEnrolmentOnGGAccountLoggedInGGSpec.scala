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
import uk.gov.hmrc.taxenrolmentassignmentfrontend.messages.PTEnrolmentOtherAccountMessagesBothGG
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.MFADetails
import uk.gov.hmrc.taxenrolmentassignmentfrontend.views.html.PTEnrolmentOnGGAccountLoggedInGG

class PTEnrolmentOnGGAccountLoggedInGGSpec extends ViewSpecHelper {

  lazy val view: PTEnrolmentOnGGAccountLoggedInGG =
    inject[PTEnrolmentOnGGAccountLoggedInGG]

  object Selectors {
    val heading = "govuk-heading-l"
    val body = "govuk-body"
    val summaryListRow = "govuk-summary-list__row"
    val summaryListKey = "govuk-summary-list__key"
    val summaryListValue = "govuk-summary-list__value"
    val saHeading = "govuk-heading-m"
  }

  val elementsToMFADetails: Map[Int, MFADetails] = Map(
    3 -> MFADetails("Text message", "Ending with 28923"),
    4 -> MFADetails("Phone number", "Ending with 53839"),
    5 -> MFADetails("Authenticator app", "HMRC APP")
  )

  val htmlWithSA =
    view(ptEnrolmentDataModel(Some(PT_USER_ID), testAccountDetailsWithSA))(
      FakeRequest(),
      testMessages
    )

  val documentWithSA = doc(htmlWithSA)

  val htmlNoEmail =
    view(
      ptEnrolmentDataModel(Some(NO_EMAIL_USER_ID), accountDetailsWithNoEmail)
    )(FakeRequest(), testMessages)
  val documentNoEmail = doc(htmlNoEmail)

  "PTEnrolmentOnAnotherAccount" should {
    "have the expected title" in {
      documentWithSA.title() shouldBe PTEnrolmentOtherAccountMessagesBothGG.title
    }
    "have the expected heading" in {
      documentWithSA
        .getElementsByClass(Selectors.heading)
        .text shouldBe PTEnrolmentOtherAccountMessagesBothGG.heading
    }

    validateTimeoutDialog(documentWithSA)
    validateTechnicalHelpLinkPresent(documentWithSA)
    validateAccessibilityStatementLinkPresent(documentWithSA)

    "have a body" that {
      val textElement = documentWithSA
        .getElementsByClass(Selectors.body)
        .get(0)
      "has the expected text" in {
        textElement.text shouldBe PTEnrolmentOtherAccountMessagesBothGG.text1
      }
    }
    "contain a summary list" that {
      val summaryListRows = documentWithSA
        .getElementsByClass(Selectors.summaryListRow)
      "includes the userId" in {
        summaryListRows
          .get(0)
          .getElementsByClass(Selectors.summaryListKey)
          .text() shouldBe "User ID"
        summaryListRows
          .get(0)
          .getElementsByClass(Selectors.summaryListValue)
          .text() shouldBe s"Ending with ${testAccountDetailsWithSA.userId}"
      }
      "includes the email" when {
        "the email is present in accountDetails" in {
          summaryListRows
            .get(1)
            .getElementsByClass(Selectors.summaryListKey)
            .text() shouldBe "Email"
          summaryListRows
            .get(1)
            .getElementsByClass(Selectors.summaryListValue)
            .text() shouldBe testAccountDetails.emailDecrypted.get
        }
      }
      "does not include the email" when {
        "the email is not present in accountDetails" in {
          documentNoEmail
            .getElementsByClass(Selectors.summaryListKey)
            .eachText() shouldNot contain("Email")
        }
      }

      "includes the last signed in date" in {
        summaryListRows
          .get(2)
          .getElementsByClass(Selectors.summaryListKey)
          .text() shouldBe "Last signed in"
        summaryListRows
          .get(2)
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
        documentWithSA.body.text() should include(PTEnrolmentOtherAccountMessagesBothGG.notMyUserId)
      }
      "contains a link to report suspicious Id" in {
        textElement
          .select("a.govuk-link")
          .get(2)
          .attr("href") should include(PTEnrolmentOtherAccountMessagesBothGG.fraudReportingUrl)
      }
    }
  }
}
