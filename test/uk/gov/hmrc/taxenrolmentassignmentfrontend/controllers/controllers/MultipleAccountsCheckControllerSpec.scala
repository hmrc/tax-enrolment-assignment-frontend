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

package uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.controllers

import play.api.test.Helpers.{status, _}
import uk.gov.hmrc.auth.core.Enrolments
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve.{Credentials, Retrieval, ~}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.helpers.TestData._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.helpers.TestFixture
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.{MultipleAccountsCheckController, testOnly}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.UnexpectedResponseFromEACD

import scala.concurrent.{ExecutionContext, Future}

class MultipleAccountsCheckControllerSpec extends TestFixture {

  val controller =     new MultipleAccountsCheckController(mockAuthAction,mcc, mockEACDConnector, UCView)

  "enrolmentCheck" when {
    "the credentialId exists for a given enrolment key" should {
      "redirect to the return url" in {
        (mockAuthConnector
          .authorise(
            _: Predicate,
            _: Retrieval[(Option[String] ~ Option[Credentials]) ~ Enrolments]
          )(_: HeaderCarrier, _: ExecutionContext))
          .expects(predicates, retrievals, *, *)
          .returning(Future.successful(retrievalResponse()))
        (mockEACDConnector
          .getUsersWithPTEnrolment(_: String)(
            _: ExecutionContext,
            _: HeaderCarrier
          ))
          .expects(NINO, *, *)
          .returning(createInboundResult(Some(UsersAssignedEnrolment1)))

        val result = controller
          .multipleAccountsCheck(testOnly.routes.TestOnlyController.successfulCall.url)
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(
          "/tax-enrolment-assignment-frontend/test-only/successful"
        )
      }
    }

    "user is signed in but PT enrolment is on another account" should {
      s"return $OK and another account holds PT underConstruction page" in {
        (mockAuthConnector
          .authorise(
            _: Predicate,
            _: Retrieval[(Option[String] ~ Option[Credentials]) ~ Enrolments]
          )(_: HeaderCarrier, _: ExecutionContext))
          .expects(predicates, retrievals, *, *)
          .returning(Future.successful(retrievalResponse()))
        (mockEACDConnector
          .getUsersWithPTEnrolment(_: String)(
            _: ExecutionContext,
            _: HeaderCarrier
          ))
          .expects(NINO, *, *)
          .returning(createInboundResult(Some(UsersAssignedEnrolment2)))

        val result = controller
          .multipleAccountsCheck(testOnly.routes.TestOnlyController.successfulCall.url)
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(result) shouldBe OK
        contentAsString(result) shouldBe UCView(true)(fakeRequest, stubMessages(), appConfig).toString

      }
    }

    "a no credentials exists in ES for a given enrolment key" should {
      s"return $OK and no account holds PT underConstruction page" in {
        (mockAuthConnector
          .authorise(
            _: Predicate,
            _: Retrieval[(Option[String] ~ Option[Credentials]) ~ Enrolments]
          )(_: HeaderCarrier, _: ExecutionContext))
          .expects(predicates, retrievals, *, *)
          .returning(Future.successful(retrievalResponse()))
        (mockEACDConnector
          .getUsersWithPTEnrolment(_: String)(
            _: ExecutionContext,
            _: HeaderCarrier
          ))
          .expects(NINO, *, *)
          .returning(createInboundResult(None))

        val result = controller
          .multipleAccountsCheck(testOnly.routes.TestOnlyController.successfulCall.url)
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(result) shouldBe OK
        contentAsString(result) shouldBe UCView(false)(fakeRequest, stubMessages(), appConfig).toString

      }
    }
    "an unexpected response is received from EACD" should {
      "return InternalServerError" in {
        (mockAuthConnector
          .authorise(
            _: Predicate,
            _: Retrieval[(Option[String] ~ Option[Credentials]) ~ Enrolments]
          )(_: HeaderCarrier, _: ExecutionContext))
          .expects(predicates, retrievals, *, *)
          .returning(Future.successful(retrievalResponse()))
        (mockEACDConnector
          .getUsersWithPTEnrolment(_: String)(
            _: ExecutionContext,
            _: HeaderCarrier
          ))
          .expects(NINO, *, *)
          .returning(createInboundResultError(UnexpectedResponseFromEACD))

        val result = controller
          .multipleAccountsCheck(testOnly.routes.TestOnlyController.successfulCall.url)
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(result) shouldBe INTERNAL_SERVER_ERROR
      }
    }
  }

}
