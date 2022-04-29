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

import play.api.mvc.Results.Ok
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.{Enrolment, Enrolments}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes.{PT_ASSIGNED_TO_CURRENT_USER, SINGLE_ACCOUNT}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.actions.RequestWithUserDetailsFromSessionAndMongo.requestConversion
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.UnexpectedErrorWhenGettingUserType
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.TestFixture

import scala.concurrent.{ExecutionContext, Future}

class AccountMongoDetailsActionSpec extends TestFixture {
  val accountMongoDetailsAction: AccountMongoDetailsAction = new AccountMongoDetailsAction(mockAccountCheckOrchestrator, testBodyParser, errorHandler)

  "invoke" should {
    "return updated request when orchestrator returns success" in {
      val requestWithUserDetailsFromSession = RequestWithUserDetailsFromSession(
        FakeRequest(), UserDetailsFromSession("foo","bar","wizz", Enrolments(Set.empty[Enrolment]), true, true), "foo")

      val expectedConversion = RequestWithUserDetailsFromSessionAndMongo(
        requestWithUserDetailsFromSession.request,
        requestWithUserDetailsFromSession.userDetails,
        requestWithUserDetailsFromSession.sessionID,
        AccountDetailsFromMongo(PT_ASSIGNED_TO_CURRENT_USER))

        val function = (requestWithUserDetailsFromSessionAndMongo: RequestWithUserDetailsFromSessionAndMongo[_]) => Future.successful(Ok(requestWithUserDetailsFromSessionAndMongo.toString()))

      (mockAccountCheckOrchestrator
        .getAccountType(_: ExecutionContext, _: HeaderCarrier, _: RequestWithUserDetailsFromSession[_]))
        .expects(*,*,requestWithUserDetailsFromSession)
        .returning(createInboundResult(PT_ASSIGNED_TO_CURRENT_USER))

      val res = accountMongoDetailsAction.invokeBlock(requestWithUserDetailsFromSession, function)
      contentAsString(res) shouldBe expectedConversion.toString()
    }
    "return Error request when orchestrator returns fail" in {
      val requestWithUserDetailsFromSession = RequestWithUserDetailsFromSession(
        FakeRequest(), UserDetailsFromSession("foo","bar","wizz", Enrolments(Set.empty[Enrolment]), true, true), "foo")
      val function = (requestWithUserDetailsFromSessionAndMongo: RequestWithUserDetailsFromSessionAndMongo[_]) => Future.successful(Ok(requestWithUserDetailsFromSessionAndMongo.toString()))

      (mockAccountCheckOrchestrator
        .getAccountType(_: ExecutionContext, _: HeaderCarrier, _: RequestWithUserDetailsFromSession[_]))
        .expects(*,*,requestWithUserDetailsFromSession)
        .returning(createInboundResultError(UnexpectedErrorWhenGettingUserType))

      val res = accountMongoDetailsAction.invokeBlock(requestWithUserDetailsFromSession, function)

      status(res) shouldBe INTERNAL_SERVER_ERROR
      contentAsString(res) should include("enrolmentError.title")
    }
  }
  "RequestWithUserDetailsFromSessionAndMongo.requestConversion" should {
    "convert correctly" in {
      val requestWithUserDetailsFromSession = RequestWithUserDetailsFromSession(
        FakeRequest(), UserDetailsFromSession("foo","bar","wizz", Enrolments(Set.empty[Enrolment]), true, true), "foo")

      requestConversion(RequestWithUserDetailsFromSessionAndMongo(
        requestWithUserDetailsFromSession.request,
        requestWithUserDetailsFromSession.userDetails,
        requestWithUserDetailsFromSession.sessionID,
        AccountDetailsFromMongo(SINGLE_ACCOUNT))) shouldBe requestWithUserDetailsFromSession
    }
  }
}