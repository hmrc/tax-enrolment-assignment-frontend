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
import play.api.http.Status.{NO_CONTENT, OK, SEE_OTHER}
import play.api.mvc.Results.Ok
import play.api.mvc._
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, defaultAwaitTimeout, redirectLocation, status}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, UpstreamErrorResponse}
import uk.gov.hmrc.play.bootstrap.binders.RedirectUrl
import uk.gov.hmrc.taxenrolmentassignmentfrontend.config.AppConfig
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.routes
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.BaseSpec
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.TestData.{NINO, userDetailsWithMismatchNino, userDetailsWithPTEnrolment}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.services.EACDService

import scala.concurrent.{ExecutionContext, Future}

class PTMismatchCheckActionSpec extends BaseSpec {

  val mockEacdService: EACDService = mock[EACDService]
  val mockAppConf: AppConfig = mock[AppConfig]

  def harnessToggleTrue[A](
    block: RequestWithUserDetailsFromSession[_] => Future[Result]
  )(implicit request: RequestWithUserDetailsFromSession[A]): Future[Result] = {
    (mockAppConf.ptNinoMismatchToggle _).expects().returning(true)
    lazy val actionProvider = new PTMismatchCheckActionImpl(mockEacdService, mockAppConf)
    actionProvider.invokeBlock(request, block)
  }

  def harnessToggleFalse[A](
    block: RequestWithUserDetailsFromSession[_] => Future[Result]
  )(implicit request: RequestWithUserDetailsFromSession[A]): Future[Result] = {
    (mockAppConf.ptNinoMismatchToggle _).expects().returning(false)
    lazy val actionProvider = new PTMismatchCheckActionImpl(mockEacdService, mockAppConf)
    actionProvider.invokeBlock(request, block)
  }

  "PTMismatchCheckAction" when {
    "a user has a HMRC-PT enrolment which does not match the request NINO" should {
      val requestInnerWithRedirect =
        FakeRequest(GET, "testUrl?redirectUrl=/testRedirect").asInstanceOf[Request[AnyContent]]

      val mismatchRequest = request.copy(userDetails = userDetailsWithMismatchNino, request = requestInnerWithRedirect)

      "delete the mismatched HMRC-PT enrolment and return SEE_OTHER" in {
        (mockEacdService
          .deallocateEnrolment(_: String, _: String)(
            _: HeaderCarrier,
            _: ExecutionContext
          ))
          .expects(*, *, *, *)
          .returning(
            EitherT[Future, UpstreamErrorResponse, HttpResponse](Future.successful(Right(HttpResponse(NO_CONTENT, ""))))
          )

        val block: RequestWithUserDetailsFromSession[_] => Future[Result] =
          userRequest => Future.successful(Ok(s"User Details: ${userRequest.userDetails}"))

        val action = harnessToggleTrue(block)(mismatchRequest)
        status(action) shouldBe SEE_OTHER
        redirectLocation(action) shouldBe Some(
          routes.AccountCheckController.accountCheck(RedirectUrl("/testRedirect")).url
        )
      }
      "not delete the mismatched HMRC-PT enrolment and return OK if there was no redirectUrl in the query string" in {
        val requestInnerWithoutRedirect = FakeRequest().asInstanceOf[Request[AnyContent]]
        val mismatchRequest =
          request.copy(userDetails = userDetailsWithMismatchNino, request = requestInnerWithoutRedirect)

        (mockEacdService
          .deallocateEnrolment(_: String, _: String)(
            _: HeaderCarrier,
            _: ExecutionContext
          ))
          .expects(mismatchRequest.userDetails.groupId, s"HMRC-PT~NINO~${Some(NINO)}", *, *)
          .returning(
            EitherT[Future, UpstreamErrorResponse, HttpResponse](Future.successful(Right(HttpResponse(NO_CONTENT, ""))))
          )

        val block: RequestWithUserDetailsFromSession[_] => Future[Result] =
          userRequest => Future.successful(Ok(s"User Details: ${userRequest.userDetails}"))

        val action = harnessToggleTrue(block)(mismatchRequest)
        status(action) shouldBe OK
      }
      "not delete the mismatched HMRC-PT enrolment and return OK if the toggle is set to false" in {
        (mockEacdService
          .deallocateEnrolment(_: String, _: String)(
            _: HeaderCarrier,
            _: ExecutionContext
          ))
          .expects(*, *, *, *)
          .never()

        val block: RequestWithUserDetailsFromSession[_] => Future[Result] =
          userRequest => Future.successful(Ok(s"User Details: ${userRequest.userDetails}"))

        val action = harnessToggleFalse(block)(mismatchRequest)
        status(action) shouldBe OK
      }
    }
    "a user has a HMRC-PT enrolment which does match the request NINO" should {
      "not make any changes to the existing enrolments and return OK" in {
        (mockEacdService
          .deallocateEnrolment(_: String, _: String)(
            _: HeaderCarrier,
            _: ExecutionContext
          ))
          .expects(*, *, *, *)
          .never()

        val block: RequestWithUserDetailsFromSession[_] => Future[Result] =
          userRequest => Future.successful(Ok(s"User Details: ${userRequest.userDetails}"))

        val action = harnessToggleTrue(block)(request.copy(userDetails = userDetailsWithPTEnrolment))
        status(action) shouldBe OK
      }
    }
    "a user does not have a HMRC-PT enrolment" should {
      "not make any changes to the existing enrolments and return OK" in {
        (mockEacdService
          .deallocateEnrolment(_: String, _: String)(
            _: HeaderCarrier,
            _: ExecutionContext
          ))
          .expects(*, *, *, *)
          .never()

        val block: RequestWithUserDetailsFromSession[_] => Future[Result] =
          userRequest => Future.successful(Ok(s"User Details: ${userRequest.userDetails}"))

        val action = harnessToggleTrue(block)(request)
        status(action) shouldBe OK
      }
    }
  }
}
