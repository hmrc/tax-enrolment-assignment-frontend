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

package uk.gov.hmrc.taxenrolmentassignmentfrontend.services

import play.api.Application
import play.api.inject.bind
import uk.gov.hmrc.auth.core.{Enrolment, EnrolmentIdentifier}
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.config.AppConfig
import uk.gov.hmrc.taxenrolmentassignmentfrontend.connectors.LegacyAuthConnector
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.UnexpectedError
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.BaseSpec
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.enums.EnrolmentEnum.hmrcPTKey

import scala.concurrent.ExecutionContext

class ThrottlingServiceSpec extends BaseSpec {

  lazy val mockLegacyAuthConnector = mock[LegacyAuthConnector]

  override implicit lazy val app: Application = localGuiceApplicationBuilder()
    .overrides(
      bind[LegacyAuthConnector].toInstance(mockLegacyAuthConnector)
    )
    .build()

  lazy val throttlingservice: ThrottlingService = app.injector.instanceOf[ThrottlingService]
  val validNonRealNino: Nino = new Generator().nextNino

  def ninoWithlast2digits(digits: String) = {
    if (digits.length != 2) {
      throw new IllegalArgumentException("digits must be 2 characters exactly")
    }
    val digit1 = digits.head
    val digit2 = digits.reverse.head
    Nino(validNonRealNino.nino.toList.updated(6, digit1).updated(7, digit2).mkString(""))
  }

  "isNinoWithinThrottleThreshold" should {
    case class TestScenario(testName: String, nino: Nino, percentage: Int)

    "return true" when {
      List(
        TestScenario("Nino last 2 digits == percentage (percentage is 1)", ninoWithlast2digits("01"), 1),
        TestScenario("Nino last 2 digits == percentage (percentage is an amount)", ninoWithlast2digits("55"), 55),
        TestScenario("Nino last 2 digits == percentage (percentage is max)", ninoWithlast2digits("99"), 99),
        TestScenario("Nino last 2 digits < percentage (percentage is one above)", ninoWithlast2digits("01"), 2),
        TestScenario("Nino last 2 digits < percentage (percentage is an amount above)", ninoWithlast2digits("01"), 50),
        TestScenario("Nino last 2 digits < percentage (percentage is max amount above)", ninoWithlast2digits("01"), 99),
        TestScenario("Nino last 2 digits == percentage (percentage is 0)", ninoWithlast2digits("00"), 0)
      ).foreach(test =>
        s"${test.testName}" in {
          throttlingservice.isNinoWithinThrottleThreshold(test.nino, test.percentage) shouldBe true
        }
      )
    }
    "return false" when {
      List(
        TestScenario("percentage is > 100", validNonRealNino, 101),
        TestScenario("percentage is 100", validNonRealNino, 100),
        TestScenario("Nino last 2 digits > percentage by 1", ninoWithlast2digits("11"), 10),
        TestScenario("Nino last 2 digits > percentage by an amount", ninoWithlast2digits("56"), 10),
        TestScenario("Nino last 2 digits > percentage by max amount", ninoWithlast2digits("99"), 10),
        TestScenario("percentage < 0 but Nino is correct format", validNonRealNino, -1)
      ).foreach(test =>
        s"${test.testName}" in {
          throttlingservice.isNinoWithinThrottleThreshold(test.nino, test.percentage) shouldBe false
        }
      )
    }
  }
  "shouldAccountTypeBeThrottled" should {
    case class TestScenario(testName: String, nino: Nino, percentage: Int, accountType: AccountTypes.Value)
    "return true" when {
      List(
        TestScenario(
          s"account type is $MULTIPLE_ACCOUNTS, valid Nino below threshold",
          ninoWithlast2digits("05"),
          6,
          MULTIPLE_ACCOUNTS
        ),
        TestScenario(
          s"account type is $SA_ASSIGNED_TO_CURRENT_USER, valid Nino below threshold",
          ninoWithlast2digits("05"),
          6,
          SA_ASSIGNED_TO_CURRENT_USER
        ),
        TestScenario(
          s"account type is $SA_ASSIGNED_TO_OTHER_USER, valid Nino below threshold",
          ninoWithlast2digits("05"),
          6,
          SA_ASSIGNED_TO_OTHER_USER
        ),
        TestScenario(
          s"account type is $MULTIPLE_ACCOUNTS, valid Nino equal to threshold",
          ninoWithlast2digits("05"),
          5,
          MULTIPLE_ACCOUNTS
        ),
        TestScenario(
          s"account type is $SA_ASSIGNED_TO_CURRENT_USER, valid Nino equal to threshold",
          ninoWithlast2digits("05"),
          5,
          SA_ASSIGNED_TO_CURRENT_USER
        ),
        TestScenario(
          s"account type is $SA_ASSIGNED_TO_OTHER_USER, vvalid Nino equal to threshold",
          ninoWithlast2digits("05"),
          5,
          SA_ASSIGNED_TO_OTHER_USER
        )
      ).foreach(test =>
        s"${test.testName}" in {
          throttlingservice.shouldAccountTypeBeThrottled(test.accountType, test.percentage, test.nino) shouldBe true
        }
      )
    }
    "return false" when {
      List(
        TestScenario(
          s"account type is $PT_ASSIGNED_TO_CURRENT_USER, Valid Nino below threshold",
          ninoWithlast2digits("05"),
          6,
          PT_ASSIGNED_TO_CURRENT_USER
        ),
        TestScenario(
          s"account type is $PT_ASSIGNED_TO_OTHER_USER, Valid Nino below threshold",
          ninoWithlast2digits("05"),
          6,
          PT_ASSIGNED_TO_OTHER_USER
        ),
        TestScenario(
          s"account type is $MULTIPLE_ACCOUNTS, valid Nino above threshold",
          ninoWithlast2digits("05"),
          4,
          MULTIPLE_ACCOUNTS
        ),
        TestScenario(
          s"account type is $SA_ASSIGNED_TO_CURRENT_USER, valid Nino above threshold",
          ninoWithlast2digits("05"),
          4,
          SA_ASSIGNED_TO_CURRENT_USER
        ),
        TestScenario(
          s"account type is $SA_ASSIGNED_TO_OTHER_USER, valid Nino above threshold",
          ninoWithlast2digits("05"),
          4,
          SA_ASSIGNED_TO_OTHER_USER
        ),
        TestScenario(
          s"account type is $PT_ASSIGNED_TO_CURRENT_USER, Valid Nino equal threshold",
          ninoWithlast2digits("06"),
          6,
          PT_ASSIGNED_TO_CURRENT_USER
        ),
        TestScenario(
          s"account type is $PT_ASSIGNED_TO_OTHER_USER, threshold is 100",
          ninoWithlast2digits("05"),
          100,
          PT_ASSIGNED_TO_OTHER_USER
        ),
        TestScenario(
          s"account type is $MULTIPLE_ACCOUNTS, threshold is 100",
          ninoWithlast2digits("05"),
          100,
          MULTIPLE_ACCOUNTS
        ),
        TestScenario(
          s"account type is $SA_ASSIGNED_TO_CURRENT_USER, threshold is 100",
          ninoWithlast2digits("05"),
          100,
          SA_ASSIGNED_TO_CURRENT_USER
        ),
        TestScenario(
          s"account type is $SA_ASSIGNED_TO_OTHER_USER, threshold is 100",
          ninoWithlast2digits("05"),
          100,
          SA_ASSIGNED_TO_OTHER_USER
        ),
        TestScenario(
          s"account type is $SINGLE_ACCOUNT, Valid Nino below threshold",
          ninoWithlast2digits("05"),
          6,
          SINGLE_ACCOUNT
        ),
        TestScenario(
          s"account type is $SINGLE_ACCOUNT, Valid Nino equal threshold",
          ninoWithlast2digits("06"),
          6,
          SINGLE_ACCOUNT
        )
      ).foreach(test =>
        s"${test.testName}" in {
          throttlingservice.shouldAccountTypeBeThrottled(test.accountType, test.percentage, test.nino) shouldBe false
        }
      )
    }
  }

  "addPTEnrolmentToEnrolments" should {
    "add new enrolment to existing set of enrolments" in {
      val newEnrolment =
        Enrolment(s"$hmrcPTKey", Seq(EnrolmentIdentifier("NINO", validNonRealNino.nino)), "Activated", None)
      val setOfExistingEnrolments = Set(
        Enrolment("foo", Seq(EnrolmentIdentifier("NINO", "foo")), "Activated", None),
        Enrolment("bar", Seq(EnrolmentIdentifier("NINO", "foo")), "Activated", None)
      )

      throttlingservice.addPTEnrolmentToEnrolments(
        setOfExistingEnrolments,
        validNonRealNino
      ) shouldBe setOfExistingEnrolments + newEnrolment
    }
  }

  "throttle" should {
    case class TestScenario(testName: String, nino: Nino, percentage: Int, accountType: AccountTypes.Value)
    val setOfExistingEnrolments = Set(
      Enrolment("foo", Seq(EnrolmentIdentifier("NINO", "foo")), "Activated", None),
      Enrolment("bar", Seq(EnrolmentIdentifier("NINO", "foo")), "Activated", None)
    )
    val newEnrolment =
      (nino: Nino) => Enrolment(s"$hmrcPTKey", Seq(EnrolmentIdentifier("NINO", nino.nino)), "Activated", None)
    val throttlingservice = (percentageToThrottle: Int) =>
      new ThrottlingService(
        mockLegacyAuthConnector,
        new AppConfig(app.injector.instanceOf[ServicesConfig]) {
          override lazy val percentageOfUsersThrottledToGetFakeEnrolment: Int = percentageToThrottle
        }
      )
    s"return $ThrottleApplied" when {
      List(
        TestScenario(
          s"account type is $MULTIPLE_ACCOUNTS, Valid Nino below threshold auth call succeeds",
          ninoWithlast2digits("55"),
          99,
          MULTIPLE_ACCOUNTS
        ),
        TestScenario(
          s"account type is $SA_ASSIGNED_TO_CURRENT_USER, Valid Nino below threshold auth call succeeds",
          ninoWithlast2digits("55"),
          99,
          SA_ASSIGNED_TO_CURRENT_USER
        ),
        TestScenario(
          s"account type is $SA_ASSIGNED_TO_OTHER_USER, Valid Nino below threshold auth call succeeds",
          ninoWithlast2digits("55"),
          99,
          SA_ASSIGNED_TO_OTHER_USER
        )
      ).foreach(test =>
        s"${test.testName}" in {
          (mockLegacyAuthConnector
            .updateEnrolments(_: Set[Enrolment])(_: ExecutionContext, _: HeaderCarrier))
            .expects(setOfExistingEnrolments + newEnrolment(test.nino), *, *)
            .returning(createInboundResult((): Unit))
            .once()

          val res = throttlingservice(test.percentage)
            .throttle(test.accountType, test.nino, setOfExistingEnrolments)
            .value
            .futureValue
          res shouldBe Right(ThrottleApplied)
        }
      )
    }
    s"return $ThrottleDoesNotApply" when {
      List(
        TestScenario(
          s"account type is $PT_ASSIGNED_TO_CURRENT_USER, Valid Nino below threshold, no auth call",
          ninoWithlast2digits("55"),
          99,
          PT_ASSIGNED_TO_CURRENT_USER
        ),
        TestScenario(
          s"account type is $PT_ASSIGNED_TO_OTHER_USER,  Valid Nino below threshold, no auth call",
          ninoWithlast2digits("55"),
          99,
          PT_ASSIGNED_TO_OTHER_USER
        ),
        TestScenario(
          s"account type is $MULTIPLE_ACCOUNTS, Valid Nino above threshold, no auth call",
          ninoWithlast2digits("55"),
          54,
          MULTIPLE_ACCOUNTS
        ),
        TestScenario(
          s"account type is $SA_ASSIGNED_TO_CURRENT_USER, Valid Nino above threshold, no auth calls",
          ninoWithlast2digits("55"),
          54,
          SA_ASSIGNED_TO_CURRENT_USER
        ),
        TestScenario(
          s"account type is $SA_ASSIGNED_TO_OTHER_USER, Valid Nino above threshold, no auth call",
          ninoWithlast2digits("55"),
          54,
          SA_ASSIGNED_TO_OTHER_USER
        ),
        TestScenario(
          s"account type is $SINGLE_ACCOUNT, Valid Nino below threshold, no auth call",
          ninoWithlast2digits("55"),
          99,
          SINGLE_ACCOUNT
        )
      ).foreach(test =>
        s"${test.testName}" in {
          (mockLegacyAuthConnector
            .updateEnrolments(_: Set[Enrolment])(_: ExecutionContext, _: HeaderCarrier))
            .expects(*, *, *)
            .returning(createInboundResult((): Unit))
            .never()

          val res = throttlingservice(test.percentage)
            .throttle(test.accountType, test.nino, setOfExistingEnrolments)
            .value
            .futureValue
          res shouldBe Right(ThrottleDoesNotApply)
        }
      )
    }
    "return Left Error" when {
      List(
        TestScenario(
          s"account type is $MULTIPLE_ACCOUNTS, Valid Nino below threshold auth call fails",
          validNonRealNino,
          99,
          MULTIPLE_ACCOUNTS
        ),
        TestScenario(
          s"account type is $SA_ASSIGNED_TO_CURRENT_USER, Valid Nino below threshold auth call fails",
          validNonRealNino,
          99,
          SA_ASSIGNED_TO_CURRENT_USER
        ),
        TestScenario(
          s"account type is $SA_ASSIGNED_TO_OTHER_USER, Valid Nino below threshold auth call fails",
          validNonRealNino,
          99,
          SA_ASSIGNED_TO_OTHER_USER
        )
      ).foreach(test =>
        s"${test.testName}" in {
          (mockLegacyAuthConnector
            .updateEnrolments(_: Set[Enrolment])(_: ExecutionContext, _: HeaderCarrier))
            .expects(setOfExistingEnrolments + newEnrolment(test.nino), *, *)
            .returning(createInboundResultError(UnexpectedError))
            .once()

          val res = throttlingservice(test.percentage)
            .throttle(test.accountType, test.nino, setOfExistingEnrolments)
            .value
            .futureValue
          res shouldBe Left(UnexpectedError)
        }
      )
    }
  }

  "newPTEnrolment" should {
    "return correct class containing NINO" in {
      throttlingservice.newPTEnrolment(validNonRealNino) shouldBe Enrolment(
        s"$hmrcPTKey",
        Seq(EnrolmentIdentifier("NINO", validNonRealNino.nino)),
        "Activated",
        None
      )
    }
  }

}
