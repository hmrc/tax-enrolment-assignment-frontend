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
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.auth.RequestWithUserDetails
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.{InvalidUserType, NoPTEnrolmentWhenOneExpected, NoSAEnrolmentWhenOneExpected, UnexpectedResponseFromTaxEnrolments}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.TestData.{accountDetails, buildFakeRequestWithSessionId, predicates, retrievalResponse, retrievals, saEnrolmentOnly}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.TestFixture
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.SessionKeys.REPORTED_FRAUD
import uk.gov.hmrc.taxenrolmentassignmentfrontend.views.html.ReportSuspiciousID

import scala.concurrent.{ExecutionContext, Future}

class ReportSuspiciousIDControllerSpec extends TestFixture {

  val view: ReportSuspiciousID = app.injector.instanceOf[ReportSuspiciousID]
  val controller =
    new ReportSuspiciousIDController(
      mockAuthAction,
      mockTeaSessionCache,
      mockMultipleAccountsOrchestrator,
      mcc,
      view,
      logger,
      errorView
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
          .checkValidAccountTypeRedirectUrlInCache(_: List[AccountTypes.Value])(
            _: RequestWithUserDetails[AnyContent],
            _: HeaderCarrier,
            _: ExecutionContext
          ))
          .expects(List(PT_ASSIGNED_TO_OTHER_USER), *, *, *)
          .returning(createInboundResult(PT_ASSIGNED_TO_OTHER_USER))

        (mockMultipleAccountsOrchestrator
          .getPTCredentialDetails(
            _: RequestWithUserDetails[AnyContent],
            _: HeaderCarrier,
            _: ExecutionContext
          ))
          .expects(*, *, *)
          .returning(createInboundResult(accountDetails))

        val result = controller
          .viewNoSA()
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(result) shouldBe OK
        contentAsString(result) should include("ReportSuspiciousID.title")
      }
    }

    "the user does not have an account type of PT_ASSIGNED_TO_OTHER_USER" should {
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
          .expects(List(PT_ASSIGNED_TO_OTHER_USER), *, *, *)
          .returning(createInboundResultError(InvalidUserType(Some("/test"))))

        val result = controller
          .viewNoSA()
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(
          "/protect-tax-info?redirectUrl=%2Ftest"
        )
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
          .checkValidAccountTypeRedirectUrlInCache(_: List[AccountTypes.Value])(
            _: RequestWithUserDetails[AnyContent],
            _: HeaderCarrier,
            _: ExecutionContext
          ))
          .expects(List(PT_ASSIGNED_TO_OTHER_USER), *, *, *)
          .returning(createInboundResult(PT_ASSIGNED_TO_OTHER_USER))

        (mockMultipleAccountsOrchestrator
          .getPTCredentialDetails(
            _: RequestWithUserDetails[AnyContent],
            _: HeaderCarrier,
            _: ExecutionContext
          ))
          .expects(*, *, *)
          .returning(createInboundResultError(NoPTEnrolmentWhenOneExpected))

        val res = controller
          .viewNoSA()
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(res) shouldBe OK
        contentAsString(res) should include("enrolmentError.title")
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
          .expects(List(PT_ASSIGNED_TO_OTHER_USER), *, *, *)
          .returning(createInboundResultError(InvalidUserType(None)))

        val res = controller
          .viewNoSA()
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(res) shouldBe OK
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
          .checkValidAccountTypeRedirectUrlInCache(_: List[AccountTypes.Value])(
            _: RequestWithUserDetails[AnyContent],
            _: HeaderCarrier,
            _: ExecutionContext
          ))
          .expects(List(SA_ASSIGNED_TO_OTHER_USER), *, *, *)
          .returning(createInboundResult(SA_ASSIGNED_TO_OTHER_USER))

        (mockMultipleAccountsOrchestrator
          .getSACredentialDetails(
            _: RequestWithUserDetails[AnyContent],
            _: HeaderCarrier,
            _: ExecutionContext
          ))
          .expects(*, *, *)
          .returning(createInboundResult(accountDetails))

        val result = controller
          .viewSA()
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(result) shouldBe OK
        contentAsString(result) should include("ReportSuspiciousID.title")
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
          .viewSA()
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(
          "/protect-tax-info?redirectUrl=%2Ftest"
        )
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
            _: RequestWithUserDetails[AnyContent],
            _: HeaderCarrier,
            _: ExecutionContext
          ))
          .expects(List(SA_ASSIGNED_TO_OTHER_USER), *, *, *)
          .returning(createInboundResult(SA_ASSIGNED_TO_OTHER_USER))

        (mockMultipleAccountsOrchestrator
          .getSACredentialDetails(
            _: RequestWithUserDetails[AnyContent],
            _: HeaderCarrier,
            _: ExecutionContext
          ))
          .expects(*, *, *)
          .returning(createInboundResultError(NoSAEnrolmentWhenOneExpected))

        val res = controller
          .viewSA()
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(res) shouldBe OK
        contentAsString(res) should include("enrolmentError.title")
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
          .viewSA()
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(res) shouldBe OK
        contentAsString(res) should include("enrolmentError.title")
      }
    }
  }

  "continue" when {
    "the user has SA assigned to another user and enrolment to PT is successful" should {
      "redirect to EnrolledAfterReportingFraud" in {
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
            _: RequestWithUserDetails[AnyContent],
            _: Format[Boolean]
          ))
          .expects(REPORTED_FRAUD, true, *, *)
          .returning(Future(CacheMap(request.sessionID, Map())))

        (mockMultipleAccountsOrchestrator
          .checkValidAccountTypeAndEnrolForPT(_: AccountTypes.Value)(
            _: RequestWithUserDetails[AnyContent],
            _: HeaderCarrier,
            _: ExecutionContext
          ))
          .expects(SA_ASSIGNED_TO_OTHER_USER, *, *, *)
          .returning(createInboundResult((): Unit))

        val res = controller
          .continue()
          .apply(buildFakeRequestWithSessionId("POST", "Not Used"))

        status(res) shouldBe SEE_OTHER
        redirectLocation(res) shouldBe Some(
          "/protect-tax-info/enrol-pt/enrolment-success-sa-access-not-wanted"
        )
      }
    }

    "the user has not got SA assigned to another user" should {
      "redirect to AccountCheck" in {
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
            _: RequestWithUserDetails[AnyContent],
            _: Format[Boolean]
          ))
          .expects(REPORTED_FRAUD, true, *, *)
          .returning(Future(CacheMap(request.sessionID, Map())))

        (mockMultipleAccountsOrchestrator
          .checkValidAccountTypeAndEnrolForPT(_: AccountTypes.Value)(
            _: RequestWithUserDetails[AnyContent],
            _: HeaderCarrier,
            _: ExecutionContext
          ))
          .expects(SA_ASSIGNED_TO_OTHER_USER, *, *, *)
          .returning(
            createInboundResultError(
              InvalidUserType(
                Some(testOnly.routes.TestOnlyController.successfulCall.url)
              )
            )
          )

        val res = controller
          .continue()
          .apply(buildFakeRequestWithSessionId("POST", "Not Used"))

        status(res) shouldBe SEE_OTHER
        redirectLocation(res) shouldBe
          Some(
            "/protect-tax-info?redirectUrl=%2Fprotect-tax-info%2Ftest-only%2Fsuccessful"
          )
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
            _: RequestWithUserDetails[AnyContent],
            _: Format[Boolean]
          ))
          .expects(REPORTED_FRAUD, true, *, *)
          .returning(Future(CacheMap(request.sessionID, Map())))

        (mockMultipleAccountsOrchestrator
          .checkValidAccountTypeAndEnrolForPT(_: AccountTypes.Value)(
            _: RequestWithUserDetails[AnyContent],
            _: HeaderCarrier,
            _: ExecutionContext
          ))
          .expects(SA_ASSIGNED_TO_OTHER_USER, *, *, *)
          .returning(
            createInboundResultError(UnexpectedResponseFromTaxEnrolments)
          )

        val res = controller
          .continue()
          .apply(buildFakeRequestWithSessionId("POST", "Not Used"))

        status(res) shouldBe OK
        contentAsString(res) should include("enrolmentError.title")
      }
    }

    "the user has not a redirect url in session" should {
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
          .save(_: String, _: Boolean)(
            _: RequestWithUserDetails[AnyContent],
            _: Format[Boolean]
          ))
          .expects(REPORTED_FRAUD, true, *, *)
          .returning(Future(CacheMap(request.sessionID, Map())))

        (mockMultipleAccountsOrchestrator
          .checkValidAccountTypeAndEnrolForPT(_: AccountTypes.Value)(
            _: RequestWithUserDetails[AnyContent],
            _: HeaderCarrier,
            _: ExecutionContext
          ))
          .expects(SA_ASSIGNED_TO_OTHER_USER, *, *, *)
          .returning(createInboundResultError(InvalidUserType(None)))

        val res = controller
          .continue()
          .apply(buildFakeRequestWithSessionId("POST", "Not Used"))

        status(res) shouldBe OK
        contentAsString(res) should include("enrolmentError.title")
      }
    }
  }
}
