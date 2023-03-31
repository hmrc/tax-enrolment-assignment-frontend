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

import org.scalamock.handlers.CallHandler1
import org.scalatest.Assertion
import play.api.Application
import play.api.inject.bind
import play.api.mvc.{Result, Results}
import play.api.test.FakeRequest
import play.api.test.Helpers.{contentAsString, redirectLocation, status, _}
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve.{Credentials, Retrieval, ~}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.BaseSpec
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.TestData._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.TEASessionCache

import scala.concurrent.{ExecutionContext, Future}

class AuthActionSpec extends BaseSpec {

  def mockDeleteDataFromCache: CallHandler1[RequestWithUserDetailsFromSession[_], Future[Boolean]] =
    (mockTeaSessionCache
      .removeRecord(_: RequestWithUserDetailsFromSession[_]))
      .expects(*)
      .returning(Future.successful(true))
      .once()

  lazy val mockTeaSessionCache = mock[TEASessionCache]
  lazy val mockAuthConnector = mock[AuthConnector]

  override lazy val overrides = Seq(
    bind[TEASessionCache].toInstance(mockTeaSessionCache)
  )

  override implicit lazy val app: Application = localGuiceApplicationBuilder()
    .overrides(
      bind[AuthConnector].toInstance(mockAuthConnector)
    )
    .build()

  lazy val authAction = app.injector.instanceOf[AuthAction]

  def defaultAsyncBody(
    requestTestCase: RequestWithUserDetailsFromSession[_] => Assertion
  ): RequestWithUserDetailsFromSession[_] => Result = testRequest => {
    requestTestCase(testRequest)
    Results.Ok("Successful")
  }

  "AuthAction" when {
    "the session contains nino, credential and no enrolments" should {
      "return OK and the userDetails" in {
        (
          mockAuthConnector
            .authorise(
              _: Predicate,
              _: Retrieval[
                ((Option[String] ~ Option[Credentials]) ~ Enrolments) ~ Option[
                  String
                ] ~ Option[AffinityGroup] ~ Option[String]
              ]
            )(
              _: HeaderCarrier,
              _: ExecutionContext
            )
          )
          .expects(predicates, retrievals, *, *)
          .returning(Future.successful(retrievalResponse()))

        val result: Future[Result] = authAction(
          defaultAsyncBody(_.userDetails shouldBe userDetailsNoEnrolments)
        )(FakeRequest())

        status(result) shouldBe OK
        contentAsString(result) shouldBe "Successful"
      }
    }

    "the session contains nino, credential and IR-SA enrolments" should {
      "return OK and the userDetails" in {
        (
          mockAuthConnector
            .authorise(
              _: Predicate,
              _: Retrieval[
                ((Option[String] ~ Option[Credentials]) ~ Enrolments) ~ Option[
                  String
                ] ~ Option[AffinityGroup] ~ Option[String]
              ]
            )(
              _: HeaderCarrier,
              _: ExecutionContext
            )
          )
          .expects(predicates, retrievals, *, *)
          .returning(
            Future.successful(retrievalResponse(enrolments = saEnrolmentOnly))
          )

        val result: Future[Result] = authAction(
          defaultAsyncBody(_.userDetails shouldBe userDetailsWithSAEnrolment)
        )(FakeRequest())

        status(result) shouldBe OK
        contentAsString(result) shouldBe "Successful"
      }
    }

    "the session contains nino, credential and PT enrolments" should {
      "return OK and the userDetails" in {
        (
          mockAuthConnector
            .authorise(
              _: Predicate,
              _: Retrieval[
                ((Option[String] ~ Option[Credentials]) ~ Enrolments) ~ Option[
                  String
                ] ~ Option[AffinityGroup] ~ Option[String]
              ]
            )(
              _: HeaderCarrier,
              _: ExecutionContext
            )
          )
          .expects(predicates, retrievals, *, *)
          .returning(
            Future.successful(retrievalResponse(enrolments = ptEnrolmentOnly))
          )

        val result: Future[Result] = authAction(
          defaultAsyncBody(_.userDetails shouldBe userDetailsWithPTEnrolment)
        )(FakeRequest())

        status(result) shouldBe OK
        contentAsString(result) shouldBe "Successful"
      }
    }

    "the session contains nino, credential and IR-SA and PT enrolments" should {
      "return OK and the userDetails" in {
        (
          mockAuthConnector
            .authorise(
              _: Predicate,
              _: Retrieval[
                ((Option[String] ~ Option[Credentials]) ~ Enrolments) ~ Option[
                  String
                ] ~ Option[AffinityGroup] ~ Option[String]
              ]
            )(
              _: HeaderCarrier,
              _: ExecutionContext
            )
          )
          .expects(predicates, retrievals, *, *)
          .returning(
            Future.successful(retrievalResponse(enrolments = saAndptEnrolments))
          )

        val result: Future[Result] = authAction(
          defaultAsyncBody(
            _.userDetails shouldBe userDetailsWithPTAndSAEnrolment
          )
        )(FakeRequest())

        status(result) shouldBe OK
        contentAsString(result) shouldBe "Successful"
      }
    }

    "the session contains nino, but no credential" should {
      "return Unauthorised" in {
        (
          mockAuthConnector
            .authorise(
              _: Predicate,
              _: Retrieval[
                ((Option[String] ~ Option[Credentials]) ~ Enrolments) ~ Option[
                  String
                ] ~ Option[AffinityGroup] ~ Option[String]
              ]
            )(
              _: HeaderCarrier,
              _: ExecutionContext
            )
          )
          .expects(predicates, retrievals, *, *)
          .returning(
            Future.successful(retrievalResponse(optCredentials = None))
          )

        val result: Future[Result] = authAction(
          defaultAsyncBody(_.userDetails shouldBe userDetailsNoEnrolments)
        )(FakeRequest())

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some("/protect-tax-info/unauthorised")
      }
    }

    "the session contains no nino, but credential" should {
      "return Unauthorised" in {
        (
          mockAuthConnector
            .authorise(
              _: Predicate,
              _: Retrieval[
                ((Option[String] ~ Option[Credentials]) ~ Enrolments) ~ Option[
                  String
                ] ~ Option[AffinityGroup] ~ Option[String]
              ]
            )(
              _: HeaderCarrier,
              _: ExecutionContext
            )
          )
          .expects(predicates, retrievals, *, *)
          .returning(Future.successful(retrievalResponse(optNino = None)))

        val result: Future[Result] = authAction(
          defaultAsyncBody(_.userDetails shouldBe userDetailsNoEnrolments)
        )(FakeRequest())

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some("/protect-tax-info/unauthorised")
      }
    }

    "the session contains no nino or credential" should {
      "return Unauthorised" in {
        (
          mockAuthConnector
            .authorise(
              _: Predicate,
              _: Retrieval[
                ((Option[String] ~ Option[Credentials]) ~ Enrolments) ~ Option[
                  String
                ] ~ Option[AffinityGroup] ~ Option[String]
              ]
            )(
              _: HeaderCarrier,
              _: ExecutionContext
            )
          )
          .expects(predicates, retrievals, *, *)
          .returning(
            Future.successful(
              retrievalResponse(optNino = None, optCredentials = None)
            )
          )

        val result: Future[Result] = authAction(
          defaultAsyncBody(_.userDetails shouldBe userDetailsNoEnrolments)
        )(FakeRequest())

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some("/protect-tax-info/unauthorised")
      }
    }

    "the session cannot be found" should {
      "redirect to the login page" in {
        (
          mockAuthConnector
            .authorise(
              _: Predicate,
              _: Retrieval[
                ((Option[String] ~ Option[Credentials]) ~ Enrolments) ~ Option[
                  String
                ] ~ Option[AffinityGroup] ~ Option[String]
              ]
            )(
              _: HeaderCarrier,
              _: ExecutionContext
            )
          )
          .expects(predicates, retrievals, *, *)
          .returning(Future.failed(SessionRecordNotFound("FAILED")))

        val result: Future[Result] = authAction(
          defaultAsyncBody(_.userDetails shouldBe userDetailsNoEnrolments)
        )(FakeRequest())

        val loginUrl = "http://localhost:9553/bas-gateway/sign-in?continue_url=http%3A%2F%2Flocalhost%3A9232%2F" +
          "personal-account&origin=tax-enrolment-assignment-frontend"

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(loginUrl)
      }
    }

    "the session is from an unsupported auth provider" should {
      "return Unauthorised" in {
        (
          mockAuthConnector
            .authorise(
              _: Predicate,
              _: Retrieval[
                ((Option[String] ~ Option[Credentials]) ~ Enrolments) ~ Option[
                  String
                ] ~ Option[AffinityGroup] ~ Option[String]
              ]
            )(
              _: HeaderCarrier,
              _: ExecutionContext
            )
          )
          .expects(predicates, retrievals, *, *)
          .returning(Future.failed(UnsupportedAuthProvider("FAILED")))

        val result: Future[Result] = authAction(
          defaultAsyncBody(_.userDetails shouldBe userDetailsNoEnrolments)
        )(FakeRequest())

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some("/protect-tax-info/unauthorised")
      }
    }

    "the session is at an insufficient confidence level" should {
      "return Unauthorised" in {
        (
          mockAuthConnector
            .authorise(
              _: Predicate,
              _: Retrieval[
                ((Option[String] ~ Option[Credentials]) ~ Enrolments) ~ Option[
                  String
                ] ~ Option[AffinityGroup] ~ Option[String]
              ]
            )(
              _: HeaderCarrier,
              _: ExecutionContext
            )
          )
          .expects(predicates, retrievals, *, *)
          .returning(Future.failed(InsufficientConfidenceLevel("FAILED")))

        val result: Future[Result] = authAction(
          defaultAsyncBody(_.userDetails shouldBe userDetailsNoEnrolments)
        )(FakeRequest())

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some("/protect-tax-info/unauthorised")
      }
    }
  }

}
