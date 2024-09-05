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

package uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.actions

import org.mockito.ArgumentMatchers.{any, anyString}
import org.mockito.MockitoSugar.{times, verify, when}
import org.mockito.stubbing.ScalaOngoingStubbing
import play.api.Application
import play.api.inject.{Binding, bind}
import play.api.mvc.AnyContent
import play.api.mvc.Results.Ok
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.AffinityGroup.Individual
import uk.gov.hmrc.auth.core.{Enrolment, Enrolments}
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes.{PT_ASSIGNED_TO_CURRENT_USER, SINGLE_OR_MULTIPLE_ACCOUNTS}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.actions.RequestWithUserDetailsFromSessionAndMongo.requestConversion
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.BaseSpec
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.TestData.CURRENT_USER_EMAIL
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.UserAnswers
import uk.gov.hmrc.taxenrolmentassignmentfrontend.pages.{AccountTypePage, RedirectUrlPage}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.JourneyCacheRepository

import scala.concurrent.Future

class AccountMongoDetailsActionSpec extends BaseSpec {

  def mockDeleteDataFromCacheWhen: ScalaOngoingStubbing[Future[Boolean]] =
    when(mockJourneyCacheRepository.clear(anyString(), anyString())) thenReturn Future.successful(true)

  def mockDeleteDataFromCacheVerify: Future[Boolean] =
    verify(mockJourneyCacheRepository, times(1)).clear(anyString(), anyString())

  override lazy val overrides: Seq[Binding[JourneyCacheRepository]] = Seq(
    bind[JourneyCacheRepository].toInstance(mockJourneyCacheRepository)
  )

  override implicit lazy val app: Application = localGuiceApplicationBuilder()
    .build()

  lazy val accountMongoDetailsAction: AccountMongoDetailsAction = app.injector.instanceOf[AccountMongoDetailsAction]

  val nino: Nino = new Generator().nextNino

  "invoke" should {
    "return updated request when orchestrator returns success Some for both account type and redirect url" in {
      val mockUserAnswers = UserAnswers("id", generateNino.nino)
        .setOrException(AccountTypePage, PT_ASSIGNED_TO_CURRENT_USER.toString)
        .setOrException(RedirectUrlPage, "foo")

      val requestWithUserDetailsFromSession: RequestWithUserDetailsFromSession[AnyContent] =
        RequestWithUserDetailsFromSession(
          FakeRequest(),
          UserDetailsFromSession(
            "foo",
            nino,
            "wizz",
            Some(CURRENT_USER_EMAIL),
            Individual,
            Enrolments(Set.empty[Enrolment]),
            hasPTEnrolment = true,
            hasSAEnrolment = true
          ),
          "foo"
        )

      val expectedConversion = requestWithGivenMongoDataAndUserAnswers(
        RequestWithUserDetailsFromSessionAndMongo(
          requestWithUserDetailsFromSession.request,
          requestWithUserDetailsFromSession.userDetails,
          requestWithUserDetailsFromSession.sessionID,
          AccountDetailsFromMongo(PT_ASSIGNED_TO_CURRENT_USER, "foo", None, None, None, None)
        ),
        mockUserAnswers
      )

      val function =
        (requestWithUserDetailsFromSessionAndMongo: DataRequest[
          _
        ]) =>
          Future.successful(
            Ok(requestWithUserDetailsFromSessionAndMongo.toString())
          )

      when(mockJourneyCacheRepository.get(any(), any()))
        .thenReturn(Future.successful(Some(mockUserAnswers)))

      val res = accountMongoDetailsAction.invokeBlock(
        requestWithGivenSessionDataAndUserAnswers(requestWithUserDetailsFromSession, mockUserAnswers),
        function
      )
      contentAsString(res) shouldBe expectedConversion.toString()
    }
    s"Redirect to login" when {
      "the cache is empty" in {
        val requestWithUserDetailsFromSession: RequestWithUserDetailsFromSession[AnyContent] =
          RequestWithUserDetailsFromSession(
            FakeRequest(),
            UserDetailsFromSession(
              "foo",
              nino,
              "wizz",
              Some(CURRENT_USER_EMAIL),
              Individual,
              Enrolments(Set.empty[Enrolment]),
              hasPTEnrolment = true,
              hasSAEnrolment = true
            ),
            "foo"
          )
        val function =
          (requestWithUserDetailsFromSessionAndMongo: DataRequest[
            _
          ]) =>
            Future.successful(
              Ok(requestWithUserDetailsFromSessionAndMongo.toString())
            )

        when(mockJourneyCacheRepository.get(any(), any()))
          .thenReturn(Future.successful(None))

        val res = accountMongoDetailsAction.invokeBlock(
          requestWithGivenSessionData(requestWithUserDetailsFromSession),
          function
        )
        val loginUrl = "http://localhost:9553/bas-gateway/sign-in?continue_url=http%3A%2F%2Flocalhost%3A9232%2F" +
          "personal-account&origin=tax-enrolment-assignment-frontend"

        status(res) shouldBe SEE_OTHER
        redirectLocation(res) shouldBe Some(loginUrl)
      }
    }
    s"Return $INTERNAL_SERVER_ERROR" when {
      s"the session cache contains the redirectUrl but no the accountType" in {
        val mockUserAnswers = UserAnswers("id", generateNino.nino)
          .setOrException(RedirectUrlPage, "foo")
        val requestWithUserDetailsFromSession: RequestWithUserDetailsFromSession[AnyContent] =
          RequestWithUserDetailsFromSession(
            FakeRequest(),
            UserDetailsFromSession(
              "foo",
              nino,
              "wizz",
              Some(CURRENT_USER_EMAIL),
              Individual,
              Enrolments(Set.empty[Enrolment]),
              hasPTEnrolment = true,
              hasSAEnrolment = true
            ),
            "foo"
          )

        val function =
          (requestWithUserDetailsFromSessionAndMongo: DataRequest[
            _
          ]) =>
            Future.successful(
              Ok(requestWithUserDetailsFromSessionAndMongo.toString())
            )

        when(mockJourneyCacheRepository.get(any(), any()))
          .thenReturn(Future.successful(Some(mockUserAnswers)))

        val res = accountMongoDetailsAction.invokeBlock(
          requestWithGivenSessionDataAndUserAnswers(requestWithUserDetailsFromSession, mockUserAnswers),
          function
        )
        status(res) shouldBe INTERNAL_SERVER_ERROR
        contentAsString(res) should include(messages("enrolmentError.heading"))
      }
      "the session cache contains the account type but not the redirect url" in {
        val mockUserAnswers = UserAnswers("id", generateNino.nino)
          .setOrException(AccountTypePage, PT_ASSIGNED_TO_CURRENT_USER.toString)
        val requestWithUserDetailsFromSession: RequestWithUserDetailsFromSession[AnyContent] =
          RequestWithUserDetailsFromSession(
            FakeRequest(),
            UserDetailsFromSession(
              "foo",
              nino,
              "wizz",
              Some(CURRENT_USER_EMAIL),
              Individual,
              Enrolments(Set.empty[Enrolment]),
              hasPTEnrolment = true,
              hasSAEnrolment = true
            ),
            "foo"
          )

        val function =
          (requestWithUserDetailsFromSessionAndMongo: DataRequest[
            _
          ]) =>
            Future.successful(
              Ok(requestWithUserDetailsFromSessionAndMongo.toString())
            )

        when(mockJourneyCacheRepository.get(any(), any()))
          .thenReturn(Future.successful(Some(mockUserAnswers)))

        val res = accountMongoDetailsAction.invokeBlock(
          requestWithGivenSessionDataAndUserAnswers(requestWithUserDetailsFromSession, mockUserAnswers),
          function
        )
        status(res) shouldBe INTERNAL_SERVER_ERROR
        contentAsString(res) should include(messages("enrolmentError.heading"))
      }
    }
  }
  "RequestWithUserDetailsFromSessionAndMongo.requestConversion" should {
    "convert correctly" in {
      val requestWithUserDetailsFromSession = RequestWithUserDetailsFromSession(
        FakeRequest(),
        UserDetailsFromSession(
          "foo",
          nino,
          "wizz",
          Some(CURRENT_USER_EMAIL),
          Individual,
          Enrolments(Set.empty[Enrolment]),
          hasPTEnrolment = true,
          hasSAEnrolment = true
        ),
        "foo"
      )

      requestConversion(
        RequestWithUserDetailsFromSessionAndMongo(
          requestWithUserDetailsFromSession.request,
          requestWithUserDetailsFromSession.userDetails,
          requestWithUserDetailsFromSession.sessionID,
          AccountDetailsFromMongo(SINGLE_OR_MULTIPLE_ACCOUNTS, "redirect", None, None, None, None)
        )
      ) shouldBe requestWithUserDetailsFromSession
    }
  }
}
