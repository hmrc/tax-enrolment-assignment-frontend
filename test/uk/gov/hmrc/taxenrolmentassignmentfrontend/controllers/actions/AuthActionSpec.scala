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

import cats.data.EitherT
import org.mockito.ArgumentMatchers.{any, anyString, eq => ameq}
import org.mockito.MockitoSugar.{mock, times, verify, when}
import org.scalatest.Assertion
import play.api.Application
import play.api.inject.{Binding, bind}
import play.api.mvc.{Result, Results}
import play.api.test.FakeRequest
import play.api.test.Helpers.{contentAsString, redirectLocation, status, _}
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.BaseSpec
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.TestData._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.JourneyCacheRepository
import uk.gov.hmrc.taxenrolmentassignmentfrontend.utils.HmrcPTEnrolment

import scala.concurrent.{ExecutionContext, Future}

class AuthActionSpec extends BaseSpec {

  override val mockJourneyCacheRepository: JourneyCacheRepository = mock[JourneyCacheRepository]
  lazy val mockAuthConnector: AuthConnector = mock[AuthConnector]
  lazy val mockHmrcPTEnrolment: HmrcPTEnrolment = mock[HmrcPTEnrolment]

  override lazy val overrides: Seq[Binding[JourneyCacheRepository]] = Seq(
    bind[JourneyCacheRepository].toInstance(mockJourneyCacheRepository)
  )

  override implicit lazy val app: Application = localGuiceApplicationBuilder()
    .overrides(
      bind[AuthConnector].toInstance(mockAuthConnector),
      bind[HmrcPTEnrolment].toInstance(mockHmrcPTEnrolment)
    )
    .build()

  lazy val authAction: AuthAction = app.injector.instanceOf[AuthAction]

  def defaultAsyncBody(
    requestTestCase: RequestWithUserDetailsFromSession[_] => Assertion
  ): RequestWithUserDetailsFromSession[_] => Result = testRequest => {
    requestTestCase(testRequest)
    Results.Ok("Successful")
  }

  "AuthAction" when {
    "the session contains nino, credential and no enrolments" should {
      "return OK and the userDetails" in {
        when(mockAuthConnector.authorise(ameq(predicates), ameq(retrievals))(any[HeaderCarrier], any[ExecutionContext]))
          .thenReturn(Future.successful(retrievalResponse()))

        when(
          mockHmrcPTEnrolment
            .findAndDeleteWrongPTEnrolment(any(), any(), anyString())(any[HeaderCarrier], any[ExecutionContext])
        )
          .thenReturn(EitherT.rightT(()))

        val result: Future[Result] = authAction(
          defaultAsyncBody(_.userDetails shouldBe userDetailsNoEnrolments)
        )(FakeRequest())

        status(result) shouldBe OK
        contentAsString(result) shouldBe "Successful"
        verify(mockHmrcPTEnrolment, times(1))
          .findAndDeleteWrongPTEnrolment(any(), any(), anyString())(any[HeaderCarrier], any[ExecutionContext])
      }
    }

    "the session contains nino, credential and IR-SA enrolments" should {
      "return OK and the userDetails" in {

        when(mockAuthConnector.authorise(ameq(predicates), ameq(retrievals))(any[HeaderCarrier], any[ExecutionContext]))
          .thenReturn(Future.successful(retrievalResponse(enrolments = saEnrolmentOnly)))

        when(
          mockHmrcPTEnrolment
            .findAndDeleteWrongPTEnrolment(any(), any(), anyString())(any[HeaderCarrier], any[ExecutionContext])
        )
          .thenReturn(EitherT.rightT(()))

        val result: Future[Result] = authAction(
          defaultAsyncBody(_.userDetails shouldBe userDetailsWithSAEnrolment)
        )(FakeRequest())

        status(result) shouldBe OK
        contentAsString(result) shouldBe "Successful"
        verify(mockHmrcPTEnrolment, times(1))
          .findAndDeleteWrongPTEnrolment(any(), any(), anyString())(any[HeaderCarrier], any[ExecutionContext])
      }
    }

    "the session contains nino, credential and PT enrolments" should {
      "return OK and the userDetails" in {

        when(mockAuthConnector.authorise(ameq(predicates), ameq(retrievals))(any[HeaderCarrier], any[ExecutionContext]))
          .thenReturn(Future.successful(retrievalResponse(enrolments = ptEnrolmentOnly)))

        when(
          mockHmrcPTEnrolment
            .findAndDeleteWrongPTEnrolment(any(), any(), anyString())(any[HeaderCarrier], any[ExecutionContext])
        )
          .thenReturn(EitherT.rightT(()))

        val result: Future[Result] = authAction(
          defaultAsyncBody(_.userDetails shouldBe userDetailsWithPTEnrolment)
        )(FakeRequest())

        status(result) shouldBe OK
        contentAsString(result) shouldBe "Successful"
        verify(mockHmrcPTEnrolment, times(1))
          .findAndDeleteWrongPTEnrolment(any(), any(), anyString())(any[HeaderCarrier], any[ExecutionContext])
      }
    }

    "the session contains nino, credential and IR-SA and PT enrolments" should {
      "return OK and the userDetails" in {
        when(mockAuthConnector.authorise(ameq(predicates), ameq(retrievals))(any[HeaderCarrier], any[ExecutionContext]))
          .thenReturn(Future.successful(retrievalResponse(enrolments = saAndptEnrolments)))

        when(
          mockHmrcPTEnrolment
            .findAndDeleteWrongPTEnrolment(any(), any(), anyString())(any[HeaderCarrier], any[ExecutionContext])
        )
          .thenReturn(EitherT.rightT(()))

        val result: Future[Result] = authAction(
          defaultAsyncBody(
            _.userDetails shouldBe userDetailsWithPTAndSAEnrolment
          )
        )(FakeRequest())

        status(result) shouldBe OK
        contentAsString(result) shouldBe "Successful"
        verify(mockHmrcPTEnrolment, times(1))
          .findAndDeleteWrongPTEnrolment(any(), any(), anyString())(any[HeaderCarrier], any[ExecutionContext])
      }
    }

    "the session contains nino, but no credential" should {
      "return Unauthorised" in {
        when(mockAuthConnector.authorise(ameq(predicates), ameq(retrievals))(any[HeaderCarrier], any[ExecutionContext]))
          .thenReturn(Future.successful(retrievalResponse(optCredentials = None)))

        when(
          mockHmrcPTEnrolment
            .findAndDeleteWrongPTEnrolment(any(), any(), anyString())(any[HeaderCarrier], any[ExecutionContext])
        )
          .thenReturn(EitherT.rightT(()))

        val result: Future[Result] = authAction(
          defaultAsyncBody(_.userDetails shouldBe userDetailsNoEnrolments)
        )(FakeRequest())

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some("/protect-tax-info/unauthorised")
        verify(mockHmrcPTEnrolment, times(0))
          .findAndDeleteWrongPTEnrolment(any(), any(), anyString())(any[HeaderCarrier], any[ExecutionContext])
      }
    }

    "the session contains no nino, but credential" should {
      "return Unauthorised" in {
        when(mockAuthConnector.authorise(ameq(predicates), ameq(retrievals))(any[HeaderCarrier], any[ExecutionContext]))
          .thenReturn(Future.successful(retrievalResponse(optNino = None)))

        when(
          mockHmrcPTEnrolment
            .findAndDeleteWrongPTEnrolment(any(), any(), anyString())(any[HeaderCarrier], any[ExecutionContext])
        )
          .thenReturn(EitherT.rightT(()))

        val result: Future[Result] = authAction(
          defaultAsyncBody(_.userDetails shouldBe userDetailsNoEnrolments)
        )(FakeRequest())

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some("/protect-tax-info/unauthorised")
        verify(mockHmrcPTEnrolment, times(0))
          .findAndDeleteWrongPTEnrolment(any(), any(), anyString())(any[HeaderCarrier], any[ExecutionContext])
      }
    }

    "the session contains no nino or credential" should {
      "return Unauthorised" in {
        when(mockAuthConnector.authorise(ameq(predicates), ameq(retrievals))(any[HeaderCarrier], any[ExecutionContext]))
          .thenReturn(
            Future.successful(
              retrievalResponse(optNino = None, optCredentials = None)
            )
          )

        when(
          mockHmrcPTEnrolment
            .findAndDeleteWrongPTEnrolment(any(), any(), anyString())(any[HeaderCarrier], any[ExecutionContext])
        )
          .thenReturn(EitherT.rightT(()))

        val result: Future[Result] = authAction(
          defaultAsyncBody(_.userDetails shouldBe userDetailsNoEnrolments)
        )(FakeRequest())

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some("/protect-tax-info/unauthorised")
        verify(mockHmrcPTEnrolment, times(0))
          .findAndDeleteWrongPTEnrolment(any(), any(), anyString())(any[HeaderCarrier], any[ExecutionContext])
      }
    }

    "the session cannot be found" should {
      "redirect to the login page" in {
        when(mockAuthConnector.authorise(ameq(predicates), ameq(retrievals))(any[HeaderCarrier], any[ExecutionContext]))
          .thenReturn(Future.failed(SessionRecordNotFound("FAILED")))

        when(
          mockHmrcPTEnrolment
            .findAndDeleteWrongPTEnrolment(any(), any(), anyString())(any[HeaderCarrier], any[ExecutionContext])
        )
          .thenReturn(EitherT.rightT(()))

        val result: Future[Result] = authAction(
          defaultAsyncBody(_.userDetails shouldBe userDetailsNoEnrolments)
        )(FakeRequest())

        val loginUrl = "http://localhost:9553/bas-gateway/sign-in?continue_url=http%3A%2F%2Flocalhost%3A9232%2F" +
          "personal-account&origin=tax-enrolment-assignment-frontend"

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(loginUrl)
        verify(mockHmrcPTEnrolment, times(0))
          .findAndDeleteWrongPTEnrolment(any(), any(), anyString())(any[HeaderCarrier], any[ExecutionContext])
      }
    }

    "the session is from an unsupported auth provider" should {
      "return Unauthorised" in {
        when(mockAuthConnector.authorise(ameq(predicates), ameq(retrievals))(any[HeaderCarrier], any[ExecutionContext]))
          .thenReturn(Future.failed(UnsupportedAuthProvider("FAILED")))

        when(
          mockHmrcPTEnrolment
            .findAndDeleteWrongPTEnrolment(any(), any(), anyString())(any[HeaderCarrier], any[ExecutionContext])
        )
          .thenReturn(EitherT.rightT(()))

        val result: Future[Result] = authAction(
          defaultAsyncBody(_.userDetails shouldBe userDetailsNoEnrolments)
        )(FakeRequest())

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some("/protect-tax-info/unauthorised")
        verify(mockHmrcPTEnrolment, times(0))
          .findAndDeleteWrongPTEnrolment(any(), any(), anyString())(any[HeaderCarrier], any[ExecutionContext])
      }
    }

    "the session is at an insufficient confidence level" should {
      "return Unauthorised" in {
        when(mockAuthConnector.authorise(ameq(predicates), ameq(retrievals))(any[HeaderCarrier], any[ExecutionContext]))
          .thenReturn(Future.failed(InsufficientConfidenceLevel("FAILED")))

        when(
          mockHmrcPTEnrolment
            .findAndDeleteWrongPTEnrolment(any(), any(), anyString())(any[HeaderCarrier], any[ExecutionContext])
        )
          .thenReturn(EitherT.rightT(()))

        val result: Future[Result] = authAction(
          defaultAsyncBody(_.userDetails shouldBe userDetailsNoEnrolments)
        )(FakeRequest())

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some("/protect-tax-info/unauthorised")
        verify(mockHmrcPTEnrolment, times(0))
          .findAndDeleteWrongPTEnrolment(any(), any(), anyString())(any[HeaderCarrier], any[ExecutionContext])
      }
    }
  }

  "The call checking invalid enrolments fails" should {
    "return an error page" in {
      when(mockAuthConnector.authorise(ameq(predicates), ameq(retrievals))(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future.successful(retrievalResponse(enrolments = ptEnrolmentOnly)))

      when(
        mockHmrcPTEnrolment
          .findAndDeleteWrongPTEnrolment(any(), any(), anyString())(any[HeaderCarrier], any[ExecutionContext])
      )
        .thenReturn(EitherT.leftT(UpstreamErrorResponse("error", INTERNAL_SERVER_ERROR)))

      val result: Future[Result] = authAction(
        defaultAsyncBody(_.userDetails shouldBe userDetailsWithPTEnrolment)
      )(FakeRequest())

      status(result) shouldBe INTERNAL_SERVER_ERROR
      verify(mockHmrcPTEnrolment, times(1))
        .findAndDeleteWrongPTEnrolment(any(), any(), anyString())(any[HeaderCarrier], any[ExecutionContext])
    }
  }
}
