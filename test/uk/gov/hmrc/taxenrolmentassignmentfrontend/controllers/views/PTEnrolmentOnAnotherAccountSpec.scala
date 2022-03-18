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
import uk.gov.hmrc.taxenrolmentassignmentfrontend.config.AppConfig
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.helpers.TestFixture
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.messages.PTEnrolmentOtherAccountMesages
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.{
  AccountDetails,
  MFADetails
}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.views.html.PTEnrolmentOnAnotherAccount

class PTEnrolmentOnAnotherAccountSpec extends TestFixture {

  lazy val view: PTEnrolmentOnAnotherAccount =
    inject[PTEnrolmentOnAnotherAccount]

  lazy val appConfigForTest = new AppConfig(servicesConfig) {
    override lazy val selfAssessmentUrl: String =
      PTEnrolmentOtherAccountMesages.saUrl
  }

  object Selectors {
    val heading = "govuk-heading-xl"
    val body = "govuk-body"
    val summaryListRow = "govuk-summary-list__row"
    val summaryListKey = "govuk-summary-list__key"
    val summaryListValue = "govuk-summary-list__value"
    val saHeading = "govuk-heading-m"
  }

  val mfaDetails = Seq(
    MFADetails("Text message", "07390328923"),
    MFADetails("Voice call", "0193453839"),
    MFADetails("Authenticator App", "HRMC APP")
  )

  val elementsToMFADetails: Map[Int, MFADetails] = Map(
    2 -> MFADetails("Text message", "07390328923"),
    3 -> MFADetails("Voice call", "0193453839"),
    4 -> MFADetails("Authenticator App", "HRMC APP")
  )

  val accountDetails = AccountDetails(
    "********3214",
    Some("email1@test.com"),
    "Yesterday",
    mfaDetails
  )

  val htmlWithSA =
    view(accountDetails, true)(FakeRequest(), testMessages, appConfigForTest)

  val documentWithSA = doc(htmlWithSA)

  val htmlNoEmail =
    view(accountDetails.copy(email = None), false)(
      FakeRequest(),
      testMessages,
      appConfigForTest
    )
  val documentNoEmail = doc(htmlNoEmail)

  val html =
    view(accountDetails, false)(FakeRequest(), testMessages, appConfigForTest)
  val document = doc(html)

  "PTEnrolmentOnAnotherAccount" should {
    "have the expected title" in {
      documentWithSA.title() shouldBe PTEnrolmentOtherAccountMesages.title
    }
    "have the expected heading" in {
      documentWithSA
        .getElementsByClass(Selectors.heading)
        .text shouldBe PTEnrolmentOtherAccountMesages.heading
    }
    "have a body" that {
      val textElement = documentWithSA
        .getElementsByClass(Selectors.body)
        .get(0)
      "has the expected text" in {
        textElement.text shouldBe PTEnrolmentOtherAccountMesages.text1
      }
      "contains a link to signin again" in {
        textElement
          .select("a")
          .attr("href") shouldBe "/tax-enrolment-assignment-frontend/logout"
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
          .text() shouldBe accountDetails.userId
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
      "does not include the email" when {
        "the email is not present in accountDetails" in {
          documentNoEmail
            .getElementsByClass(Selectors.summaryListKey)
            .eachText() shouldNot contain("Email")
        }
      }
      elementsToMFADetails.foreach {
        case (elementNumber, mfaDetails) =>
          s"include the ${mfaDetails.factorName}" in {
            summaryListRows
              .get(elementNumber)
              .getElementsByClass(Selectors.summaryListKey)
              .text() shouldBe mfaDetails.factorName
            summaryListRows
              .get(elementNumber)
              .getElementsByClass(Selectors.summaryListValue)
              .text() shouldBe mfaDetails.factorValue
          }
      }
    }
    "contains a link for if userId not recognised" that {
      val textElement = documentWithSA
        .getElementsByClass(Selectors.body)
        .get(1)
      "has the correct text" in {
        textElement.text() shouldBe PTEnrolmentOtherAccountMesages.notMyUserId
      }
      "has the link to fraud reporting" in {
        textElement
          .select("a")
          .attr("href") shouldBe PTEnrolmentOtherAccountMesages.fraudReportingUrl
      }
    }

    "contain self-assessment information" when {
      "currentAccountHasSA is true" that {
        val textElement = documentWithSA
          .getElementsByClass(Selectors.body)
          .get(2)
        "has the expected heading" in {
          documentWithSA
            .getElementsByClass(Selectors.saHeading)
            .text() shouldBe PTEnrolmentOtherAccountMesages.saHeading
        }
        "has the expected text" in {
          textElement.text() shouldBe PTEnrolmentOtherAccountMesages.saText
        }
        "has a link to self-assessment" in {
          textElement
            .select("a")
            .attr("href") shouldBe PTEnrolmentOtherAccountMesages.saUrl
        }
      }
    }
    "not contain self-assessment information" when {
      "currentAccountHasSA is true" in {
        document
          .getElementsByClass(Selectors.saHeading)
          .size() shouldBe 0
        document
          .getElementsByClass(Selectors.body)
          .size() shouldBe 2
      }
    }
  }
}
