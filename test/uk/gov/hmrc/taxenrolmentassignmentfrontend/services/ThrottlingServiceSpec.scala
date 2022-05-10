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

package uk.gov.hmrc.taxenrolmentassignmentfrontend.services

import uk.gov.hmrc.auth.core.{Enrolment, EnrolmentIdentifier}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes.{MULTIPLE_ACCOUNTS, PT_ASSIGNED_TO_CURRENT_USER, PT_ASSIGNED_TO_OTHER_USER, SA_ASSIGNED_TO_CURRENT_USER, SA_ASSIGNED_TO_OTHER_USER, SINGLE_ACCOUNT}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.TestFixture
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.enums.EnrolmentEnum.hmrcPTKey

class ThrottlingServiceSpec extends TestFixture {

  val throttlingservice = new ThrottlingService(mockLegacyAuthConnector, mockAppConfig)
  val validNonRealNino = "QQ123456A"

  "isNinoWithinThrottleThreshold" should {
    case class TestScenario(testName: String, nino: String, percentage: Int)
    "return Exception" when {
      List(
        TestScenario("Nino is invalid format (all letters)", "JWAAAAAAA", 10),
        TestScenario("Nino is invalid format (empty)", "", 10),
        TestScenario("Nino is invalid format (not correct length 8)", "JW000000", 10),
        TestScenario("Nino is invalid format (not correct length 7)", "JW00000", 10),
        TestScenario("Nino is invalid format (not correct length 6)", "JW0000", 10),
        TestScenario("Nino is invalid format (not correct length 5)", "JW000", 10),
        TestScenario("Nino is invalid format (not correct length 4)", "JW00", 10),
        TestScenario("Nino is invalid format (not correct length 3)", "JW0", 10),
        TestScenario("Nino is invalid format (not correct length 2)", "JW", 10),
        TestScenario("Nino is invalid format (not correct length 1)", "J", 10),
        TestScenario("percentage < 0 but Nino is correct format", validNonRealNino, -1)
      ).foreach(test =>
        s"${test.testName}" in {
          intercept[IllegalArgumentException](throttlingservice.isNinoWithinThrottleThreshold(test.nino, test.percentage))
      })
    }
    "return true" when {
      List(
        TestScenario("Nino last 2 digits == percentage (percentage is 0)", "QQ123400A", 0),
        TestScenario("Nino last 2 digits == percentage (percentage is 1)", "QQ123401A", 1),
        TestScenario("Nino last 2 digits == percentage (percentage is an amount)", "QQ123455A", 55),
        TestScenario("Nino last 2 digits == percentage (percentage is max)", "QQ123499A", 99),
        TestScenario("Nino last 2 digits < percentage (percentage is one above)", "QQ123401A", 2),
        TestScenario("Nino last 2 digits < percentage (percentage is an amount above)", "QQ123401A", 50),
        TestScenario("Nino last 2 digits < percentage (percentage is max amount above)", "QQ123401A", 99)
      ).foreach(test =>
        s"${test.testName}" in {
          throttlingservice.isNinoWithinThrottleThreshold(test.nino, test.percentage) shouldBe true
        })
    }
    "return false" when {
      List(
        TestScenario("percentage is > 100", validNonRealNino, 101),
        TestScenario("percentage is 100", validNonRealNino, 100),
        TestScenario("Nino last 2 digits > percentage by 1", "QQ123411A", 10),
        TestScenario("Nino last 2 digits > percentage by an amount", "QQ123456A", 10),
        TestScenario("Nino last 2 digits > percentage by max amount", "QQ123499A", 10)
      ).foreach(test =>
        s"${test.testName}" in {
          throttlingservice.isNinoWithinThrottleThreshold(test.nino, test.percentage) shouldBe false
        })
    }
  }
  "shouldAccountTypeBeThrottled" should {
    case class TestScenario(testName: String, nino: String, percentage: Int, accountType: AccountTypes.Value)
    "return true" when {
      List(
        TestScenario(s"account type is $PT_ASSIGNED_TO_CURRENT_USER, valid Nino below threshold", "QQ123405A", 6, PT_ASSIGNED_TO_CURRENT_USER),
        TestScenario(s"account type is $PT_ASSIGNED_TO_OTHER_USER, valid Nino below threshold", "QQ123405A", 6, PT_ASSIGNED_TO_OTHER_USER),
        TestScenario(s"account type is $MULTIPLE_ACCOUNTS, valid Nino below threshold", "QQ123405A", 6, MULTIPLE_ACCOUNTS),
        TestScenario(s"account type is $SA_ASSIGNED_TO_CURRENT_USER, valid Nino below threshold", "QQ123405A", 6, SA_ASSIGNED_TO_CURRENT_USER),
        TestScenario(s"account type is $SA_ASSIGNED_TO_OTHER_USER, valid Nino below threshold", "QQ123405A", 6, SA_ASSIGNED_TO_OTHER_USER),
        TestScenario(s"account type is $PT_ASSIGNED_TO_CURRENT_USER, valid Nino equal to threshold", "QQ123405A", 5, PT_ASSIGNED_TO_CURRENT_USER),
        TestScenario(s"account type is $PT_ASSIGNED_TO_OTHER_USER, valid Nino equal to threshold", "QQ123405A", 5, PT_ASSIGNED_TO_OTHER_USER),
        TestScenario(s"account type is $MULTIPLE_ACCOUNTS, valid Nino equal to threshold", "QQ123405A", 5, MULTIPLE_ACCOUNTS),
        TestScenario(s"account type is $SA_ASSIGNED_TO_CURRENT_USER, valid Nino equal to threshold", "QQ123405A", 5, SA_ASSIGNED_TO_CURRENT_USER),
        TestScenario(s"account type is $SA_ASSIGNED_TO_OTHER_USER, vvalid Nino equal to threshold", "QQ123405A", 5, SA_ASSIGNED_TO_OTHER_USER)
      ).foreach(test =>
        s"${test.testName}" in {
          throttlingservice.shouldAccountTypeBeThrottled(test.accountType, test.percentage, test.nino) shouldBe true
        })
    }
    "return false" when {
      List(
        TestScenario(s"account type is $PT_ASSIGNED_TO_CURRENT_USER, valid Nino above threshold", "QQ123405A", 4, PT_ASSIGNED_TO_CURRENT_USER),
        TestScenario(s"account type is $PT_ASSIGNED_TO_OTHER_USER, valid Nino above threshold", "QQ123405A", 4, PT_ASSIGNED_TO_OTHER_USER),
        TestScenario(s"account type is $MULTIPLE_ACCOUNTS, valid Nino above threshold", "QQ123405A", 4, MULTIPLE_ACCOUNTS),
        TestScenario(s"account type is $SA_ASSIGNED_TO_CURRENT_USER, valid Nino above threshold", "QQ123405A", 4, SA_ASSIGNED_TO_CURRENT_USER),
        TestScenario(s"account type is $SA_ASSIGNED_TO_OTHER_USER, valid Nino above threshold", "QQ123405A", 4, SA_ASSIGNED_TO_OTHER_USER),
        TestScenario(s"account type is $PT_ASSIGNED_TO_CURRENT_USER, threshold is 100", "QQ123405A", 100, PT_ASSIGNED_TO_CURRENT_USER),
        TestScenario(s"account type is $PT_ASSIGNED_TO_OTHER_USER, threshold is 100", "QQ123405A", 100, PT_ASSIGNED_TO_OTHER_USER),
        TestScenario(s"account type is $MULTIPLE_ACCOUNTS, threshold is 100", "QQ123405A", 100, MULTIPLE_ACCOUNTS),
        TestScenario(s"account type is $SA_ASSIGNED_TO_CURRENT_USER, threshold is 100", "QQ123405A", 100, SA_ASSIGNED_TO_CURRENT_USER),
        TestScenario(s"account type is $SA_ASSIGNED_TO_OTHER_USER, threshold is 100", "QQ123405A", 100, SA_ASSIGNED_TO_OTHER_USER),
        TestScenario(s"account type is $SINGLE_ACCOUNT, Valid Nino below threshold", "QQ123405A", 6, SINGLE_ACCOUNT),
        TestScenario(s"account type is $SINGLE_ACCOUNT, Valid Nino equal threshold", "QQ123406A", 6, SINGLE_ACCOUNT),
        TestScenario(s"account type is $SINGLE_ACCOUNT, Invalid Nino", "QQ", 1, SINGLE_ACCOUNT)
      ).foreach(test =>
        s"${test.testName}" in {
          throttlingservice.shouldAccountTypeBeThrottled(test.accountType, test.percentage, test.nino) shouldBe false
        })
    }
    "return Exception" when {
      List(
        TestScenario(s"account type is $PT_ASSIGNED_TO_CURRENT_USER, Invalid nino", "QQ", 1, PT_ASSIGNED_TO_CURRENT_USER),
        TestScenario(s"account type is $PT_ASSIGNED_TO_OTHER_USER, Invalid nino", "QQ", 1, PT_ASSIGNED_TO_OTHER_USER),
        TestScenario(s"account type is $MULTIPLE_ACCOUNTS, Invalid nino", "QQ", 1, MULTIPLE_ACCOUNTS),
        TestScenario(s"account type is $SA_ASSIGNED_TO_CURRENT_USER, Invalid nino", "QQ", 1, SA_ASSIGNED_TO_CURRENT_USER),
        TestScenario(s"account type is $SA_ASSIGNED_TO_OTHER_USER, Invalid nino", "QQ", 1, SA_ASSIGNED_TO_OTHER_USER)
      ).foreach(test =>
        s"${test.testName}" in {
          intercept[IllegalArgumentException](throttlingservice.shouldAccountTypeBeThrottled(test.accountType, test.percentage, test.nino))
        })
    }
  }

  "addPTEnrolmentToEnrolments" should {
    "add new enrolment to existing set of enrolments" in {
      val newEnrolment = Enrolment(s"$hmrcPTKey", Seq(EnrolmentIdentifier("NINO", "foobarwizz")), "Activated", None)
      val setOfExistingEnrolments = Set(
        Enrolment("foo", Seq(EnrolmentIdentifier("NINO", "foo")), "Activated", None),
        Enrolment("bar", Seq(EnrolmentIdentifier("NINO", "foo")), "Activated", None)
      )

      throttlingservice.addPTEnrolmentToEnrolments(setOfExistingEnrolments, "foobarwizz") shouldBe setOfExistingEnrolments + newEnrolment
    }
  }

  "throttle" should {
    case class TestScenario(testName: String, nino: String, percentage: Int, accountType: AccountTypes.Value)
    s"return $ThrottleApplied" when {

    }
    s"return $ThrottleDoesNotApply" when {
      List(
        TestScenario(s"account type is $PT_ASSIGNED_TO_CURRENT_USER, Invalid nino", "QQ", 1, PT_ASSIGNED_TO_CURRENT_USER),
      )
    }
    "return Left Error" when {
      "throttle applies but auth returns error" in {

      }
    }
  }

  "newPTEnrolment" should {
    "return correct class containing NINO" in {
      throttlingservice.newPTEnrolment("foo") shouldBe Enrolment(s"$hmrcPTKey", Seq(EnrolmentIdentifier("NINO", "foo")), "Activated", None)
    }
  }

}
