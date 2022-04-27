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

import play.api.libs.json.Format
import play.api.mvc.AnyContent
import play.api.test.Helpers.{status, _}
import uk.gov.hmrc.auth.core.Enrolments
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve.{Credentials, Retrieval, ~}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes.SA_ASSIGNED_TO_OTHER_USER
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.actions.RequestWithUserDetailsFromSession
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.{InvalidUserType, NoSAEnrolmentWhenOneExpected}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.TestData._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.{TestFixture, UrlPaths}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.views.html.{ReportSuspiciousID, SignInWithSAAccount}

import scala.concurrent.{ExecutionContext, Future}

class SignInAgainPageControllerSpec extends TestFixture {
  val view: SignInWithSAAccount = app.injector.instanceOf[SignInWithSAAccount]
  val signOutController = new SignOutController(
    mockAuthAction,
    mcc,
    testAppConfig,
    mockTeaSessionCache
  )
  lazy val reportSuspiciousIDController = new ReportSuspiciousIDController(
    mockAuthAction,
    mockTeaSessionCache,
    mockMultipleAccountsOrchestrator,
    mcc,
    reportSuspiciousIDPage,
    logger,
    errorHandler
  )

  lazy val reportSuspiciousIDPage: ReportSuspiciousID =
    inject[ReportSuspiciousID]

  val controller =
    new SignInWithSAAccountController(
      mockAuthAction,
      mcc,
      mockMultipleAccountsOrchestrator,
      view,
      mockTeaSessionCache,
      logger,
      errorHandler
    )

  "view" when {
    "a user has SA on another account" should {
      "render the signInWithSAAccount page" in {
        (mockAuthConnector
          .authorise(
            _: Predicate,
            _: Retrieval[
              Option[String] ~ Option[Credentials] ~ Enrolments ~ Option[String]
            ]
          )(_: HeaderCarrier, _: ExecutionContext))
          .expects(predicates, retrievals, *, *)
          .returning(Future.successful(retrievalResponse()))

        (mockMultipleAccountsOrchestrator
          .checkValidAccountTypeRedirectUrlInCache(_: List[AccountTypes.Value])(
            _: RequestWithUserDetailsFromSession[AnyContent],
            _: HeaderCarrier,
            _: ExecutionContext
          ))
          .expects(List(SA_ASSIGNED_TO_OTHER_USER), *, *, *)
          .returning(createInboundResult(SA_ASSIGNED_TO_OTHER_USER))

        (mockMultipleAccountsOrchestrator
          .getSACredentialDetails(
            _: RequestWithUserDetailsFromSession[AnyContent],
            _: HeaderCarrier,
            _: ExecutionContext
          ))
          .expects(*, *, *)
          .returning(createInboundResult(accountDetails))

        val result = controller
          .view()
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(result) shouldBe OK
        contentAsString(result) should include("signInAgain.title")
      }
    }

    s"the user does not have an account type of $SA_ASSIGNED_TO_OTHER_USER" should {
      s"redirect to ${UrlPaths.accountCheckPath}" in {
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
            _: RequestWithUserDetailsFromSession[AnyContent],
            _: HeaderCarrier,
            _: ExecutionContext
          ))
          .expects(List(SA_ASSIGNED_TO_OTHER_USER), *, *, *)
          .returning(
            createInboundResultError(InvalidUserType(Some(UrlPaths.returnUrl)))
          )

        val result = controller.view
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(UrlPaths.accountCheckPath)
      }
    }

    "the current user has a no SA enrolment on other account but session says it is other account" should {
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
            _: RequestWithUserDetailsFromSession[AnyContent],
            _: HeaderCarrier,
            _: ExecutionContext
          ))
          .expects(List(SA_ASSIGNED_TO_OTHER_USER), *, *, *)
          .returning(createInboundResult(SA_ASSIGNED_TO_OTHER_USER))

        (mockMultipleAccountsOrchestrator
          .getSACredentialDetails(
            _: RequestWithUserDetailsFromSession[AnyContent],
            _: HeaderCarrier,
            _: ExecutionContext
          ))
          .expects(*, *, *)
          .returning(createInboundResultError(NoSAEnrolmentWhenOneExpected))

        val res = controller
          .view()
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(res) shouldBe INTERNAL_SERVER_ERROR
        contentAsString(res) should include("enrolmentError.title")
      }
    }

    s"the cache no redirectUrl" should {
      "return InternalServerError" in {
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
            _: RequestWithUserDetailsFromSession[AnyContent],
            _: HeaderCarrier,
            _: ExecutionContext
          ))
          .expects(List(SA_ASSIGNED_TO_OTHER_USER), *, *, *)
          .returning(createInboundResultError(InvalidUserType(None)))

        val result = controller.view
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(result) shouldBe INTERNAL_SERVER_ERROR
        contentAsString(result) should include("enrolmentError.title")
      }
    }
  }
  "continue" when {
    "the user has a redirectUrl in cache" should {
      s"redirect to ${UrlPaths.logoutPath}" in {
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

        (mockTeaSessionCache
          .getEntry(_: String)(
            _: RequestWithUserDetailsFromSession[AnyContent],
            _: Format[String]
          ))
          .expects("redirectURL", *, *)
          .returning(Future.successful(Some(UrlPaths.returnUrl)))

        val res = controller
          .continue()
          .apply(buildFakeRequestWithSessionId("POST", "Not Used"))

        status(res) shouldBe SEE_OTHER
        redirectLocation(res) shouldBe
          Some(UrlPaths.logoutPath)
      }
    }

    "the user has not got a redirect url in session" should {
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

        (mockTeaSessionCache
          .getEntry(_: String)(
            _: RequestWithUserDetailsFromSession[AnyContent],
            _: Format[String]
          ))
          .expects("redirectURL", *, *)
          .returning(Future.successful(None))

        val res = controller
          .continue()
          .apply(buildFakeRequestWithSessionId("POST", "Not Used"))

        status(res) shouldBe INTERNAL_SERVER_ERROR
        contentAsString(res) should include("enrolmentError.title")
      }
    }
  }
}
