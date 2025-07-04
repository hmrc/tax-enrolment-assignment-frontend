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
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.TestData.CREDENTIAL_ID
import uk.gov.hmrc.taxenrolmentassignmentfrontend.messages.EnrolledPTWithSAOnOtherAccountMessages
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.{AccountDetails, SCP}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.views.html.EnrolledForPTWithSAOnOtherAccount
import uk.gov.hmrc.crypto.Sensitive.SensitiveString

class EnrolledForPTWithSAOnOtherAccountSpec extends ViewSpecHelper {

  val view: EnrolledForPTWithSAOnOtherAccount =
    app.injector.instanceOf[EnrolledForPTWithSAOnOtherAccount]
  val userId                                  = "3214"

  val currentAccountDetails: AccountDetails = AccountDetails(
    identityProviderType = SCP,
    credId = "credId321",
    userId,
    Some(SensitiveString("email2@test.com")),
    Some("Today"),
    mfaDetails
  )
  val saAccountDetails: AccountDetails      = AccountDetails(
    identityProviderType = SCP,
    credId = CREDENTIAL_ID,
    userId,
    Some(SensitiveString("email1@test.com")),
    Some("Yesterday"),
    mfaDetails
  )
  val html: HtmlFormat.Appendable           =
    view(currentAccountDetails, saAccountDetails)(FakeRequest(), testMessages)
  val document: Document                    = doc(html)
  val htmlSA: HtmlFormat.Appendable         =
    view(currentAccountDetails, saAccountDetails)(FakeRequest(), testMessages)
  val documentSA: Document                  = doc(htmlSA)

  object Selectors {
    val heading         = "govuk-heading-xl"
    val body            = "govuk-body"
    val subHeading      = "govuk-heading-m"
    val smallSubHeading = "govuk-heading-s"
    val button          = "govuk-button"
    val form            = "form"
  }
  "EnrolledForPTWithSAOnOtherAccount" when {
    "the user has chosen to keep SA separate" should {
      "contain the correct title" in {
        documentSA.title shouldBe EnrolledPTWithSAOnOtherAccountMessages.title
      }
      "contain the correct header" in {
        documentSA
          .getElementsByClass("govuk-heading-l")
          .text shouldBe EnrolledPTWithSAOnOtherAccountMessages.heading
      }

      validateTimeoutDialog(documentSA)
      validateTechnicalHelpLinkPresent(documentSA)
      validateAccessibilityStatementLinkPresent(documentSA)
    }

    "the user has come from fraud reporting" should {
      "contain the correct title" in {
        document.title shouldBe EnrolledPTWithSAOnOtherAccountMessages.title
      }
      "contain the correct header" in {
        document
          .getElementsByClass("govuk-heading-l")
          .text shouldBe EnrolledPTWithSAOnOtherAccountMessages.heading
      }

      validateTimeoutDialog(document)
      validateTechnicalHelpLinkPresent(document)

      "contain the correct body" which {
        "have expected paragraphs that don't include SA" in {
          document
            .getElementsByTag("p")
            .get(0)
            .text shouldBe EnrolledPTWithSAOnOtherAccountMessages.paragraph1

          document
            .getElementsByTag("p")
            .get(1)
            .text shouldBe EnrolledPTWithSAOnOtherAccountMessages.paragraph2(userId)

          document
            .getElementById("ptaLink")
            .text shouldBe EnrolledPTWithSAOnOtherAccountMessages.ptaLinkText

          document
            .getElementById("ptaLink")
            .attr("href") should include(EnrolledPTWithSAOnOtherAccountMessages.ptaLink)

        }
      }
    }
  }
}
