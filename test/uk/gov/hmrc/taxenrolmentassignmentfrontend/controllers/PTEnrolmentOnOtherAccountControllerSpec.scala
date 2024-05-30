/*
 * Copyright 2023 HM Revenue & Customs
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

import play.api.Application
import play.api.http.Status.OK
import play.api.inject.bind
import play.api.mvc.{AnyContent, BodyParsers}
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve.{Credentials, Retrieval, ~}
import uk.gov.hmrc.auth.core.{AffinityGroup, AuthConnector, Enrolments}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes.PT_ASSIGNED_TO_OTHER_USER
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.actions.RequestWithUserDetailsFromSessionAndMongo
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.TestData._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.{ControllersBaseSpec, UrlPaths}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.orchestrators.{AccountCheckOrchestrator, MultipleAccountsOrchestrator}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.reporting.{AuditEvent, AuditHandler}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.TEASessionCache
import uk.gov.hmrc.taxenrolmentassignmentfrontend.services.SilentAssignmentService
import uk.gov.hmrc.taxenrolmentassignmentfrontend.views.html.PTEnrolmentOnAnotherAccount

import scala.concurrent.{ExecutionContext, Future}

class PTEnrolmentOnOtherAccountControllerSpec extends ControllersBaseSpec {

  lazy val mockSilentAssignmentService = mock[SilentAssignmentService]
  lazy val mockAccountCheckOrchestrator = mock[AccountCheckOrchestrator]
  lazy val mockAuditHandler = mock[AuditHandler]

  lazy val testBodyParser: BodyParsers.Default = mock[BodyParsers.Default]
  lazy val mockMultipleAccountsOrchestrator = mock[MultipleAccountsOrchestrator]

  override lazy val overrides = Seq(
    bind[TEASessionCache].toInstance(mockTeaSessionCache)
  )

  override implicit lazy val app: Application = localGuiceApplicationBuilder()
    .overrides(
      bind[SilentAssignmentService].toInstance(mockSilentAssignmentService),
      bind[AccountCheckOrchestrator].toInstance(mockAccountCheckOrchestrator),
      bind[AuditHandler].toInstance(mockAuditHandler),
      bind[AuthConnector].toInstance(mockAuthConnector),
      bind[BodyParsers.Default].toInstance(testBodyParser),
      bind[MultipleAccountsOrchestrator].toInstance(mockMultipleAccountsOrchestrator)
    )
    .build()

  lazy val controller = app.injector.instanceOf[PTEnrolmentOnOtherAccountController]

  val view: PTEnrolmentOnAnotherAccount =
    app.injector.instanceOf[PTEnrolmentOnAnotherAccount]

  "view" when {
    "the user with no SA has another account with PT enrolment" should {
      "render the pt on another page with no Access SA text" in {
        val ptEnrolmentDataModelNone = ptEnrolmentDataModel(None)
        (
          mockAuthConnector
            .authorise(
              _: Predicate,
              _: Retrieval[
                ((Option[String] ~ Option[Credentials]) ~ Enrolments) ~ Option[
                  String
                ] ~ Option[AffinityGroup] ~ Option[String]
              ]
            )(
              _: HeaderCarrier,
              _: ExecutionContext
            )
          )
          .expects(predicates, retrievals, *, *)
          .returning(Future.successful(retrievalResponse()))

        (
          mockMultipleAccountsOrchestrator
            .getCurrentAndPTAAndSAIfExistsForUser(
              _: RequestWithUserDetailsFromSessionAndMongo[AnyContent],
              _: HeaderCarrier,
              _: ExecutionContext
            )
          )
          .expects(*, *, *)
          .returning(createInboundResult(ptEnrolmentDataModelNone))
        mockGetDataFromCacheForActionSuccess(randomAccountType)

        val auditEvent = AuditEvent.auditPTEnrolmentOnOtherAccount(
          accountDetailsWithPT.copy(lastLoginDate = Some(s"27 February 2022 ${messages("common.dateToTime")} 12:00PM"))
        )(requestWithAccountType(randomAccountType), messagesApi)

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
              currentAccountDetails = ptEnrolmentDataModelNone.currentAccountDetails.copy(lastLoginDate =
                Some(s"27 February 2022 ${messages("common.dateToTime")} 12:00PM")
              ),
              ptAccountDetails = ptEnrolmentDataModelNone.ptAccountDetails.copy(lastLoginDate =
                Some(s"27 February 2022 ${messages("common.dateToTime")} 12:00PM")
              )
            )
        )(
          fakeRequest,
          messages
        ).toString
      }
    }

    "the current user with SA has another account with PT enrolment" should {
      "render the pt on another page with Access SA text" in {

        val ptEnrolmentModel = ptEnrolmentDataModel(Some(USER_ID))

        (
          mockAuthConnector
            .authorise(
              _: Predicate,
              _: Retrieval[
                ((Option[String] ~ Option[Credentials]) ~ Enrolments) ~ Option[
                  String
                ] ~ Option[AffinityGroup] ~ Option[String]
              ]
            )(
              _: HeaderCarrier,
              _: ExecutionContext
            )
          )
          .expects(predicates, retrievals, *, *)
          .returning(
            Future.successful(retrievalResponse(enrolments = saEnrolmentOnly))
          )

        (
          mockMultipleAccountsOrchestrator
            .getCurrentAndPTAAndSAIfExistsForUser(
              _: RequestWithUserDetailsFromSessionAndMongo[AnyContent],
              _: HeaderCarrier,
              _: ExecutionContext
            )
          )
          .expects(*, *, *)
          .returning(createInboundResult(ptEnrolmentModel))
        mockGetDataFromCacheForActionSuccess(randomAccountType)

        val auditEvent = AuditEvent.auditPTEnrolmentOnOtherAccount(
          accountDetailsWithPT.copy(lastLoginDate = Some(s"27 February 2022 ${messages("common.dateToTime")} 12:00PM"))
        )(requestWithAccountType(randomAccountType), messagesApi)

        (mockAuditHandler
          .audit(_: AuditEvent)(_: HeaderCarrier))
          .expects(auditEvent, *)
          .returning(Future.successful((): Unit))
          .once()

        val result = controller.view
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(result) shouldBe OK
        contentAsString(result) shouldBe view(
          ptEnrolmentModel.copy(
            currentAccountDetails = ptEnrolmentModel.currentAccountDetails.copy(lastLoginDate =
              Some(s"27 February 2022 ${messages("common.dateToTime")} 12:00PM")
            ),
            ptAccountDetails = ptEnrolmentModel.ptAccountDetails.copy(lastLoginDate =
              Some(s"27 February 2022 ${messages("common.dateToTime")} 12:00PM")
            )
          )
        )(
          fakeRequest,
          messages
        ).toString
      }
    }

    "the signed user has SA enrolment in session and PT enrolment on another account" should {
      "render the pt on another page with Access Self Assessment text" in {

        val ptEnrolmentModel = ptEnrolmentDataModel(Some(USER_ID))

        (
          mockAuthConnector
            .authorise(
              _: Predicate,
              _: Retrieval[
                ((Option[String] ~ Option[Credentials]) ~ Enrolments) ~ Option[
                  String
                ] ~ Option[AffinityGroup] ~ Option[String]
              ]
            )(
              _: HeaderCarrier,
              _: ExecutionContext
            )
          )
          .expects(predicates, retrievals, *, *)
          .returning(
            Future.successful(retrievalResponse(enrolments = saEnrolmentOnly))
          )

        (
          mockMultipleAccountsOrchestrator
            .getCurrentAndPTAAndSAIfExistsForUser(
              _: RequestWithUserDetailsFromSessionAndMongo[AnyContent],
              _: HeaderCarrier,
              _: ExecutionContext
            )
          )
          .expects(*, *, *)
          .returning(createInboundResult(ptEnrolmentModel))
        mockGetDataFromCacheForActionSuccess(randomAccountType)

        val auditEvent = AuditEvent.auditPTEnrolmentOnOtherAccount(
          accountDetailsWithPT.copy(lastLoginDate = Some(s"27 February 2022 ${messages("common.dateToTime")} 12:00PM"))
        )(requestWithAccountType(randomAccountType), messagesApi)

        (mockAuditHandler
          .audit(_: AuditEvent)(_: HeaderCarrier))
          .expects(auditEvent, *)
          .returning(Future.successful((): Unit))
          .once()

        val result = controller.view
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(result) shouldBe OK
        contentAsString(result) shouldBe view(
          ptEnrolmentModel.copy(
            currentAccountDetails = ptEnrolmentModel.currentAccountDetails.copy(lastLoginDate =
              Some(s"27 February 2022 ${messages("common.dateToTime")} 12:00PM")
            ),
            ptAccountDetails = ptEnrolmentModel.ptAccountDetails.copy(lastLoginDate =
              Some(s"27 February 2022 ${messages("common.dateToTime")} 12:00PM")
            )
          )
        )(
          fakeRequest,
          messages
        ).toString
      }
    }

    "the signed user has another account with SA enrolment which has both PT enrolment" should {
      "render the pt on another page with Access Self Assessment text" in {

        val ptEnrolmentModel = ptEnrolmentDataModel(Some(PT_USER_ID))

        (
          mockAuthConnector
            .authorise(
              _: Predicate,
              _: Retrieval[
                ((Option[String] ~ Option[Credentials]) ~ Enrolments) ~ Option[
                  String
                ] ~ Option[AffinityGroup] ~ Option[String]
              ]
            )(
              _: HeaderCarrier,
              _: ExecutionContext
            )
          )
          .expects(predicates, retrievals, *, *)
          .returning(
            Future.successful(retrievalResponse(enrolments = saEnrolmentOnly))
          )

        (
          mockMultipleAccountsOrchestrator
            .getCurrentAndPTAAndSAIfExistsForUser(
              _: RequestWithUserDetailsFromSessionAndMongo[AnyContent],
              _: HeaderCarrier,
              _: ExecutionContext
            )
          )
          .expects(*, *, *)
          .returning(createInboundResult(ptEnrolmentModel))
        mockGetDataFromCacheForActionSuccess(randomAccountType)

        val auditEvent = AuditEvent.auditPTEnrolmentOnOtherAccount(
          accountDetailsWithPT.copy(lastLoginDate = Some(s"27 February 2022 ${messages("common.dateToTime")} 12:00PM"))
        )(requestWithAccountType(randomAccountType), messagesApi)

        (mockAuditHandler
          .audit(_: AuditEvent)(_: HeaderCarrier))
          .expects(auditEvent, *)
          .returning(Future.successful((): Unit))
          .once()

        val result = controller.view
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(result) shouldBe OK
        contentAsString(result) shouldBe view(
          ptEnrolmentModel.copy(
            currentAccountDetails = ptEnrolmentModel.currentAccountDetails.copy(lastLoginDate =
              Some(s"27 February 2022 ${messages("common.dateToTime")} 12:00PM")
            ),
            ptAccountDetails = ptEnrolmentModel.ptAccountDetails.copy(lastLoginDate =
              Some(s"27 February 2022 ${messages("common.dateToTime")} 12:00PM")
            )
          )
        )(
          fakeRequest,
          messages
        ).toString
      }
    }

    "the signed user has other accounts with SA enrolment and a different one with PT enrolment" should {
      "render the pt on another page with Access Self Assessment text" in {

        val ptEnrolmentModel = ptEnrolmentDataModel(Some("8764"))

        (
          mockAuthConnector
            .authorise(
              _: Predicate,
              _: Retrieval[
                ((Option[String] ~ Option[Credentials]) ~ Enrolments) ~ Option[
                  String
                ] ~ Option[AffinityGroup] ~ Option[String]
              ]
            )(
              _: HeaderCarrier,
              _: ExecutionContext
            )
          )
          .expects(predicates, retrievals, *, *)
          .returning(
            Future.successful(retrievalResponse(enrolments = saEnrolmentOnly))
          )

        (
          mockMultipleAccountsOrchestrator
            .getCurrentAndPTAAndSAIfExistsForUser(
              _: RequestWithUserDetailsFromSessionAndMongo[AnyContent],
              _: HeaderCarrier,
              _: ExecutionContext
            )
          )
          .expects(*, *, *)
          .returning(createInboundResult(ptEnrolmentModel))
        mockGetDataFromCacheForActionSuccess(randomAccountType)

        val auditEvent = AuditEvent.auditPTEnrolmentOnOtherAccount(
          accountDetailsWithPT.copy(lastLoginDate = Some(s"27 February 2022 ${messages("common.dateToTime")} 12:00PM"))
        )(requestWithAccountType(randomAccountType), messagesApi)

        (mockAuditHandler
          .audit(_: AuditEvent)(_: HeaderCarrier))
          .expects(auditEvent, *)
          .returning(Future.successful((): Unit))
          .once()

        val result = controller.view
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(result) shouldBe OK
        contentAsString(result) shouldBe view(
          ptEnrolmentModel.copy(
            currentAccountDetails = ptEnrolmentModel.currentAccountDetails.copy(lastLoginDate =
              Some(s"27 February 2022 ${messages("common.dateToTime")} 12:00PM")
            ),
            ptAccountDetails = ptEnrolmentModel.ptAccountDetails.copy(lastLoginDate =
              Some(s"27 February 2022 ${messages("common.dateToTime")} 12:00PM")
            )
          )
        )(
          fakeRequest,
          messages
        ).toString
      }
    }

    s"the user does not have an account type of $PT_ASSIGNED_TO_OTHER_USER" should {
      s"redirect to ${UrlPaths.accountCheckPath}" in {
        (
          mockAuthConnector
            .authorise(
              _: Predicate,
              _: Retrieval[
                ((Option[String] ~ Option[Credentials]) ~ Enrolments) ~ Option[
                  String
                ] ~ Option[AffinityGroup] ~ Option[String]
              ]
            )(
              _: HeaderCarrier,
              _: ExecutionContext
            )
          )
          .expects(predicates, retrievals, *, *)
          .returning(Future.successful(retrievalResponse()))

        (
          mockMultipleAccountsOrchestrator
            .getCurrentAndPTAAndSAIfExistsForUser(
              _: RequestWithUserDetailsFromSessionAndMongo[AnyContent],
              _: HeaderCarrier,
              _: ExecutionContext
            )
          )
          .expects(*, *, *)
          .returning(
            createInboundResultError(IncorrectUserType(UrlPaths.returnUrl, randomAccountType))
          )
        mockGetDataFromCacheForActionSuccess(randomAccountType)

        val result = controller.view
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(UrlPaths.accountCheckPath)
      }
    }

    "the current user has a no PT enrolment on other account but session says it is other account" should {
      "render the error page" in {

        (
          mockAuthConnector
            .authorise(
              _: Predicate,
              _: Retrieval[
                ((Option[String] ~ Option[Credentials]) ~ Enrolments) ~ Option[
                  String
                ] ~ Option[AffinityGroup] ~ Option[String]
              ]
            )(
              _: HeaderCarrier,
              _: ExecutionContext
            )
          )
          .expects(predicates, retrievals, *, *)
          .returning(
            Future.successful(retrievalResponse(enrolments = saEnrolmentOnly))
          )

        (
          mockMultipleAccountsOrchestrator
            .getCurrentAndPTAAndSAIfExistsForUser(
              _: RequestWithUserDetailsFromSessionAndMongo[AnyContent],
              _: HeaderCarrier,
              _: ExecutionContext
            )
          )
          .expects(*, *, *)
          .returning(createInboundResultError(NoPTEnrolmentWhenOneExpected))
        mockGetDataFromCacheForActionSuccess(randomAccountType)

        val res = controller.view
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(res) shouldBe INTERNAL_SERVER_ERROR
        contentAsString(res) should include(messages("enrolmentError.heading"))
      }
    }
    "no redirect url in cache" should {
      "render the error page" in {
        (
          mockAuthConnector
            .authorise(
              _: Predicate,
              _: Retrieval[
                ((Option[String] ~ Option[Credentials]) ~ Enrolments) ~ Option[
                  String
                ] ~ Option[AffinityGroup] ~ Option[String]
              ]
            )(
              _: HeaderCarrier,
              _: ExecutionContext
            )
          )
          .expects(predicates, retrievals, *, *)
          .returning(Future.successful(retrievalResponse()))

        mockGetDataFromCacheForActionNoRedirectUrl

        val res = controller.view
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(res) shouldBe INTERNAL_SERVER_ERROR
        contentAsString(res) should include(messages("enrolmentError.heading"))
      }
    }
  }
}
