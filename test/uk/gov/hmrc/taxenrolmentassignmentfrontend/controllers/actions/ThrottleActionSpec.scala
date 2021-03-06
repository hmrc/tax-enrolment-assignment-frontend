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

package uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.actions

import play.api.http.Status.INTERNAL_SERVER_ERROR
import play.api.mvc.Results.Ok
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.AffinityGroup.Individual
import uk.gov.hmrc.auth.core.{Enrolment, Enrolments}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes.PT_ASSIGNED_TO_CURRENT_USER
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.UnexpectedError
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.TestFixture
import uk.gov.hmrc.taxenrolmentassignmentfrontend.services.{ThrottleApplied, ThrottleDoesNotApply}

import scala.concurrent.{ExecutionContext, Future}

class ThrottleActionSpec extends TestFixture {

  val action =
    new ThrottleAction(mockThrottlingService, testBodyParser, errorHandler, logger)
  val exampleRequestSessionAndMongo: RequestWithUserDetailsFromSessionAndMongo[_] = RequestWithUserDetailsFromSessionAndMongo(
    FakeRequest(),
    UserDetailsFromSession(
      "123",
      "nino",
      "gID",
      Individual,
      Enrolments(Set(Enrolment("foo"))),
      false,
      false
    ),
    "sesh",
    AccountDetailsFromMongo(PT_ASSIGNED_TO_CURRENT_USER, "redirectURL", Map())
  )
  val exampleRequestSession: RequestWithUserDetailsFromSession[_] = {
    RequestWithUserDetailsFromSession(exampleRequestSessionAndMongo.request, exampleRequestSessionAndMongo.userDetails, exampleRequestSessionAndMongo.sessionID)
  }
  val exampleControllerFunction =
    (r: RequestWithUserDetailsFromSessionAndMongo[_]) =>
      Future.successful(Ok("got through"))


  "invokeBlock" should {
    s"return result of function if throttling service returns $ThrottleDoesNotApply" in {
      (mockThrottlingService
        .throttle(_: AccountTypes.Value, _: String, _: Set[Enrolment])(
          _: ExecutionContext,
          _: HeaderCarrier
        ))
        .expects(
          exampleRequestSessionAndMongo.accountDetailsFromMongo.accountType,
          exampleRequestSessionAndMongo.userDetails.nino,
          exampleRequestSessionAndMongo.userDetails.enrolments.enrolments,
          *,
          *
        )
        .returning(createInboundResult(ThrottleDoesNotApply))

      val res = action.invokeBlock(exampleRequestSessionAndMongo, exampleControllerFunction)
      contentAsString(res) shouldBe "got through"
      status(res) shouldBe OK
    }
    s"redirect to redirect URL of user if throttling service returns $ThrottleApplied" in {
      (mockThrottlingService
        .throttle(_: AccountTypes.Value, _: String, _: Set[Enrolment])(
          _: ExecutionContext,
          _: HeaderCarrier
        ))
        .expects(
          exampleRequestSessionAndMongo.accountDetailsFromMongo.accountType,
          exampleRequestSessionAndMongo.userDetails.nino,
          exampleRequestSessionAndMongo.userDetails.enrolments.enrolments,
          *,
          *
        )
        .returning(createInboundResult(ThrottleApplied))

      val res = action.invokeBlock(exampleRequestSessionAndMongo, exampleControllerFunction)

      status(res) shouldBe SEE_OTHER
      redirectLocation(res).get shouldBe exampleRequestSessionAndMongo.accountDetailsFromMongo.redirectUrl
    }
    s"return $INTERNAL_SERVER_ERROR if throttling service returns Error" in {
      (mockThrottlingService
        .throttle(_: AccountTypes.Value, _: String, _: Set[Enrolment])(
          _: ExecutionContext,
          _: HeaderCarrier
        ))
        .expects(
          exampleRequestSessionAndMongo.accountDetailsFromMongo.accountType,
          exampleRequestSessionAndMongo.userDetails.nino,
          exampleRequestSessionAndMongo.userDetails.enrolments.enrolments,
          *,
          *
        )
        .returning(createInboundResultError(UnexpectedError))

      val res = action.invokeBlock(exampleRequestSessionAndMongo, exampleControllerFunction)

      status(res) shouldBe INTERNAL_SERVER_ERROR
    }
  }

  "throttle" should {
    s"return None if throttle does not apply" in {
      (mockThrottlingService
        .throttle(_: AccountTypes.Value, _: String, _: Set[Enrolment])(
          _: ExecutionContext,
          _: HeaderCarrier
        ))
        .expects(
          PT_ASSIGNED_TO_CURRENT_USER,
          exampleRequestSession.userDetails.nino,
          exampleRequestSession.userDetails.enrolments.enrolments,
          *,
          *
        )
        .returning(createInboundResult(ThrottleDoesNotApply))

      val res = action.throttle(PT_ASSIGNED_TO_CURRENT_USER,"redirectURL")(implicitly, implicitly, exampleRequestSession)
      await(res) shouldBe None
    }
    s"return result that redirects to users redirect URL of user if throttling service returns $ThrottleApplied" in {
      (mockThrottlingService
        .throttle(_: AccountTypes.Value, _: String, _: Set[Enrolment])(
          _: ExecutionContext,
          _: HeaderCarrier
        ))
        .expects(
          PT_ASSIGNED_TO_CURRENT_USER,
          exampleRequestSession.userDetails.nino,
          exampleRequestSession.userDetails.enrolments.enrolments,
          *,
          *
        )
        .returning(createInboundResult(ThrottleApplied))

      val res = action.throttle(PT_ASSIGNED_TO_CURRENT_USER,"redirectURL")(implicitly, implicitly, exampleRequestSession)

      status(res.map(_.get)) shouldBe SEE_OTHER
      redirectLocation(res.map(_.get)).get shouldBe "redirectURL"
    }
    s"return $INTERNAL_SERVER_ERROR if throttling service returns Error" in {
      (mockThrottlingService
        .throttle(_: AccountTypes.Value, _: String, _: Set[Enrolment])(
          _: ExecutionContext,
          _: HeaderCarrier
        ))
        .expects(
          PT_ASSIGNED_TO_CURRENT_USER,
          exampleRequestSession.userDetails.nino,
          exampleRequestSession.userDetails.enrolments.enrolments,
          *,
          *
        )
        .returning(createInboundResultError(UnexpectedError))

      val res = action.throttle(PT_ASSIGNED_TO_CURRENT_USER,"redirectURL")(implicitly, implicitly, exampleRequestSession)

      status(res.map(_.get)) shouldBe INTERNAL_SERVER_ERROR
    }
  }
}
