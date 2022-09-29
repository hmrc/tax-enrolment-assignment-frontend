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
import play.api.mvc.AnyContent
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve.{Credentials, Retrieval, ~}
import uk.gov.hmrc.auth.core.{AffinityGroup, Enrolments}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes.PT_ASSIGNED_TO_OTHER_USER
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.actions.RequestWithUserDetailsFromSessionAndMongo
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.TestData._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.{TestFixture, ThrottleHelperSpec, UrlPaths}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.reporting.AuditEvent
import uk.gov.hmrc.taxenrolmentassignmentfrontend.views.html.PTEnrolmentOnAnotherAccount

import scala.concurrent.{ExecutionContext, Future}

class PTEnrolmentOnOtherAccountControllerSpec extends TestFixture with ThrottleHelperSpec {

  val view: PTEnrolmentOnAnotherAccount =
    app.injector.instanceOf[PTEnrolmentOnAnotherAccount]

  val controller = new PTEnrolmentOnOtherAccountController(
    mockAuthAction,
    mockAccountMongoDetailsAction,
    mockThrottleAction,
    mcc,
    mockMultipleAccountsOrchestrator,
    view,
    logger,
    errorHandler,
    mockAuditHandler
  )

  "view" when {
    specificThrottleTests(controller.view())

    "the user with no SA has another account with PT enrolment" should {
      "render the pt on another page with no Access SA text" in {
        val ptEnrolmentDataModelNone = ptEnrolmentDataModel(None)
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
          .getCurrentAndPTAAndSAIfExistsForUser(
            _: RequestWithUserDetailsFromSessionAndMongo[AnyContent],
            _: HeaderCarrier,
            _: ExecutionContext
          ))
          .expects(*, *, *)
          .returning(createInboundResult(ptEnrolmentDataModelNone))
        mockGetDataFromCacheForActionSuccess(randomAccountType)
        mockAccountShouldNotBeThrottled(randomAccountType, NINO, noEnrolments.enrolments)

        val auditEvent = AuditEvent.auditPTEnrolmentOnOtherAccount(
          accountDetailsWithPT.copy(lastLoginDate = "27 common.month2 2022 common.dateToTime 12:00 PM")
        )(requestWithAccountType(randomAccountType), stubbedMessagesApi)

        (mockAuditHandler
          .audit(_: AuditEvent)(_: HeaderCarrier))
          .expects(auditEvent, *)
          .returning(Future.successful((): Unit))
          .once()

        val result = controller.view
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(result) shouldBe OK
        contentAsString(result) shouldBe view(
          ptEnrolmentDataModelNone
            .copy(
              currentAccountDetails = ptEnrolmentDataModelNone.currentAccountDetails.copy(lastLoginDate = "27 common.month2 2022 common.dateToTime 12:00 PM"),
              ptAccountDetails = ptEnrolmentDataModelNone.ptAccountDetails.copy(lastLoginDate = "27 common.month2 2022 common.dateToTime 12:00 PM")
            ))(
          fakeRequest,
          stubMessages()
        ).toString
      }
    }

    "the current user with SA has another account with PT enrolment" should {
      "render the pt on another page with Access SA text" in {

        val ptEnrolmentModel = ptEnrolmentDataModel(Some(USER_ID))

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
          .getCurrentAndPTAAndSAIfExistsForUser(
            _: RequestWithUserDetailsFromSessionAndMongo[AnyContent],
            _: HeaderCarrier,
            _: ExecutionContext
          ))
          .expects(*, *, *)
          .returning(createInboundResult(ptEnrolmentModel))
        mockGetDataFromCacheForActionSuccess(randomAccountType)
        mockAccountShouldNotBeThrottled(randomAccountType, NINO, saEnrolmentOnly.enrolments)

        val auditEvent = AuditEvent.auditPTEnrolmentOnOtherAccount(
          accountDetailsWithPT.copy(lastLoginDate = "27 common.month2 2022 common.dateToTime 12:00 PM")
        )(requestWithAccountType(randomAccountType), stubbedMessagesApi)

        (mockAuditHandler
          .audit(_: AuditEvent)(_: HeaderCarrier))
          .expects(auditEvent, *)
          .returning(Future.successful((): Unit))
          .once()

        val result = controller.view
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(result) shouldBe OK
        contentAsString(result) shouldBe view(ptEnrolmentModel.copy(
          currentAccountDetails = ptEnrolmentModel.currentAccountDetails.copy(lastLoginDate = "27 common.month2 2022 common.dateToTime 12:00 PM"),
          ptAccountDetails = ptEnrolmentModel.ptAccountDetails.copy(lastLoginDate = "27 common.month2 2022 common.dateToTime 12:00 PM")
        ))(
          fakeRequest,
          stubMessages()
        ).toString
      }
    }

    "the signed user has SA enrolment in session and PT enrolment on another account" should {
      "render the pt on another page with Access Self Assessment text" in {

        val ptEnrolmentModel = ptEnrolmentDataModel(Some(USER_ID))

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
          .getCurrentAndPTAAndSAIfExistsForUser(
            _: RequestWithUserDetailsFromSessionAndMongo[AnyContent],
            _: HeaderCarrier,
            _: ExecutionContext
          ))
          .expects(*, *, *)
          .returning(createInboundResult(ptEnrolmentModel))
        mockGetDataFromCacheForActionSuccess(randomAccountType)
        mockAccountShouldNotBeThrottled(randomAccountType, NINO, saEnrolmentOnly.enrolments)

        val auditEvent = AuditEvent.auditPTEnrolmentOnOtherAccount(
          accountDetailsWithPT.copy(lastLoginDate = "27 common.month2 2022 common.dateToTime 12:00 PM")
        )(requestWithAccountType(randomAccountType), stubbedMessagesApi)

        (mockAuditHandler
          .audit(_: AuditEvent)(_: HeaderCarrier))
          .expects(auditEvent, *)
          .returning(Future.successful((): Unit))
          .once()

        val result = controller.view
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(result) shouldBe OK
        contentAsString(result) shouldBe view(ptEnrolmentModel.copy(
          currentAccountDetails = ptEnrolmentModel.currentAccountDetails.copy(lastLoginDate = "27 common.month2 2022 common.dateToTime 12:00 PM"),
          ptAccountDetails = ptEnrolmentModel.ptAccountDetails.copy(lastLoginDate = "27 common.month2 2022 common.dateToTime 12:00 PM")
        ))(
          fakeRequest,
          stubMessages()
        ).toString
      }
    }

    "the signed user has another account with SA enrolment which has both PT enrolment" should {
      "render the pt on another page with Access Self Assessment text" in {

        val ptEnrolmentModel = ptEnrolmentDataModel(Some(PT_USER_ID))

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
          .getCurrentAndPTAAndSAIfExistsForUser(
            _: RequestWithUserDetailsFromSessionAndMongo[AnyContent],
            _: HeaderCarrier,
            _: ExecutionContext
          ))
          .expects(*, *, *)
          .returning(createInboundResult(ptEnrolmentModel))
        mockGetDataFromCacheForActionSuccess(randomAccountType)
        mockAccountShouldNotBeThrottled(randomAccountType, NINO, saEnrolmentOnly.enrolments)

        val auditEvent = AuditEvent.auditPTEnrolmentOnOtherAccount(
          accountDetailsWithPT.copy(lastLoginDate = "27 common.month2 2022 common.dateToTime 12:00 PM")
        )(requestWithAccountType(randomAccountType), stubbedMessagesApi)

        (mockAuditHandler
          .audit(_: AuditEvent)(_: HeaderCarrier))
          .expects(auditEvent, *)
          .returning(Future.successful((): Unit))
          .once()

        val result = controller.view
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(result) shouldBe OK
        contentAsString(result) shouldBe view(ptEnrolmentModel.copy(
          currentAccountDetails = ptEnrolmentModel.currentAccountDetails.copy(lastLoginDate = "27 common.month2 2022 common.dateToTime 12:00 PM"),
          ptAccountDetails = ptEnrolmentModel.ptAccountDetails.copy(lastLoginDate = "27 common.month2 2022 common.dateToTime 12:00 PM")
        ))(
          fakeRequest,
          stubMessages()
        ).toString
      }
    }

    "the signed user has other accounts with SA enrolment and a different one with PT enrolment" should {
      "render the pt on another page with Access Self Assessment text" in {

        val ptEnrolmentModel = ptEnrolmentDataModel(Some("8764"))

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
          .getCurrentAndPTAAndSAIfExistsForUser(
            _: RequestWithUserDetailsFromSessionAndMongo[AnyContent],
            _: HeaderCarrier,
            _: ExecutionContext
          ))
          .expects(*, *, *)
          .returning(createInboundResult(ptEnrolmentModel))
        mockGetDataFromCacheForActionSuccess(randomAccountType)
        mockAccountShouldNotBeThrottled(randomAccountType, NINO, saEnrolmentOnly.enrolments)

        val auditEvent = AuditEvent.auditPTEnrolmentOnOtherAccount(
          accountDetailsWithPT.copy(lastLoginDate = "27 common.month2 2022 common.dateToTime 12:00 PM")
        )(requestWithAccountType(randomAccountType), stubbedMessagesApi)

        (mockAuditHandler
          .audit(_: AuditEvent)(_: HeaderCarrier))
          .expects(auditEvent, *)
          .returning(Future.successful((): Unit))
          .once()

        val result = controller.view
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(result) shouldBe OK
        contentAsString(result) shouldBe view(ptEnrolmentModel.copy(
          currentAccountDetails = ptEnrolmentModel.currentAccountDetails.copy(lastLoginDate = "27 common.month2 2022 common.dateToTime 12:00 PM"),
          ptAccountDetails = ptEnrolmentModel.ptAccountDetails.copy(lastLoginDate = "27 common.month2 2022 common.dateToTime 12:00 PM")
        ))(
          fakeRequest,
          stubMessages()
        ).toString
      }
    }

    s"the user does not have an account type of $PT_ASSIGNED_TO_OTHER_USER" should {
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
          .getCurrentAndPTAAndSAIfExistsForUser(
            _: RequestWithUserDetailsFromSessionAndMongo[AnyContent],
            _: HeaderCarrier,
            _: ExecutionContext
          ))
          .expects(*, *, *)
          .returning(
            createInboundResultError(IncorrectUserType(UrlPaths.returnUrl, randomAccountType))
          )
        mockGetDataFromCacheForActionSuccess(randomAccountType)
        mockAccountShouldNotBeThrottled(randomAccountType, NINO, noEnrolments.enrolments)

        val result = controller.view
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
              ] ~ Option[AffinityGroup]
            ]
          )(_: HeaderCarrier, _: ExecutionContext))
          .expects(predicates, retrievals, *, *)
          .returning(
            Future.successful(retrievalResponse(enrolments = saEnrolmentOnly))
          )

        (mockMultipleAccountsOrchestrator
          .getCurrentAndPTAAndSAIfExistsForUser(
            _: RequestWithUserDetailsFromSessionAndMongo[AnyContent],
            _: HeaderCarrier,
            _: ExecutionContext
          ))
          .expects(*, *, *)
          .returning(createInboundResultError(NoPTEnrolmentWhenOneExpected))
        mockGetDataFromCacheForActionSuccess(randomAccountType)
        mockAccountShouldNotBeThrottled(randomAccountType, NINO, saEnrolmentOnly.enrolments)

        val res = controller.view
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(res) shouldBe INTERNAL_SERVER_ERROR
        contentAsString(res) should include("enrolmentError.heading")
      }
    }
    "no redirect url in cache" should {
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

        val res = controller.view
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(res) shouldBe INTERNAL_SERVER_ERROR
        contentAsString(res) should include("enrolmentError.heading")
      }
    }
  }
}
