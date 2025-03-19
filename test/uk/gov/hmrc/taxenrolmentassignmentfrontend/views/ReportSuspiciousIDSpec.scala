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
import play.api.test.FakeRequest
import play.twirl.api.HtmlFormat
import uk.gov.hmrc.crypto.Sensitive.SensitiveString
import uk.gov.hmrc.taxenrolmentassignmentfrontend.config.AppConfig
import uk.gov.hmrc.taxenrolmentassignmentfrontend.messages.ReportSuspiciousIDMessages
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.{AccountDetails, MFADetails, SCP}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.views.html.{ReportSuspiciousIDGateway, ReportSuspiciousIDOneLogin}

class ReportSuspiciousIDSpec extends ViewSpecHelper {

  val reportSuspiciousIdGGView: ReportSuspiciousIDGateway =
    app.injector.instanceOf[ReportSuspiciousIDGateway]

  val reportSuspiciousIdOLView: ReportSuspiciousIDOneLogin =
    app.injector.instanceOf[ReportSuspiciousIDOneLogin]

  val appConfig: AppConfig = app.injector.instanceOf[AppConfig]

  object Selectors {
    val backLink = "govuk-back-link"
    val heading = "govuk-heading-l"
    val body = "govuk-body"
    val summaryListRow = "govuk-summary-list__row"
    val summaryListKey = "govuk-summary-list__key"
    val summaryListValue = "govuk-summary-list__value"
    val links = "govuk-link"
  }

  val mfaDetails: Seq[MFADetails] = Seq(MFADetails("mfaDetails.text", "28923"))

  val accountDetails: AccountDetails =
    AccountDetails(
      identityProviderType = SCP,
      "credId",
      "4533",
      Some(SensitiveString("email1@test.com")),
      Some("Yesterday"),
      mfaDetails
    )

  def view(accountDetails: AccountDetails = accountDetails): HtmlFormat.Appendable =
    reportSuspiciousIdGGView(accountDetails, appConfig)(FakeRequest(), testMessages)

  val viewSA: HtmlFormat.Appendable =
    reportSuspiciousIdGGView(accountDetails, appConfig)(FakeRequest(), testMessages)

  val document: Document = doc(view())
  val documentSA: Document = doc(viewSA)

  "The Report suspicious ID Page for GG account" should {
    "have a back link" that {
      val backLink =
        document.getElementsByClass(Selectors.backLink)
      "has the text back" in {
        backLink.text() shouldBe "Back"
      }
      "has the expected href" in {
        backLink.get(0).attr("href") shouldBe "#"
      }
    }

    validateTimeoutDialog(document)
    validateTechnicalHelpLinkPresent(document)
    validateAccessibilityStatementLinkPresent(document)

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
          .text() shouldBe accountDetails.emailDecrypted.get
      }

      "includes the last signed in date" in {
        suspiciousIdDetailsRows
          .get(2)
          .getElementsByClass(Selectors.summaryListKey)
          .text() shouldBe "Last signed in"
        suspiciousIdDetailsRows
          .get(2)
          .getElementsByClass(Selectors.summaryListValue)
          .text() shouldBe accountDetails.lastLoginDate.get
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
        .text() shouldBe ReportSuspiciousIDMessages.paragraphGG
    }

    "contains a valid paragraph details for the link" in {
      val paragraph = document.select("p." + Selectors.body)

      paragraph
        .get(1)
        .text() shouldBe ReportSuspiciousIDMessages.linkTextGG + " " + ReportSuspiciousIDMessages.postLinkTextGG
    }

    "contains the relevant link to report the fraud" in {
      val links = document.select("""a[id="reportLink"]""")

      links.get(0).attr("href") shouldBe ReportSuspiciousIDMessages.linkGG
    }
  }

  "The Report suspicious ID Page for OL account" should {
    def view(accountDetails: AccountDetails = accountDetails): HtmlFormat.Appendable =
      reportSuspiciousIdOLView(accountDetails, appConfig)(FakeRequest(), testMessages)
    val document: Document = doc(view())
    "have a back link" that {
      val backLink =
        document.getElementsByClass(Selectors.backLink)
      "has the text back" in {
        backLink.text() shouldBe "Back"
      }
      "has the expected href" in {
        backLink.get(0).attr("href") shouldBe "#"
      }
    }

    validateTimeoutDialog(document)
    validateTechnicalHelpLinkPresent(document)
    validateAccessibilityStatementLinkPresent(document)

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
      "includes the email field with correct value" in {
        suspiciousIdDetailsRows
          .get(0)
          .getElementsByClass(Selectors.summaryListKey)
          .text() shouldBe "Email"
        suspiciousIdDetailsRows
          .get(0)
          .getElementsByClass(Selectors.summaryListValue)
          .text() shouldBe accountDetails.emailObfuscated.get
      }

      "includes the last signed in date" in {
        suspiciousIdDetailsRows
          .get(1)
          .getElementsByClass(Selectors.summaryListKey)
          .text() shouldBe "Last signed in"
        suspiciousIdDetailsRows
          .get(1)
          .getElementsByClass(Selectors.summaryListValue)
          .text() shouldBe accountDetails.lastLoginDate.get
      }
      "includes the text message field with correct value" in {
        suspiciousIdDetailsRows
          .get(2)
          .getElementsByClass(Selectors.summaryListKey)
          .text() shouldBe "Text message"
        suspiciousIdDetailsRows
          .get(2)
          .getElementsByClass(Selectors.summaryListValue)
          .text() shouldBe s"Ending with ${accountDetails.mfaDetails.head.factorValue}"
      }
    }

    "contains a valid paragraph details" in {
      val paragraph = document.select("p." + Selectors.body)

      paragraph
        .get(0)
        .text() shouldBe ReportSuspiciousIDMessages.paragraphOL
    }

    "contains a valid paragraph details for the link" in {
      val paragraph = document.select("p." + Selectors.body)

      paragraph
        .get(1)
        .text() shouldBe ReportSuspiciousIDMessages.linkTextOL
    }

    "contains the relevant link to report the fraud" in {
      val links = document.select("""a[id="reportLink"]""")

      links.get(0).attr("href") shouldBe ReportSuspiciousIDMessages.linkOL
    }
  }

  "The report suspicious ID Page when email and last login date fields are missing" should {
    "not render the email or last signed in fields" in {
      val ad = accountDetails.copy(email = None, lastLoginDate = None)
      val document: Document = doc(view(ad))
      val suspiciousIdDetailsRows =
        document.getElementsByClass(Selectors.summaryListRow)
      val renderedElements = suspiciousIdDetailsRows.text()
      renderedElements.contains("Email") shouldBe false
      renderedElements.contains("Last signed in") shouldBe false
    }
  }
}
