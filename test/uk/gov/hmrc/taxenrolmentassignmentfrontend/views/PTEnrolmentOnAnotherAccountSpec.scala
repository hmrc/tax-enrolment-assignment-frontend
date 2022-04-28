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
import uk.gov.hmrc.taxenrolmentassignmentfrontend.config.AppConfig
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.TestData._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.TestFixture
import uk.gov.hmrc.taxenrolmentassignmentfrontend.messages.PTEnrolmentOtherAccountMesages
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
    MFADetails("mfaDetails.text", "28923"),
    MFADetails("mfaDetails.voice", "53839"),
    MFADetails("mfaDetails.totp", "HRMC APP")
  )

  val elementsToMFADetails: Map[Int, MFADetails] = Map(
    3 -> MFADetails("Text message", "Ending with 28923"),
    4 -> MFADetails("Phone number", "Ending with 53839"),
    5 -> MFADetails("Authenticator app", "HRMC APP")
  )

  val testAccountDetails = AccountDetails(
    userId = USER_ID,
    email = Some("email.otherUser@test.com"),
    lastLoginDate = "27 February 2022 at 12:00 PM",
    mfaDetails
  )
  val testAccountDetailsWithSA = AccountDetails(
    userId = PT_USER_ID,
    email = Some("email.otherUser@test.com"),
    lastLoginDate = "27 February 2022 at 12:00 PM",
    mfaDetails,
    hasSA = Some(true)
  )

  val accountDetailsWithNoEmail: AccountDetails = AccountDetails(
    userId = "9871",
    email = None,
    lastLoginDate = "27 February 2022 at 12:00 PM",
    mfaDetails = List(MFADetails("mfaDetails.text", "26543"))
  )

  val htmlSignedInWithSA =
    view(ptEnrolmentDataModel(Some(USER_ID), testAccountDetailsWithSA))(
      FakeRequest(),
      testMessages,
      appConfigForTest
    )

  val htmlWithSA =
    view(ptEnrolmentDataModel(Some(PT_USER_ID), testAccountDetailsWithSA))(
      FakeRequest(),
      testMessages,
      appConfigForTest
    )

  val htmlOtherAccountWithSA =
    view(ptEnrolmentDataModel(Some(PT_USER_ID), testAccountDetails))(
      FakeRequest(),
      testMessages,
      appConfigForTest
    )

  val documentWithSA = doc(htmlWithSA)
  val documentSignedInWithSA = doc(htmlSignedInWithSA)
  val documentOtherAccountWithSA = doc(htmlOtherAccountWithSA)

  val htmlNoEmail =
    view(
      ptEnrolmentDataModel(Some(NO_EMAIL_USER_ID), accountDetailsWithNoEmail)
    )(FakeRequest(), testMessages, appConfigForTest)
  val documentNoEmail = doc(htmlNoEmail)

  val html =
    view(ptEnrolmentDataModel(None, testAccountDetails))(
      FakeRequest(),
      testMessages,
      appConfigForTest
    )
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
        documentWithSA
          .getElementsByClass(Selectors.body)
          .get(2)
          .select("a")
          .attr("href") shouldBe "/protect-tax-info/logout"
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
            .text() shouldBe testAccountDetails.email.get
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
          .text() shouldBe testAccountDetails.lastLoginDate
      }
      elementsToMFADetails.foreach {
        case (elementNumber, mfaDetails) =>
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
        .getElementById("inset_text")
      "has the correct text" in {
        textElement.text() shouldBe PTEnrolmentOtherAccountMesages.notMyUserId
      }
      "contains a link to report suspicious Id" in {
        textElement
          .select("a")
          .attr("href") shouldBe "/protect-tax-info/no-pt-enrolment/contact-hmrc-pta"
      }
    }

    "contain self-assessment information" when {
      "another account outside the session holds the PT enrolment session and also has access to SA" that {
        val textElement = documentOtherAccountWithSA
          .getElementsByClass(Selectors.body)
          .get(3)
        "has the expected heading" in {
          documentOtherAccountWithSA
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
      "the current account has SA but the PT enrolment is on another account" that {
        val textElement = documentWithSA
          .getElementsByClass(Selectors.body)
          .get(3)
        "has the expected heading" in {
          documentWithSA
            .getElementsByClass(Selectors.saHeading)
            .text() shouldBe PTEnrolmentOtherAccountMesages.saHeading
        }
        "has the expected text" in {
          textElement.text() shouldBe PTEnrolmentOtherAccountMesages.saText2
        }
        "has a link to self-assessment" in {
          textElement
            .select("a")
            .attr("href") shouldBe PTEnrolmentOtherAccountMesages.saUrl
        }
      }

      "the account in session has access to SA and has the PT enrolment" that {
        val textElement = documentSignedInWithSA
          .getElementsByClass(Selectors.body)
          .get(3)
        "has the expected heading" in {
          documentWithSA
            .getElementsByClass(Selectors.saHeading)
            .text() shouldBe PTEnrolmentOtherAccountMesages.saHeading
        }
        "has the expected text" in {
          textElement.text() shouldBe PTEnrolmentOtherAccountMesages.saText3
        }
        "has a link to self-assessment" in {
          textElement
            .select("a")
            .attr("href") shouldBe PTEnrolmentOtherAccountMesages.saUrl
        }
      }
    }
    "not contain self-assessment information" when {
      "the user does not have an SA enrolment on any associated accounts" in {
        document
          .getElementsByClass(Selectors.saHeading)
          .size() shouldBe 0
        document
          .getElementsByClass(Selectors.body)
          .size() shouldBe 3
      }
    }
  }
}
