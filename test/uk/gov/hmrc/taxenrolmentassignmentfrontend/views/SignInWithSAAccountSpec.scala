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

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import play.api.test.FakeRequest
import uk.gov.hmrc.crypto.Sensitive.SensitiveString
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.SignOutController
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.TestData.CREDENTIAL_ID
import uk.gov.hmrc.taxenrolmentassignmentfrontend.messages.SignInAgainMessages
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.{AccountDetails, MFADetails}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.views.html.{ReportSuspiciousID, SignInWithSAAccount}

class SignInWithSAAccountSpec extends ViewSpecHelper {

  lazy val testTeaSessionCache = new TestTeaSessionCache
  lazy val signOutController = new SignOutController(
    mockAuthAction,
    mcc,
    appConfig,
    testTeaSessionCache,
    logger
  )
  lazy val signInAgainPage: SignInWithSAAccount = inject[SignInWithSAAccount]
  lazy val reportSuspiciousIDPage: ReportSuspiciousID =
    inject[ReportSuspiciousID]
  lazy val view =
    signInAgainPage(accountDetails)(FakeRequest(), testMessages)
  lazy val document: Document =
    Jsoup.parse(view.toString())

  object Selectors {
    val headingXL = "govuk-heading-xl"
    val headingM = "govuk-heading-m"
    val body = "govuk-body"
    val backLink = "govuk-back-link"
    val button = "govuk-button"
    val summaryListRow = "govuk-summary-list__row"
    val summaryListKey = "govuk-summary-list__key"
    val summaryListValue = "govuk-summary-list__value"
    val link = "govuk-link"
  }

  val mfaDetails = Seq(
    MFADetails("Text message", "07390328923"),
    MFADetails("Voice call", "0193453839"),
    MFADetails("Authenticator App", "HRMC APP")
  )

  val elementsToMFADetails: Map[Int, MFADetails] = Map(
    3 -> MFADetails("Text message", "07390328923"),
    4 -> MFADetails("Voice call", "0193453839"),
    5 -> MFADetails("Authenticator App", "HRMC APP")
  )

  val accountDetails = AccountDetails(
    credId = CREDENTIAL_ID,
    "********3214",
    Some(SensitiveString("email1@test.com")),
    "Yesterday",
    mfaDetails
  )

  "The SignInAgain Page" should {
    "contain the correct title" in {
      document.title shouldBe SignInAgainMessages.title
    }

    "contain the correct main header" in {
      document
        .getElementsByClass(Selectors.headingXL)
        .text shouldBe SignInAgainMessages.heading
    }

    "contain the correct paragraph" in {
      val paragraph = document.select("p." + Selectors.body)
      paragraph
        .get(0)
        .getElementsByClass(Selectors.body)
        .text() shouldBe SignInAgainMessages.paragraph
    }

    "contain the correct second heading" in {
      document
        .getElementsByClass(Selectors.headingM)
        .text shouldBe SignInAgainMessages.heading2
    }
    "contain a summary list" that {
      val summaryListRows = document
        .getElementsByClass(Selectors.summaryListRow)
      "includes the userId" in {
        summaryListRows
          .get(0)
          .getElementsByClass(Selectors.summaryListKey)
          .text() shouldBe "User ID"
        summaryListRows
          .get(0)
          .getElementsByClass(Selectors.summaryListValue)
          .text() shouldBe s"Ending with ${accountDetails.userId}"
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
            .text() shouldBe accountDetails.emailDecrypted.get
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
          .text() shouldBe accountDetails.lastLoginDate
      }
      elementsToMFADetails.foreach {
        case (elementNumber, mfaDetails) =>
          s"include the key - ${mfaDetails.factorNameKey}" in {
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

    "correct gov-uk link target and link text" in {
      document
        .getElementById("reportId")
        .text() shouldBe SignInAgainMessages.linkText
    }
    "contain the correct back link" in {
      val backLink = document
        .getElementsByClass(Selectors.backLink)
      backLink.text shouldBe SignInAgainMessages.backLink
      backLink.get(0).attr("href") shouldBe "#"
    }
    "contain the correct button" in {
      document
        .getElementsByClass(Selectors.button)
        .text shouldBe SignInAgainMessages.continue
    }

    validateTimeoutDialog(document)
    validateTechnicalHelpLinkPresent(document)
    validateAccessibilityStatementLinkPresent(document)

  }
}
