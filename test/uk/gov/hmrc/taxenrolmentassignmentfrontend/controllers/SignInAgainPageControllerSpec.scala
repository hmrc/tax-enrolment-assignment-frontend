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

import play.api.libs.json.Json
import play.api.mvc.AnyContent
import play.api.test.Helpers.{status, _}
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve.{Credentials, Retrieval, ~}
import uk.gov.hmrc.auth.core.{AffinityGroup, Enrolments}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes.SA_ASSIGNED_TO_OTHER_USER
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.actions.RequestWithUserDetailsFromSessionAndMongo
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.{IncorrectUserType, NoSAEnrolmentWhenOneExpected, UnexpectedPTEnrolment}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.TestData._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.{TestFixture, ThrottleHelperSpec, UrlPaths}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.reporting.AuditEvent
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.SessionKeys.{USER_ASSIGNED_SA_ENROLMENT, accountDetailsForCredential}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.views.html.SignInWithSAAccount

import scala.concurrent.{ExecutionContext, Future}

class SignInAgainPageControllerSpec extends TestFixture with ThrottleHelperSpec {
  val view: SignInWithSAAccount = app.injector.instanceOf[SignInWithSAAccount]
  val controller =
    new SignInWithSAAccountController(
      mockAuthAction,
      mockAccountMongoDetailsAction,
      mockThrottleAction,
      mcc,
      mockMultipleAccountsOrchestrator,
      view,
      logger,
      mockAuditHandler,
      errorHandler
    )

  "view" when {

    specificThrottleTests(controller.view())

    "a user has SA on another account" should {
      "render the signInWithSAAccount page" when {
        "the user has not already been assigned the PT enrolment" in {
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
            .returning(Future.successful(retrievalResponse()))

          (mockMultipleAccountsOrchestrator
            .checkAccessAllowedForPage(_: List[AccountTypes.Value])(
              _: RequestWithUserDetailsFromSessionAndMongo[AnyContent]
            ))
            .expects(List(SA_ASSIGNED_TO_OTHER_USER), *)
            .returning(Right(SA_ASSIGNED_TO_OTHER_USER))

          (mockMultipleAccountsOrchestrator
            .getSACredentialDetails(
              _: RequestWithUserDetailsFromSessionAndMongo[_],
              _: HeaderCarrier,
              _: ExecutionContext
            ))
            .expects(*, *, *)
            .returning(createInboundResult(accountDetails))
          mockGetDataFromCacheForActionSuccess(randomAccountType)
          mockAccountShouldNotBeThrottled(randomAccountType, NINO, noEnrolments.enrolments)

          val result = controller
            .view()
            .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

          status(result) shouldBe OK
          contentAsString(result) should include("signInAgain.title")
        }
      }
      s"redirect to ${UrlPaths.enrolledPTSAOnOtherAccountPath}" when {
        "the user has already been assigned the PT enrolment" in {
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
            .returning(Future.successful(retrievalResponse(enrolments = ptEnrolmentOnly)))

          (mockMultipleAccountsOrchestrator
            .checkAccessAllowedForPage(_: List[AccountTypes.Value])(
              _: RequestWithUserDetailsFromSessionAndMongo[AnyContent]
            ))
            .expects(List(SA_ASSIGNED_TO_OTHER_USER), *)
            .returning(Left(UnexpectedPTEnrolment))

          mockGetDataFromCacheForActionSuccess(SA_ASSIGNED_TO_OTHER_USER)
          mockAccountShouldNotBeThrottled(SA_ASSIGNED_TO_OTHER_USER, NINO, ptEnrolmentOnly.enrolments)

          val result = controller
            .view()
            .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some(UrlPaths.enrolledPTSAOnOtherAccountPath)
        }
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
              ] ~ Option[AffinityGroup]
            ]
          )(_: HeaderCarrier, _: ExecutionContext))
          .expects(predicates, retrievals, *, *)
          .returning(Future.successful(retrievalResponse()))
        mockGetDataFromCacheForActionNoRedirectUrl

        val result = controller
          .view()
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(result) shouldBe INTERNAL_SERVER_ERROR
        contentAsString(result) should include("enrolmentError.title")
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
              ] ~ Option[AffinityGroup]
            ]
          )(_: HeaderCarrier, _: ExecutionContext))
          .expects(predicates, retrievals, *, *)
          .returning(Future.successful(retrievalResponse()))

        (mockMultipleAccountsOrchestrator
          .checkAccessAllowedForPage(_: List[AccountTypes.Value])(
            _: RequestWithUserDetailsFromSessionAndMongo[AnyContent]
          ))
          .expects(List(SA_ASSIGNED_TO_OTHER_USER), *)
          .returning(
            Left(IncorrectUserType(UrlPaths.returnUrl, randomAccountType))
          )
        mockGetDataFromCacheForActionSuccess(randomAccountType)
        mockAccountShouldNotBeThrottled(randomAccountType, NINO, noEnrolments.enrolments)

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
              ] ~ Option[AffinityGroup]
            ]
          )(_: HeaderCarrier, _: ExecutionContext))
          .expects(predicates, retrievals, *, *)
          .returning(Future.successful(retrievalResponse()))

        (mockMultipleAccountsOrchestrator
          .checkAccessAllowedForPage(_: List[AccountTypes.Value])(
            _: RequestWithUserDetailsFromSessionAndMongo[_]
          ))
          .expects(List(SA_ASSIGNED_TO_OTHER_USER), *)
          .returning(Right(SA_ASSIGNED_TO_OTHER_USER))

        (mockMultipleAccountsOrchestrator
          .getSACredentialDetails(
            _: RequestWithUserDetailsFromSessionAndMongo[_],
            _: HeaderCarrier,
            _: ExecutionContext
          ))
          .expects(*, *, *)
          .returning(createInboundResultError(NoSAEnrolmentWhenOneExpected))
        mockGetDataFromCacheForActionSuccess(randomAccountType)
        mockAccountShouldNotBeThrottled(randomAccountType, NINO, noEnrolments.enrolments)

        val res = controller
          .view()
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(res) shouldBe INTERNAL_SERVER_ERROR
        contentAsString(res) should include("enrolmentError.title")
      }
    }
  }
  "continue" should {

    specificThrottleTests(controller.continue())

      s"redirect to ${UrlPaths.logoutPath}" in {
        val additionalCacheData = Map(USER_ASSIGNED_SA_ENROLMENT -> Json.toJson(UsersAssignedEnrolment1),
          accountDetailsForCredential(CREDENTIAL_ID_1) -> Json.toJson(accountDetails))
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
          .returning(Future.successful(retrievalResponse()))

        mockGetDataFromCacheForActionSuccess(SA_ASSIGNED_TO_OTHER_USER, UrlPaths.returnUrl, additionalCacheData)
        mockAccountShouldNotBeThrottled(SA_ASSIGNED_TO_OTHER_USER, NINO, noEnrolments.enrolments)
        val auditEvent = AuditEvent.auditSigninAgainWithSACredential()(requestWithAccountType(SA_ASSIGNED_TO_OTHER_USER, UrlPaths.returnUrl, additionalCacheData = additionalCacheData))
        (mockAuditHandler
          .audit(_: AuditEvent)(_: HeaderCarrier))
          .expects(auditEvent, *)
          .returning(Future.successful((): Unit))
          .once()

      val res = controller
        .continue()
        .apply(buildFakeRequestWithSessionId("POST", "Not Used"))

      status(res) shouldBe SEE_OTHER
      redirectLocation(res) shouldBe
        Some(UrlPaths.logoutPath)
    }

    "render the error page when redirect url not in cache" in {
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
        .returning(Future.successful(retrievalResponse()))
      mockGetDataFromCacheForActionNoRedirectUrl

        val result = controller
          .view()
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(result) shouldBe INTERNAL_SERVER_ERROR
        contentAsString(result) should include("enrolmentError.title")
    }
    }
}
