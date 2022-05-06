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

import play.api.libs.json.Format
import play.api.mvc.Results.Ok
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.{Enrolment, Enrolments}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes.{PT_ASSIGNED_TO_CURRENT_USER, SINGLE_ACCOUNT}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.actions.RequestWithUserDetailsFromSessionAndMongo.requestConversion
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.routes
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.TestFixture

import scala.concurrent.Future

class AccountMongoDetailsActionSpec extends TestFixture {
  val accountMongoDetailsAction: AccountMongoDetailsAction = new AccountMongoDetailsAction(mockAccountCheckOrchestrator, testBodyParser, errorHandler)

  "invoke" should {
    "return updated request when orchestrator returns success Some for both account type and redirect url" in {
      val requestWithUserDetailsFromSession = RequestWithUserDetailsFromSession(
        FakeRequest(), UserDetailsFromSession("foo", "bar", "wizz", Enrolments(Set.empty[Enrolment]), true, true), "foo")

      val expectedConversion = RequestWithUserDetailsFromSessionAndMongo(
        requestWithUserDetailsFromSession.request,
        requestWithUserDetailsFromSession.userDetails,
        requestWithUserDetailsFromSession.sessionID,
        AccountDetailsFromMongo(PT_ASSIGNED_TO_CURRENT_USER, "foo"))

      val function = (requestWithUserDetailsFromSessionAndMongo: RequestWithUserDetailsFromSessionAndMongo[_]) => Future.successful(Ok(requestWithUserDetailsFromSessionAndMongo.toString()))

      (mockAccountCheckOrchestrator
        .getAccountTypeFromCache(_: RequestWithUserDetailsFromSession[_], _: Format[AccountTypes.Value]))
        .expects(requestWithUserDetailsFromSession, *)
        .returning(Future.successful(Some(PT_ASSIGNED_TO_CURRENT_USER)))
      (mockAccountCheckOrchestrator
        .getRedirectUrlFromCache(_: RequestWithUserDetailsFromSession[_]))
        .expects(requestWithUserDetailsFromSession)
        .returning(Future.successful(Some("foo")))

      val res = accountMongoDetailsAction.invokeBlock(requestWithUserDetailsFromSession, function)
      contentAsString(res) shouldBe expectedConversion.toString()
    }
    s"Return $INTERNAL_SERVER_ERROR" when {
      s"orchestrator returns success None for Account type but Some redirect url" in {
        val requestWithUserDetailsFromSession = RequestWithUserDetailsFromSession(
          FakeRequest(), UserDetailsFromSession("foo", "bar", "wizz", Enrolments(Set.empty[Enrolment]), true, true), "foo")

        val function = (requestWithUserDetailsFromSessionAndMongo: RequestWithUserDetailsFromSessionAndMongo[_]) => Future.successful(Ok(requestWithUserDetailsFromSessionAndMongo.toString()))

        (mockAccountCheckOrchestrator
          .getAccountTypeFromCache(_: RequestWithUserDetailsFromSession[_], _: Format[AccountTypes.Value]))
          .expects(requestWithUserDetailsFromSession, *)
          .returning(Future.successful(None))
        (mockAccountCheckOrchestrator
          .getRedirectUrlFromCache(_: RequestWithUserDetailsFromSession[_]))
          .expects(requestWithUserDetailsFromSession)
          .returning(Future.successful(Some("redirectUrl")))

        val res = accountMongoDetailsAction.invokeBlock(requestWithUserDetailsFromSession, function)
        status(res) shouldBe INTERNAL_SERVER_ERROR
        contentAsString(res) should include("enrolmentError.title")
      }
      "orchestrator returns success Some for Account type but None redirect url" in {
        val requestWithUserDetailsFromSession = RequestWithUserDetailsFromSession(
          FakeRequest(), UserDetailsFromSession("foo", "bar", "wizz", Enrolments(Set.empty[Enrolment]), true, true), "foo")

        val function = (requestWithUserDetailsFromSessionAndMongo: RequestWithUserDetailsFromSessionAndMongo[_]) => Future.successful(Ok(requestWithUserDetailsFromSessionAndMongo.toString()))

        (mockAccountCheckOrchestrator
          .getAccountTypeFromCache(_: RequestWithUserDetailsFromSession[_], _: Format[AccountTypes.Value]))
          .expects(requestWithUserDetailsFromSession, *)
          .returning(Future.successful(Some(PT_ASSIGNED_TO_CURRENT_USER)))
        (mockAccountCheckOrchestrator
          .getRedirectUrlFromCache(_: RequestWithUserDetailsFromSession[_]))
          .expects(requestWithUserDetailsFromSession)
          .returning(Future.successful(None))

        val res = accountMongoDetailsAction.invokeBlock(requestWithUserDetailsFromSession, function)
        status(res) shouldBe INTERNAL_SERVER_ERROR
        contentAsString(res) should include("enrolmentError.title")
      }
    }
      "orchestrator get account type fails" in {
        val requestWithUserDetailsFromSession = RequestWithUserDetailsFromSession(
          FakeRequest(), UserDetailsFromSession("foo", "bar", "wizz", Enrolments(Set.empty[Enrolment]), true, true), "foo")
        val function = (requestWithUserDetailsFromSessionAndMongo: RequestWithUserDetailsFromSessionAndMongo[_]) => Future.successful(Ok(requestWithUserDetailsFromSessionAndMongo.toString()))
        (mockAccountCheckOrchestrator
          .getRedirectUrlFromCache(_: RequestWithUserDetailsFromSession[_]))
          .expects(requestWithUserDetailsFromSession)
          .returning(Future.successful(None))
        (mockAccountCheckOrchestrator
          .getAccountTypeFromCache(_: RequestWithUserDetailsFromSession[_], _: Format[AccountTypes.Value]))
          .expects(requestWithUserDetailsFromSession, *)
          .returning(Future.failed(exception = new Exception("uh oh")))

        val res = accountMongoDetailsAction.invokeBlock(requestWithUserDetailsFromSession, function)
        status(res) shouldBe INTERNAL_SERVER_ERROR
        contentAsString(res) should include("enrolmentError.title")
      }

      "orchestrator get redirect url fails" in {
        val requestWithUserDetailsFromSession = RequestWithUserDetailsFromSession(
          FakeRequest(), UserDetailsFromSession("foo", "bar", "wizz", Enrolments(Set.empty[Enrolment]), true, true), "foo")
        val function = (requestWithUserDetailsFromSessionAndMongo: RequestWithUserDetailsFromSessionAndMongo[_]) => Future.successful(Ok(requestWithUserDetailsFromSessionAndMongo.toString()))
        (mockAccountCheckOrchestrator
          .getRedirectUrlFromCache(_: RequestWithUserDetailsFromSession[_]))
          .expects(requestWithUserDetailsFromSession)
          .returning(Future.failed(exception = new Exception("uh oh")))

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
        AccountDetailsFromMongo(SINGLE_ACCOUNT, "redirect"))) shouldBe requestWithUserDetailsFromSession
    }
  }
}