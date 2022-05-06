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

import play.api.http.Status.OK
import play.api.libs.json.Format
import play.api.mvc.AnyContent
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.Enrolments
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve.{Credentials, Retrieval, ~}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes.{PT_ASSIGNED_TO_OTHER_USER, SA_ASSIGNED_TO_OTHER_USER}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.actions.{RequestWithUserDetailsFromSession, RequestWithUserDetailsFromSessionAndMongo}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.{IncorrectUserType, NoPTEnrolmentWhenOneExpected, NoSAEnrolmentWhenOneExpected, UnexpectedResponseFromTaxEnrolments}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.TestData._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.{TestFixture, UrlPaths}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.SessionKeys.REPORTED_FRAUD
import uk.gov.hmrc.taxenrolmentassignmentfrontend.views.html.ReportSuspiciousID

import scala.concurrent.{ExecutionContext, Future}

class ReportSuspiciousIDControllerSpec extends TestFixture {

  val view: ReportSuspiciousID = app.injector.instanceOf[ReportSuspiciousID]
  val controller =
    new ReportSuspiciousIDController(
      mockAuthAction,
      mockAccountMongoDetailsAction,
      mockTeaSessionCache,
      mockMultipleAccountsOrchestrator,
      mcc,
      view,
      logger,
      errorHandler
    )

  "viewNoSA" when {
    "a user has PT on another account" should {
      "render the ReportSuspiciousID page" in {
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
          .checkValidAccountType(_: List[AccountTypes.Value])(
            _: RequestWithUserDetailsFromSessionAndMongo[AnyContent]
          ))
          .expects(List(PT_ASSIGNED_TO_OTHER_USER), *)
          .returning(Right(PT_ASSIGNED_TO_OTHER_USER))

        (mockMultipleAccountsOrchestrator
          .getPTCredentialDetails(
            _: RequestWithUserDetailsFromSessionAndMongo[AnyContent],
            _: HeaderCarrier,
            _: ExecutionContext
          ))
          .expects(*, *, *)
          .returning(createInboundResult(accountDetails))
        mockGetAccountTypeAndRedirectUrlSuccess(randomAccountType)

        val result = controller
          .viewNoSA()
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(result) shouldBe OK
        contentAsString(result) should include("ReportSuspiciousID.title")
      }
    }

    "the user does not have an account type of PT_ASSIGNED_TO_OTHER_USER" should {
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
          .checkValidAccountType(_: List[AccountTypes.Value])(
            _: RequestWithUserDetailsFromSessionAndMongo[AnyContent]
          ))
          .expects(List(PT_ASSIGNED_TO_OTHER_USER), *)
          .returning(
            Left(IncorrectUserType(UrlPaths.returnUrl, randomAccountType))
          )
        mockGetAccountTypeAndRedirectUrlSuccess(randomAccountType)

        val result = controller
          .viewNoSA()
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(UrlPaths.accountCheckPath)
      }
    }

    "the current user has a no PT enrolment on other account but session says it is other account" should {
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
          .returning(
            Future.successful(retrievalResponse(enrolments = saEnrolmentOnly))
          )

        (mockMultipleAccountsOrchestrator
          .checkValidAccountType(_: List[AccountTypes.Value])(
            _: RequestWithUserDetailsFromSessionAndMongo[AnyContent]
          ))
          .expects(List(PT_ASSIGNED_TO_OTHER_USER), *)
          .returning(Right(PT_ASSIGNED_TO_OTHER_USER))

        (mockMultipleAccountsOrchestrator
          .getPTCredentialDetails(
            _: RequestWithUserDetailsFromSessionAndMongo[AnyContent],
            _: HeaderCarrier,
            _: ExecutionContext
          ))
          .expects(*, *, *)
          .returning(createInboundResultError(NoPTEnrolmentWhenOneExpected))
        mockGetAccountTypeAndRedirectUrlSuccess(randomAccountType)

        val res = controller
          .viewNoSA()
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(res) shouldBe INTERNAL_SERVER_ERROR
        contentAsString(res) should include("enrolmentError.title")
      }
    }

  }

  "viewSA" when {
    "a user has SA on another account" should {
      "render the ReportSuspiciousID page" in {
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
          .checkValidAccountType(_: List[AccountTypes.Value])(
            _: RequestWithUserDetailsFromSessionAndMongo[AnyContent]
          ))
          .expects(List(SA_ASSIGNED_TO_OTHER_USER), *)
          .returning(Right(SA_ASSIGNED_TO_OTHER_USER))

        (mockMultipleAccountsOrchestrator
          .getSACredentialDetails(
            _: RequestWithUserDetailsFromSessionAndMongo[AnyContent],
            _: HeaderCarrier,
            _: ExecutionContext
          ))
          .expects(*, *, *)
          .returning(createInboundResult(accountDetails))
        mockGetAccountTypeAndRedirectUrlSuccess(randomAccountType)

        val result = controller
          .viewSA()
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(result) shouldBe OK
        contentAsString(result) should include("ReportSuspiciousID.title")
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
        mockGetAccountTypeSucessRedirectFail

        val res = controller
          .viewNoSA()
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(res) shouldBe INTERNAL_SERVER_ERROR
        contentAsString(res) should include("enrolmentError.title")
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
          .checkValidAccountType(_: List[AccountTypes.Value])(
            _: RequestWithUserDetailsFromSessionAndMongo[AnyContent]
          ))
          .expects(List(SA_ASSIGNED_TO_OTHER_USER), *)
          .returning(
            Left(IncorrectUserType(UrlPaths.returnUrl, randomAccountType))
          )
        mockGetAccountTypeAndRedirectUrlSuccess(randomAccountType)

        val result = controller
          .viewSA()
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
          .checkValidAccountType(_: List[AccountTypes.Value])(
            _: RequestWithUserDetailsFromSessionAndMongo[AnyContent]
          ))
          .expects(List(SA_ASSIGNED_TO_OTHER_USER), *)
          .returning(Right(SA_ASSIGNED_TO_OTHER_USER))

        (mockMultipleAccountsOrchestrator
          .getSACredentialDetails(
            _: RequestWithUserDetailsFromSessionAndMongo[AnyContent],
            _: HeaderCarrier,
            _: ExecutionContext
          ))
          .expects(*, *, *)
          .returning(createInboundResultError(NoSAEnrolmentWhenOneExpected))
        mockGetAccountTypeAndRedirectUrlSuccess(randomAccountType)

        val res = controller
          .viewSA()
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(res) shouldBe INTERNAL_SERVER_ERROR
        contentAsString(res) should include("enrolmentError.title")
      }
    }
  }

  "continue" when {
    "the user has SA assigned to another user and enrolment to PT is successful" should {
      s"redirect to ${UrlPaths.enrolledPTSAOnOtherAccountPath}" in {
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
          .save(_: String, _: Boolean)(
            _: RequestWithUserDetailsFromSessionAndMongo[AnyContent],
            _: Format[Boolean]
          ))
          .expects(REPORTED_FRAUD, true, *, *)
          .returning(Future(CacheMap(request.sessionID, Map())))

        (mockMultipleAccountsOrchestrator
          .checkValidAccountTypeAndEnrolForPT(_: AccountTypes.Value)(
            _: RequestWithUserDetailsFromSessionAndMongo[AnyContent],
            _: HeaderCarrier,
            _: ExecutionContext
          ))
          .expects(SA_ASSIGNED_TO_OTHER_USER, *, *, *)
          .returning(createInboundResult((): Unit))
        mockGetAccountTypeAndRedirectUrlSuccess(randomAccountType)

        val res = controller
          .continue()
          .apply(buildFakeRequestWithSessionId("POST", "Not Used"))

        status(res) shouldBe SEE_OTHER
        redirectLocation(res) shouldBe Some(
          UrlPaths.enrolledPTSAOnOtherAccountPath
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
        mockGetAccountTypeSucessRedirectFail

        val res = controller
          .continue()
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(res) shouldBe INTERNAL_SERVER_ERROR
        contentAsString(res) should include("enrolmentError.title")
      }
    }

    "the user has not got SA assigned to another user" should {
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

        (mockTeaSessionCache
          .save(_: String, _: Boolean)(
            _: RequestWithUserDetailsFromSession[AnyContent],
            _: Format[Boolean]
          ))
          .expects(REPORTED_FRAUD, true, *, *)
          .returning(Future(CacheMap(request.sessionID, Map())))

        (mockMultipleAccountsOrchestrator
          .checkValidAccountTypeAndEnrolForPT(_: AccountTypes.Value)(
            _: RequestWithUserDetailsFromSessionAndMongo[AnyContent],
            _: HeaderCarrier,
            _: ExecutionContext
          ))
          .expects(SA_ASSIGNED_TO_OTHER_USER, *, *, *)
          .returning(
            createInboundResultError(IncorrectUserType(UrlPaths.returnUrl, randomAccountType))
          )
        mockGetAccountTypeAndRedirectUrlSuccess(randomAccountType)

        val res = controller
          .continue()
          .apply(buildFakeRequestWithSessionId("POST", "Not Used"))

        status(res) shouldBe SEE_OTHER
        redirectLocation(res) shouldBe
          Some(UrlPaths.accountCheckPath)
      }
    }

    "the user has SA assigned to another user but enrolment to PT is unsuccessful" should {
      "render the error view" in {
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
          .save(_: String, _: Boolean)(
            _: RequestWithUserDetailsFromSession[AnyContent],
            _: Format[Boolean]
          ))
          .expects(REPORTED_FRAUD, true, *, *)
          .returning(Future(CacheMap(request.sessionID, Map())))

        (mockMultipleAccountsOrchestrator
          .checkValidAccountTypeAndEnrolForPT(_: AccountTypes.Value)(
            _: RequestWithUserDetailsFromSessionAndMongo[AnyContent],
            _: HeaderCarrier,
            _: ExecutionContext
          ))
          .expects(SA_ASSIGNED_TO_OTHER_USER, *, *, *)
          .returning(
            createInboundResultError(UnexpectedResponseFromTaxEnrolments)
          )
        mockGetAccountTypeAndRedirectUrlSuccess(randomAccountType)

        val res = controller
          .continue()
          .apply(buildFakeRequestWithSessionId("POST", "Not Used"))

        status(res) shouldBe INTERNAL_SERVER_ERROR
        contentAsString(res) should include("enrolmentError.title")
      }
    }
  }
}
