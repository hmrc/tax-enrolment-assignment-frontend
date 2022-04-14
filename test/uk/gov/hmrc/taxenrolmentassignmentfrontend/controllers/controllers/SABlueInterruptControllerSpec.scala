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
import play.api.mvc.AnyContent
import play.api.test.Helpers.{status, _}
import uk.gov.hmrc.auth.core.Enrolments
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve.{Credentials, Retrieval, ~}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes.SA_ASSIGNED_TO_OTHER_USER
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.SABlueInterruptController
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.auth.RequestWithUserDetails
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.helpers.TestData._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.helpers.TestFixture
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.InvalidUserType
import uk.gov.hmrc.taxenrolmentassignmentfrontend.views.html.SABlueInterrupt

import scala.concurrent.{ExecutionContext, Future}

class SABlueInterruptControllerSpec extends TestFixture {

  val blueSAView: SABlueInterrupt =
    inject[SABlueInterrupt]

  val controller =
    new SABlueInterruptController(
      mockAuthAction,
      mcc,
      mockMultipleAccountsOrchestrator,
      logger,
      blueSAView,
      errorView
    )

  "view" when {
    "a user has SA on another account" should {
      "render the SABlueInterrupt page" in {
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
          .checkValidAccountTypeRedirectUrlInCache(_: List[AccountTypes.Value])(
            _: RequestWithUserDetails[AnyContent],
            _: HeaderCarrier,
            _: ExecutionContext
          ))
          .expects(List(SA_ASSIGNED_TO_OTHER_USER), *, *, *)
          .returning(createInboundResult(SA_ASSIGNED_TO_OTHER_USER))

        val result = controller
          .view()
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(result) shouldBe OK
        val page = Jsoup.parse(contentAsString(result))
        page.title shouldBe "selfAssessmentInterrupt.title"
        page
          .select("h1")
          .text() shouldBe "selfAssessmentInterrupt.heading"
        page
          .select("p")
          .text() shouldBe "selfAssessmentInterrupt.paragraph1 " ++ "selfAssessmentInterrupt.paragraph2"
      }
    }

    "the user does not have an account type of SA_ASSIGNED_TO_OTHER_USER" should {
      "redirect to account check" in {
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
          .checkValidAccountTypeRedirectUrlInCache(_: List[AccountTypes.Value])(
            _: RequestWithUserDetails[AnyContent],
            _: HeaderCarrier,
            _: ExecutionContext
          ))
          .expects(List(SA_ASSIGNED_TO_OTHER_USER), *, *, *)
          .returning(createInboundResultError(InvalidUserType(Some("/test"))))

        val result = controller
          .view()
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(
          "/tax-enrolment-assignment-frontend?redirectUrl=%2Ftest"
        )
      }
    }

    s"the cache no redirectUrl" should {
      "render the error page" in {
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
          .checkValidAccountTypeRedirectUrlInCache(_: List[AccountTypes.Value])(
            _: RequestWithUserDetails[AnyContent],
            _: HeaderCarrier,
            _: ExecutionContext
          ))
          .expects(List(SA_ASSIGNED_TO_OTHER_USER), *, *, *)
          .returning(createInboundResultError(InvalidUserType(None)))

        val res = controller
          .view()
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(res) shouldBe OK
        contentAsString(res) should include("enrolmentError.title")
      }
    }
  }

  "post" when {
    "a user has SA on another account" should {
      "redirect to keepAccessToSA" in {
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
          .checkValidAccountTypeRedirectUrlInCache(_: List[AccountTypes.Value])(
            _: RequestWithUserDetails[AnyContent],
            _: HeaderCarrier,
            _: ExecutionContext
          ))
          .expects(List(SA_ASSIGNED_TO_OTHER_USER), *, *, *)
          .returning(createInboundResult(SA_ASSIGNED_TO_OTHER_USER))

        val result = controller
          .continue()
          .apply(buildFakePOSTRequestWithSessionId(Map.empty))

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(
          "/tax-enrolment-assignment-frontend/enrol-pt/other-user-id-has-sa/keep-access-to-sa-from-pta"
        )
      }
    }

    "the user does not have an account type of SA_ASSIGNED_TO_OTHER_USER" should {
      "redirect to account check" in {
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
          .checkValidAccountTypeRedirectUrlInCache(_: List[AccountTypes.Value])(
            _: RequestWithUserDetails[AnyContent],
            _: HeaderCarrier,
            _: ExecutionContext
          ))
          .expects(List(SA_ASSIGNED_TO_OTHER_USER), *, *, *)
          .returning(createInboundResultError(InvalidUserType(Some("/test"))))

        val result = controller
          .continue()
          .apply(buildFakePOSTRequestWithSessionId(Map.empty))

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(
          "/tax-enrolment-assignment-frontend?redirectUrl=%2Ftest"
        )
      }
    }

    s"the cache no redirectUrl" should {
      "render the error page" in {
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
          .checkValidAccountTypeRedirectUrlInCache(_: List[AccountTypes.Value])(
            _: RequestWithUserDetails[AnyContent],
            _: HeaderCarrier,
            _: ExecutionContext
          ))
          .expects(List(SA_ASSIGNED_TO_OTHER_USER), *, *, *)
          .returning(createInboundResultError(InvalidUserType(None)))

        val result = controller
          .continue()
          .apply(buildFakePOSTRequestWithSessionId(Map.empty))

        status(result) shouldBe OK
        contentAsString(result) should include("enrolmentError.title")
      }
    }
  }
}
