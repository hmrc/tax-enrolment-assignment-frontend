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
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes.SA_ASSIGNED_TO_OTHER_USER
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.auth.RequestWithUserDetails
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.{
  EnrolCurrentUserController,
  ReportSuspiciousIdController,
  testOnly
}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.helpers.TestData._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.helpers.TestFixture
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.{
  InvalidUserType,
  UnexpectedResponseFromTaxEnrolments
}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.UsersAssignedEnrolment
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.SessionKeys.REPORTED_FRAUD
import uk.gov.hmrc.taxenrolmentassignmentfrontend.views.html.{
  EnrolCurrentUser,
  ReportSuspiciousID
}

import scala.concurrent.{ExecutionContext, Future}

class ReportSuspiciousIdControllerSpec extends TestFixture {

  val view: ReportSuspiciousID = app.injector.instanceOf[ReportSuspiciousID]
  val controller =
    new ReportSuspiciousIdController(
      mockAuthAction,
      mockTeaSessionCache,
      mockMultipleAccountsOrchestrator,
      mcc,
      view,
      errorView
    )

  "view" when {
    "called" should {
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

        val result = controller
          .view()
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(result) shouldBe OK
        contentAsString(result) should include("reportSuspiciousId.title")
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
          "/tax-enrolment-assignment-frontend/enrol-pt/enrolment-success-sa-access-not-wanted"
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
            "/tax-enrolment-assignment-frontend/no-pt-enrolment?redirectUrl=%2Ftax-enrolment-assignment-frontend%2Ftest-only%2Fsuccessful"
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

        status(res) shouldBe INTERNAL_SERVER_ERROR
      }
    }
  }
}
