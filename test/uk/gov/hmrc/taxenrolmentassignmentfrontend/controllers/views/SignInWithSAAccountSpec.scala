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
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.messages.SignInAgainMessages
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.{ReportSuspiciousIDController, SignOutController}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.{AccountDetails, MFADetails}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.views.html.{ReportSuspiciousID, SignInWithSAAccount}

class SignInWithSAAccountSpec extends TestFixture {

  lazy val testTeaSessionCache = new TestTeaSessionCache
  lazy val signOutController = new SignOutController(mockAuthAction,mcc,testAppConfig,testTeaSessionCache)
  lazy val reportSuspiciousIDController = new ReportSuspiciousIDController(
    mockAuthAction,
    mockTeaSessionCache,
    mockMultipleAccountsOrchestrator,
    mcc,
    reportSuspiciousIDPage,
    logger,
    errorView
  )
  lazy val signInAgainPage: SignInWithSAAccount =inject[SignInWithSAAccount]
  lazy val reportSuspiciousIDPage: ReportSuspiciousID =inject[ReportSuspiciousID]
  lazy val result: HtmlFormat.Appendable = signInAgainPage(accountDetails)(FakeRequest(), testMessages)

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
    "********3214",
    Some("email1@test.com"),
    "Yesterday",
    mfaDetails
  )

  "The SignInAgain Page" should {
    "contain the correct title" in {
      doc(result).title shouldBe SignInAgainMessages.title
    }

    "contain the correct main header" in {
      doc(result).getElementsByClass(Selectors.headingXL).text shouldBe SignInAgainMessages.heading1
    }

    "contain the correct paragraph" in {
      val paragraph = doc(result).select("p." + Selectors.body)
      paragraph.get(0).getElementsByClass(Selectors.body).text() shouldBe SignInAgainMessages.paragraph
    }

    "contain the correct second heading" in {
      doc(result).getElementsByClass(Selectors.headingM).text shouldBe SignInAgainMessages.heading2
    }
    "contain a summary list" that {
      val summaryListRows =  doc(result)
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
            .text() shouldBe accountDetails.email.get
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
      doc(result).getElementById("reportId").text() shouldBe SignInAgainMessages.linkText
    }
    "contain the correct back link" in {
      doc(result).getElementsByClass(Selectors.backLink).text shouldBe SignInAgainMessages.backLink
    }
    "contain the correct button" in {
      doc(result).getElementsByClass(Selectors.button).text shouldBe SignInAgainMessages.continue
    }
  }
}
