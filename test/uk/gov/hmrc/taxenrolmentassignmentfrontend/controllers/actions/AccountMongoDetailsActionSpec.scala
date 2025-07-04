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

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{times, verify, when}
import org.mockito.stubbing.OngoingStubbing
import play.api.Application
import play.api.inject.{Binding, bind}
import play.api.libs.json.{JsString, Json}
import play.api.mvc.Results.Ok
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.AffinityGroup.Individual
import uk.gov.hmrc.auth.core.{Enrolment, Enrolments}
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.CacheMap
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes.{PT_ASSIGNED_TO_CURRENT_USER, SINGLE_ACCOUNT}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.actions.RequestWithUserDetailsFromSessionAndMongo.requestConversion
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.BaseSpec
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.TestData.{CURRENT_USER_EMAIL, PROVIDER_TYPE}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.SessionKeys.{ACCOUNT_TYPE, REDIRECT_URL}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.TEASessionCache

import scala.concurrent.Future

class AccountMongoDetailsActionSpec extends BaseSpec {

  def mockDeleteDataFromCacheWhen: OngoingStubbing[Future[Boolean]] =
    when(mockTeaSessionCache.removeRecord(any()))
      .thenReturn(Future.successful(true))

  def mockDeleteDataFromCacheVerify: Future[Boolean] =
    verify(mockTeaSessionCache, times(1)).removeRecord(any())

  lazy val mockTeaSessionCache: TEASessionCache = mock[TEASessionCache]

  override lazy val overrides: Seq[Binding[TEASessionCache]] = Seq(
    bind[TEASessionCache].toInstance(mockTeaSessionCache)
  )

  override implicit lazy val app: Application = localGuiceApplicationBuilder()
    .build()

  lazy val accountMongoDetailsAction: AccountMongoDetailsAction = app.injector.instanceOf[AccountMongoDetailsAction]

  val nino: Nino = new Generator().nextNino

  "invoke"                                                      should {
    "successfully build enriched request when cache contains both accountType and redirectUrl" in {
      val exampleMongoSessionData           =
        Map(ACCOUNT_TYPE -> Json.toJson(PT_ASSIGNED_TO_CURRENT_USER), REDIRECT_URL -> JsString("foo"))
      val requestWithUserDetailsFromSession = RequestWithUserDetailsFromSession(
        FakeRequest(),
        UserDetailsFromSession(
          "foo",
          PROVIDER_TYPE,
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

      val expectedConversion = RequestWithUserDetailsFromSessionAndMongo(
        requestWithUserDetailsFromSession.request,
        requestWithUserDetailsFromSession.userDetails,
        requestWithUserDetailsFromSession.sessionID,
        AccountDetailsFromMongo(PT_ASSIGNED_TO_CURRENT_USER, "foo", exampleMongoSessionData)(crypto.crypto)
      )

      val function =
        (requestWithUserDetailsFromSessionAndMongo: RequestWithUserDetailsFromSessionAndMongo[
          _
        ]) =>
          Future.successful(
            Ok(requestWithUserDetailsFromSessionAndMongo.toString())
          )

      when(mockTeaSessionCache.fetch()(any[RequestWithUserDetailsFromSession[_]]))
        .thenReturn(Future.successful(Some(CacheMap("id", exampleMongoSessionData))))

      val res = accountMongoDetailsAction.invokeBlock(
        requestWithUserDetailsFromSession,
        function
      )
      contentAsString(res) shouldBe expectedConversion.toString()
    }
    s"Redirect to login" when {
      "the cache is empty" in {
        val requestWithUserDetailsFromSession = RequestWithUserDetailsFromSession(
          FakeRequest(),
          UserDetailsFromSession(
            "foo",
            PROVIDER_TYPE,
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
        val function                          =
          (requestWithUserDetailsFromSessionAndMongo: RequestWithUserDetailsFromSessionAndMongo[
            _
          ]) =>
            Future.successful(
              Ok(requestWithUserDetailsFromSessionAndMongo.toString())
            )

        when(mockTeaSessionCache.fetch()(any[RequestWithUserDetailsFromSession[_]]))
          .thenReturn(Future.successful(None))

        val res      = accountMongoDetailsAction.invokeBlock(
          requestWithUserDetailsFromSession,
          function
        )
        val loginUrl = "http://localhost:9553/bas-gateway/sign-in?continue_url=http%3A%2F%2Flocalhost%3A9232%2F" +
          "personal-account&origin=tax-enrolment-assignment-frontend"

        status(res)           shouldBe SEE_OTHER
        redirectLocation(res) shouldBe Some(loginUrl)
      }
    }
    s"Return $INTERNAL_SERVER_ERROR" when {
      s"the session cache contains the redirectUrl but no the accountType" in {
        val exampleMongoSessionData           = Map(REDIRECT_URL -> JsString("foo"))
        val requestWithUserDetailsFromSession =
          RequestWithUserDetailsFromSession(
            FakeRequest(),
            UserDetailsFromSession(
              "foo",
              PROVIDER_TYPE,
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
          (requestWithUserDetailsFromSessionAndMongo: RequestWithUserDetailsFromSessionAndMongo[
            _
          ]) =>
            Future.successful(
              Ok(requestWithUserDetailsFromSessionAndMongo.toString())
            )

        when(mockTeaSessionCache.fetch()(any[RequestWithUserDetailsFromSession[_]]))
          .thenReturn(Future.successful(Some(CacheMap("id", exampleMongoSessionData))))

        val res = accountMongoDetailsAction.invokeBlock(
          requestWithUserDetailsFromSession,
          function
        )
        status(res)        shouldBe INTERNAL_SERVER_ERROR
        contentAsString(res) should include(messages("enrolmentError.heading"))
      }
      "the session cache contains the account type but not the redirect url" in {
        val exampleMongoSessionData           = Map(ACCOUNT_TYPE -> Json.toJson(PT_ASSIGNED_TO_CURRENT_USER))
        val requestWithUserDetailsFromSession =
          RequestWithUserDetailsFromSession(
            FakeRequest(),
            UserDetailsFromSession(
              "foo",
              PROVIDER_TYPE,
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
          (requestWithUserDetailsFromSessionAndMongo: RequestWithUserDetailsFromSessionAndMongo[
            _
          ]) =>
            Future.successful(
              Ok(requestWithUserDetailsFromSessionAndMongo.toString())
            )

        when(mockTeaSessionCache.fetch()(any[RequestWithUserDetailsFromSession[_]]))
          .thenReturn(Future.successful(Some(CacheMap("id", exampleMongoSessionData))))

        val res = accountMongoDetailsAction.invokeBlock(
          requestWithUserDetailsFromSession,
          function
        )
        status(res)        shouldBe INTERNAL_SERVER_ERROR
        contentAsString(res) should include(messages("enrolmentError.heading"))
      }
    }

    "when reading from cache returns an exception" in {
      val requestWithUserDetailsFromSession = RequestWithUserDetailsFromSession(
        FakeRequest(),
        UserDetailsFromSession(
          "foo",
          PROVIDER_TYPE,
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
      val function                          =
        (requestWithUserDetailsFromSessionAndMongo: RequestWithUserDetailsFromSessionAndMongo[
          _
        ]) =>
          Future.successful(
            Ok(requestWithUserDetailsFromSessionAndMongo.toString())
          )

      when(mockTeaSessionCache.fetch()(any[RequestWithUserDetailsFromSession[_]]))
        .thenReturn(Future.failed(exception = new Exception("uh oh")))

      val res = accountMongoDetailsAction.invokeBlock(
        requestWithUserDetailsFromSession,
        function
      )
      status(res)        shouldBe INTERNAL_SERVER_ERROR
      contentAsString(res) should include(messages("enrolmentError.heading"))

    }
  }
  "RequestWithUserDetailsFromSessionAndMongo.requestConversion" should {
    "convert correctly" in {
      val requestWithUserDetailsFromSession = RequestWithUserDetailsFromSession(
        FakeRequest(),
        UserDetailsFromSession(
          "foo",
          PROVIDER_TYPE,
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
          AccountDetailsFromMongo(SINGLE_ACCOUNT, "redirect", Map())(crypto.crypto)
        )
      ) shouldBe requestWithUserDetailsFromSession
    }
  }
}
