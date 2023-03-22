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
import org.jsoup.Jsoup
import org.scalamock.handlers.{CallHandler1, CallHandler5}
import play.api.Application
import play.api.http.Status.OK
import play.api.inject.bind
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{Action, AnyContent, BodyParsers}
import uk.gov.hmrc.auth.core.{AffinityGroup, AuthConnector, Enrolment, Enrolments}
import play.api.test.Helpers._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve.{Credentials, Retrieval, ~}
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.service.TEAFResult
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.actions.{AccountMongoDetailsAction, RequestWithUserDetailsFromSession, RequestWithUserDetailsFromSessionAndMongo, ThrottleAction}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.TestData.{NINO, accountDetails, buildFakeRequestWithSessionId, noEnrolments, predicates, randomAccountType, retrievalResponse, retrievals}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.{BaseSpec, ControllersBaseSpec, TestFixture, ThrottleHelperSpec}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.orchestrators.{AccountCheckOrchestrator, MultipleAccountsOrchestrator}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.reporting.AuditHandler
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.SessionKeys.ACCOUNT_TYPE
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.TEASessionCache
import uk.gov.hmrc.taxenrolmentassignmentfrontend.services.{SilentAssignmentService, ThrottleApplied, ThrottleDoesNotApply, ThrottleResult, ThrottlingService}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.views.html.EnrolledForPTPage

import scala.concurrent.{ExecutionContext, Future}

class EnrolledForPTControllerSpec extends ControllersBaseSpec {

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
      bind[ThrottlingService].toInstance(mockThrottlingService),
      bind[AuthConnector].toInstance(mockAuthConnector),
      bind[BodyParsers.Default].toInstance(testBodyParser),
      bind[MultipleAccountsOrchestrator].toInstance(mockMultipleAccountsOrchestrator)
    )
    .build()

  lazy val controller = app.injector.instanceOf[EnrolledForPTController]

  lazy val view: EnrolledForPTPage =
    app.injector.instanceOf[EnrolledForPTPage]

  "view" when {
    specificThrottleTests(controller.view)

    "the user has multiple accounts and none have SA" should {
      "render the landing page" in {
        (mockAuthConnector
          .authorise(
            _: Predicate,
            _: Retrieval[
              ((Option[String] ~ Option[Credentials]) ~ Enrolments) ~ Option[
                String
              ] ~ Option[AffinityGroup] ~ Option[String]
            ]
          )(_: HeaderCarrier, _: ExecutionContext))
          .expects(predicates, retrievals, *, *)
          .returning(
            Future.successful(retrievalResponse())
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

        mockAccountShouldNotBeThrottled(randomAccountType, NINO, noEnrolments.enrolments)

        val result = controller.view
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(result) shouldBe OK
        Jsoup
          .parse(contentAsString(result))
          .body().text() should include(messages("enrolledForPT.heading"))
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
              ] ~ Option[AffinityGroup] ~ Option[String]
            ]
          )(_: HeaderCarrier, _: ExecutionContext))
          .expects(predicates, retrievals, *, *)
          .returning(Future.successful(retrievalResponse()))

        (mockMultipleAccountsOrchestrator
          .getDetailsForEnrolledPT(
            _: RequestWithUserDetailsFromSessionAndMongo[_],
            _: HeaderCarrier,
            _: ExecutionContext
          ))
          .expects(*, *, *)
          .returning(
            createInboundResultError(
              IncorrectUserType(
                testOnly.routes.TestOnlyController.successfulCall.url,randomAccountType
              )
            )
          )

        mockGetDataFromCacheForActionSuccess(randomAccountType)
        mockAccountShouldNotBeThrottled(randomAccountType, NINO, noEnrolments.enrolments)


        val result = controller.view
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(
          "/protect-tax-info?redirectUrl=%2Fprotect-tax-info%2Ftest-only%2Fsuccessful"
        )
      }
    }

    "the user is not a  multiple accounts usertype and has no redirectUrl stored in session" should {
      "render the error page" in {
        (mockAuthConnector
          .authorise(
            _: Predicate,
            _: Retrieval[
              ((Option[String] ~ Option[Credentials]) ~ Enrolments) ~ Option[
                String
              ] ~ Option[AffinityGroup] ~ Option[String]
            ]
          )(_: HeaderCarrier, _: ExecutionContext))
          .expects(predicates, retrievals, *, *)
          .returning(Future.successful(retrievalResponse()))

        mockGetDataFromCacheForActionNoRedirectUrl

        val res = controller.view
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(res) shouldBe INTERNAL_SERVER_ERROR
        contentAsString(res) should include(messages("enrolmentError.heading"))
      }
    }

    "the call to users-group-search fails" should {
      "render the error view" in {
        (mockAuthConnector
          .authorise(
            _: Predicate,
            _: Retrieval[
              ((Option[String] ~ Option[Credentials]) ~ Enrolments) ~ Option[
                String
              ] ~ Option[AffinityGroup] ~ Option[String]
            ]
          )(_: HeaderCarrier, _: ExecutionContext))
          .expects(predicates, retrievals, *, *)
          .returning(Future.successful(retrievalResponse()))

        (mockMultipleAccountsOrchestrator
          .getDetailsForEnrolledPT(
            _: RequestWithUserDetailsFromSessionAndMongo[_],
            _: HeaderCarrier,
            _: ExecutionContext
          ))
          .expects(*, *, *)
          .returning(
            createInboundResultError(UnexpectedResponseFromUsersGroupsSearch)
          )

        mockGetDataFromCacheForActionSuccess(randomAccountType)

        mockAccountShouldNotBeThrottled(randomAccountType, NINO, noEnrolments.enrolments)


        val res = controller.view
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(res) shouldBe INTERNAL_SERVER_ERROR
        contentAsString(res) should include(messages("enrolmentError.heading"))
      }
    }
  }
  "continue" when {
    specificThrottleTests(controller.continue)
  }
}
