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

package uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers

import uk.gov.hmrc.auth.core.{AffinityGroup, Enrolments}
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve.{Credentials, Retrieval, ~}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.TestData.{NINO, accountDetails, buildFakeRequestWithSessionId, predicates, randomAccountType, retrievalResponse, retrievals, saEnrolmentOnly}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.{TestFixture, ThrottleHelperSpec, UrlPaths}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.views.html.EnrolledForPTPage
import play.api.http.Status.OK
import play.api.test.Helpers._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes.SA_ASSIGNED_TO_CURRENT_USER
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.actions.RequestWithUserDetailsFromSessionAndMongo

import scala.concurrent.{ExecutionContext, Future}

class EnrolledForPTWithSAControllerSpec extends TestFixture with ThrottleHelperSpec {

  val view: EnrolledForPTPage =
    app.injector.instanceOf[EnrolledForPTPage]

  val controller = new EnrolledForPTWithSAController(
    mockAuthAction,
    mockAccountMongoDetailsAction,
    mockThrottleAction,
    mcc,
    mockMultipleAccountsOrchestrator,
    logger,
    view,
    errorHandler
  )

  "view" when {
    specificThrottleTests(controller.view)
    "the user has multiple accounts, is signed in with one with SA then" should {
      "see the Enrolled to PT with SA page" in {
        (mockAuthConnector
          .authorise(
            _: Predicate,
            _: Retrieval[
              ((Option[String] ~ Option[Credentials]) ~ Enrolments) ~ Option[
                String
              ] ~ Option[AffinityGroup]
            ]
          )(_: HeaderCarrier, _: ExecutionContext))
          .expects(predicates, retrievals, *, *)
          .returning(
            Future.successful(retrievalResponse(enrolments = saEnrolmentOnly))
          )

        (mockMultipleAccountsOrchestrator
          .getDetailsForEnrolledPT(
            _: RequestWithUserDetailsFromSessionAndMongo[_],
            _: HeaderCarrier,
            _: ExecutionContext
          ))
          .expects(*, *, *)
          .returning(createInboundResult(accountDetails))

        mockGetDataFromCacheForActionSuccess(randomAccountType)
        mockAccountShouldNotBeThrottled(randomAccountType, NINO, saEnrolmentOnly.enrolments)

        val result = controller.view
          .apply(buildFakeRequestWithSessionId("",""))

        status(result) shouldBe OK

        contentAsString(result).contains("enrolledForPT.title")

        contentAsString(result).contains("enrolledForPT.paragraphSA")

      }
    }
  }

  "continue" when {
    specificThrottleTests(controller.continue)

    "the user has multiple accounts, is signed in with one with SA then" should {
      s"redirect to ${UrlPaths.returnUrl}" in {
        (mockAuthConnector
          .authorise(
            _: Predicate,
            _: Retrieval[
              ((Option[String] ~ Option[Credentials]) ~ Enrolments) ~ Option[
                String
              ] ~ Option[AffinityGroup]
            ]
          )(_: HeaderCarrier, _: ExecutionContext))
          .expects(predicates, retrievals, *, *)
          .returning(Future.successful(retrievalResponse(enrolments = saEnrolmentOnly)))


        mockGetDataFromCacheForActionSuccess(SA_ASSIGNED_TO_CURRENT_USER, UrlPaths.returnUrl)

        mockAccountShouldNotBeThrottled(SA_ASSIGNED_TO_CURRENT_USER, NINO, saEnrolmentOnly.enrolments)


        val result = controller
          .continue()
          .apply(buildFakeRequestWithSessionId("",""))

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(
          UrlPaths.returnUrl
        )
      }
    }
  }


}
