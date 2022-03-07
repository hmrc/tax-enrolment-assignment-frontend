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

import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import play.api.test.FakeRequest
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.helpers.TestFixture
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.messages.{
  AssociatedAccountsMessages,
  EnrolCurrentUserMessages
}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.forms.AccountsBelongToUserForm
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.AccountDetails
import uk.gov.hmrc.taxenrolmentassignmentfrontend.views.html.AssociatedAccounts

class AssociatedAccountsSpec extends TestFixture {

  lazy val view: AssociatedAccounts = inject[AssociatedAccounts]
  val fixedCurrentUserAccount = AccountDetails(
    "*********9871",
    "olivia.cunningham32@gmail.com",
    "12 December 2021"
  )
  val fixedOtherAccount1 = AccountDetails(
    "*********1234",
    "olivia.cunningham32@gmail.com",
    "21 October 2021"
  )

  val fixedOtherAccount2 = AccountDetails(
    "*********9872",
    "olivia.cunningham@zetec.com",
    "19 October 2021"
  )

  val accountDetailsToElementNumber: Map[AccountDetails, Int] = Map(
    fixedCurrentUserAccount -> 0,
    fixedOtherAccount1 -> 1,
    fixedOtherAccount2 -> 2
  )

  val form = AccountsBelongToUserForm.accountsBelongToUserForm

  object Selectors {
    val heading = "govuk-heading-xl"
    val userIds = "govuk-heading-m"
    val signedInTag = "govuk-tag"
    val summaryList = "govuk-summary-list"
    val summaryListRow = "govuk-summary-list__row"
    val summaryListKey = "govuk-summary-list__key"
    val summaryListValue = "govuk-summary-list__value"
    val radios = "govuk-radios__item"
    val radioInput = "govuk-radios__input"
    val radioLables = "govuk-label govuk-radios__label"
    val errorSummaryTitle = "govuk-error-summary__title"
    val errorSummaryList = "govuk-list govuk-error-summary__list"
    val button = "govuk-button"
  }

  "AssociatedAccounts" when {
    "there are no form errors" should {
      val html =
        view(
          form,
          fixedCurrentUserAccount,
          List(fixedOtherAccount1, fixedOtherAccount2)
        )(FakeRequest(), testMessages)
      val document = doc(html)
      "have the expected title" in {
        document.title() shouldBe AssociatedAccountsMessages.title
      }

      "have the expected heading" in {
        document
          .getElementsByClass(Selectors.heading)
          .text shouldBe AssociatedAccountsMessages.heading
      }
      "have user account details summary" that {
        val userIds = document.getElementsByClass(Selectors.userIds)
        val summaryLists = document.getElementsByClass(Selectors.summaryList)
        "includes 3 userIds" in {
          userIds.size() shouldBe 3
        }
        "includes 3 summary list" in {
          summaryLists.size() shouldBe 3
        }
        "has the currently signed in user at the top" which {
          validateAccountDetailsCorrect(
            userIds,
            summaryLists,
            fixedCurrentUserAccount
          )
        }
        List(fixedOtherAccount1, fixedOtherAccount2).foreach { accountDetails =>
          s"has ${accountDetails.userId} account" which {
            validateAccountDetailsCorrect(userIds, summaryLists, accountDetails)
          }
        }
      }
      "have radio buttons" that {
        val radioButtons = document.getElementsByClass(Selectors.radios)
        "have the option to select if userIds are recognised" in {
          val radioButton1 = radioButtons
            .get(0)
          radioButton1
            .getElementsByClass(Selectors.radioLables)
            .text() shouldBe AssociatedAccountsMessages.radioDoesRecogniseIds
          radioButton1
            .getElementsByClass(Selectors.radioInput)
            .attr("value") shouldBe "yes"
        }
        "has the option to select userIds not recognised" in {
          val radioButton2 = radioButtons
            .get(1)
          radioButton2
            .getElementsByClass(Selectors.radioLables)
            .text() shouldBe AssociatedAccountsMessages.radioDoesNotRecogniseIds
          radioButton2
            .getElementsByClass(Selectors.radioInput)
            .attr("value") shouldBe "no"
        }
      }
      "contain the correct button" in {
        document
          .getElementsByClass(Selectors.button)
          .text shouldBe EnrolCurrentUserMessages.button
      }
    }

    "there are form errors" should {
      val formWithErrors = AccountsBelongToUserForm.accountsBelongToUserForm
        .bind(Map("recogniseUserIds" -> ""))
      val html =
        view(
          formWithErrors,
          fixedCurrentUserAccount,
          List(fixedOtherAccount1, fixedOtherAccount2)
        )(FakeRequest(), testMessages)
      val document = doc(html)

      "have an error summary" that {
        "has title" in {
          document
            .getElementsByClass(Selectors.errorSummaryTitle)
            .text() shouldBe AssociatedAccountsMessages.errorTitle
        }
        "contains a message that links to field with error" in {
          val errorSummary = document
            .getElementsByClass(Selectors.errorSummaryList)
            .first()
          errorSummary
            .select("a")
            .attr("href") shouldBe "#check-ids-form"
          errorSummary.text() shouldBe AssociatedAccountsMessages.errorMessage
        }
      }
    }
  }

  def validateAccountDetailsCorrect(userIdsElements: Elements,
                                    summaryListElements: Elements,
                                    accountDetails: AccountDetails): Unit = {
    val elementNumber: Int = accountDetailsToElementNumber(accountDetails)
    val isCurrentlySignedIn = elementNumber.equals(0)

    "includes the userId" in {
      userIdsElements.get(elementNumber).text() should include(
        AssociatedAccountsMessages.userId(accountDetails.userId)
      )
    }
    if (isCurrentlySignedIn) {
      "includes the signed in tag" in {
        userIdsElements
          .get(elementNumber)
          .getElementsByClass(Selectors.signedInTag)
          .text() shouldBe "SIGNED IN"
      }
    } else {
      "does not include the signed in tag" in {
        userIdsElements
          .get(elementNumber)
          .getElementsByClass(Selectors.signedInTag)
          .size() shouldBe 0
      }
    }

    "includes the summary list" that {
      val summaryList: Element = summaryListElements.get(elementNumber)
      val summaryListKeys =
        summaryList.getElementsByClass(Selectors.summaryListKey)
      val summaryListValues =
        summaryList.getElementsByClass(Selectors.summaryListValue)
      "has an email address" in {
        summaryListKeys.get(0).text() shouldBe "Email"
        summaryListValues
          .get(0)
          .text() shouldBe accountDetails.email
      }
      "has a last signed in date" in {
        summaryListKeys.get(1).text() shouldBe "Last signed in"
        summaryListValues
          .get(1)
          .text() shouldBe accountDetails.lastLoginDate
      }
    }
  }
}
