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

import org.jsoup.Jsoup
import play.api.http.Status.{INTERNAL_SERVER_ERROR, OK}
import play.api.mvc.AnyContent
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.Enrolments
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve.{Credentials, Retrieval, ~}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.auth.RequestWithUserDetails
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.helpers.TestData._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.helpers.TestFixture
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.messages.LandingPageMessages
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.{
  LandingPageController,
  testOnly
}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.{
  InvalidUserType,
  UnexpectedResponseFromUsersGroupSearch
}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.views.html.LandingPage

import scala.concurrent.{ExecutionContext, Future}

class LandingPageControllerSpec extends TestFixture {

  val landingView: LandingPage = app.injector.instanceOf[LandingPage]

  val controller = new LandingPageController(
    mockAuthAction,
    mcc,
    mockMultipleAccountsOrchestrator,
    mockTeaSessionCache,
    logger,
    landingView
  )

  "view" when {
    "the user has multiple accounts and is signed in with one with SA" should {
      "render the landing page" in {
        (mockAuthConnector
          .authorise(
            _: Predicate,
            _: Retrieval[
              ((Option[String] ~ Option[Credentials]) ~ Enrolments) ~ Option[
                String
              ]
            ]
          )(_: HeaderCarrier, _: ExecutionContext))
          .expects(predicates, retrievals, *, *)
          .returning(
            Future.successful(retrievalResponse(enrolments = saEnrolmentOnly))
          )

        (mockMultipleAccountsOrchestrator
          .getDetailsForLandingPage(
            _: RequestWithUserDetails[AnyContent],
            _: HeaderCarrier,
            _: ExecutionContext
          ))
          .expects(*, *, *)
          .returning(createInboundResult(accountDetails))

        val result = controller.view
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(result) shouldBe OK
        Jsoup
          .parse(contentAsString(result))
          .title() shouldBe "landingPage.title"
      }
    }

    "the user has multiple accounts and none have SA" should {
      "render the landing page" in {
        (mockAuthConnector
          .authorise(
            _: Predicate,
            _: Retrieval[
              ((Option[String] ~ Option[Credentials]) ~ Enrolments) ~ Option[
                String
              ]
            ]
          )(_: HeaderCarrier, _: ExecutionContext))
          .expects(predicates, retrievals, *, *)
          .returning(Future.successful(retrievalResponse()))

        (mockMultipleAccountsOrchestrator
          .getDetailsForLandingPage(
            _: RequestWithUserDetails[AnyContent],
            _: HeaderCarrier,
            _: ExecutionContext
          ))
          .expects(*, *, *)
          .returning(createInboundResult(accountDetails))

        val result = controller.view
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(result) shouldBe OK
        Jsoup
          .parse(contentAsString(result))
          .title() shouldBe "landingPage.title"
      }
    }

    "the user is not a  multiple accounts usertype and has redirectUrl stored in session" should {
      "redirect to accountCheck" in {
        (mockAuthConnector
          .authorise(
            _: Predicate,
            _: Retrieval[
              ((Option[String] ~ Option[Credentials]) ~ Enrolments) ~ Option[
                String
              ]
            ]
          )(_: HeaderCarrier, _: ExecutionContext))
          .expects(predicates, retrievals, *, *)
          .returning(Future.successful(retrievalResponse()))

        (mockMultipleAccountsOrchestrator
          .getDetailsForLandingPage(
            _: RequestWithUserDetails[AnyContent],
            _: HeaderCarrier,
            _: ExecutionContext
          ))
          .expects(*, *, *)
          .returning(
            createInboundResultError(
              InvalidUserType(
                Some(testOnly.routes.TestOnlyController.successfulCall.url)
              )
            )
          )

        val result = controller.view
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(
          "/tax-enrolment-assignment-frontend/no-pt-enrolment?redirectUrl=%2Ftax-enrolment-assignment-frontend%2Ftest-only%2Fsuccessful"
        )
      }
    }

    "the user is not a  multiple accounts usertype and has no redirectUrl stored in session" should {
      "return INTERNAL_SERVER_ERROR" in {
        (mockAuthConnector
          .authorise(
            _: Predicate,
            _: Retrieval[
              ((Option[String] ~ Option[Credentials]) ~ Enrolments) ~ Option[
                String
              ]
            ]
          )(_: HeaderCarrier, _: ExecutionContext))
          .expects(predicates, retrievals, *, *)
          .returning(Future.successful(retrievalResponse()))

        (mockMultipleAccountsOrchestrator
          .getDetailsForLandingPage(
            _: RequestWithUserDetails[AnyContent],
            _: HeaderCarrier,
            _: ExecutionContext
          ))
          .expects(*, *, *)
          .returning(createInboundResultError(InvalidUserType(None)))

        val result = controller.view
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(result) shouldBe INTERNAL_SERVER_ERROR
      }
    }

    "the call to users-group-search fails" should {
      "return INTERNAL_SERVER_ERROR" in {
        (mockAuthConnector
          .authorise(
            _: Predicate,
            _: Retrieval[
              ((Option[String] ~ Option[Credentials]) ~ Enrolments) ~ Option[
                String
              ]
            ]
          )(_: HeaderCarrier, _: ExecutionContext))
          .expects(predicates, retrievals, *, *)
          .returning(Future.successful(retrievalResponse()))

        (mockMultipleAccountsOrchestrator
          .getDetailsForLandingPage(
            _: RequestWithUserDetails[AnyContent],
            _: HeaderCarrier,
            _: ExecutionContext
          ))
          .expects(*, *, *)
          .returning(
            createInboundResultError(UnexpectedResponseFromUsersGroupSearch)
          )

        val result = controller.view
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(result) shouldBe INTERNAL_SERVER_ERROR
      }
    }
  }
}
