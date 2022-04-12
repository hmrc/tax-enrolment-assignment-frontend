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


  import play.api.http.Status.{INTERNAL_SERVER_ERROR, OK, SEE_OTHER}
  import play.api.libs.json.Format
  import play.api.mvc.AnyContent
  import play.api.test.Helpers.{contentAsString, defaultAwaitTimeout, redirectLocation, status}
  import uk.gov.hmrc.auth.core.Enrolments
  import uk.gov.hmrc.auth.core.authorise.Predicate
  import uk.gov.hmrc.auth.core.retrieve.{Credentials, Retrieval, ~}
  import uk.gov.hmrc.http.HeaderCarrier
  import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.auth.RequestWithUserDetails
  import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.helpers.TestData._
  import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.helpers.TestFixture
  import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.{ReportSuspiciousIDController, SignInWithSAAccountController, SignOutController, testOnly}
  import uk.gov.hmrc.taxenrolmentassignmentfrontend.views.html.{ReportSuspiciousID, SignInWithSAAccount}

  import scala.concurrent.{ExecutionContext, Future}

class SignInAgainPageControllerSpec extends TestFixture {
  val view: SignInWithSAAccount = app.injector.instanceOf[SignInWithSAAccount]
  val signOutController = new SignOutController(mockAuthAction, mcc, testAppConfig, mockTeaSessionCache)
  lazy val reportSuspiciousIDController = new ReportSuspiciousIDController(mockAuthAction,mcc,logger,reportSuspiciousIDPage)
  lazy val reportSuspiciousIDPage: ReportSuspiciousID =inject[ReportSuspiciousID]

  val controller =
    new SignInWithSAAccountController(
      mockAuthAction,
      mcc,
      view,
      signOutController,
      reportSuspiciousIDController,
      mockEacdService,
      mockTeaSessionCache,
      logger,
      mockUsersGroupService
    )

  "view" when {
    "the user wants to keep access to Self Assessment from your personal tax account" should {
      "render the sign in again page" in {
        (mockAuthConnector
          .authorise(
            _: Predicate,
            _: Retrieval[Option[String] ~ Option[Credentials] ~ Enrolments ~ Option[String]]
          )(_: HeaderCarrier, _: ExecutionContext))
          .expects(predicates, retrievals, *, *)
          .returning(Future.successful(retrievalResponse()))

        (mockEacdService
          .getUsersAssignedSAEnrolment(
            _: RequestWithUserDetails[AnyContent],
            _: HeaderCarrier,
            _: ExecutionContext
          ))
          .expects(*, *, *)
          .returning(createInboundResult(UsersAssignedEnrolment1))

        (mockUsersGroupService
          .getAccountDetails(_: String)(
            _: ExecutionContext,
            _: HeaderCarrier,
            _: RequestWithUserDetails[AnyContent]
          ))
          .expects(CREDENTIAL_ID_1, *, *, *)
          .returning(createInboundResult(accountDetails))

        val result = controller.view().apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(result) shouldBe OK
        contentAsString(result) should include("signInAgain.title")
      }
    }

    "the current user has no SA enrolments assigned" should {
      "redirect to the account check" in {
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
            Future.successful(retrievalResponse(enrolments = ptEnrolmentOnly))
          )

        (mockEacdService
          .getUsersAssignedSAEnrolment(
            _: RequestWithUserDetails[AnyContent],
            _: HeaderCarrier,
            _: ExecutionContext
          ))
          .expects(*, *, *)
          .returning(createInboundResult(UsersAssignedEnrolmentEmpty))

        (mockTeaSessionCache
          .getEntry(_: String)(
            _: RequestWithUserDetails[AnyContent],
            _: Format[String]
          ))
          .expects("redirectURL", *, *)
          .returning(
            Future.successful(
              Some(testOnly.routes.TestOnlyController.successfulCall.url)
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

        (mockEacdService
          .getUsersAssignedSAEnrolment(
            _: RequestWithUserDetails[AnyContent],
            _: HeaderCarrier,
            _: ExecutionContext
          ))
          .expects(*, *, *)
          .returning(createInboundResult(UsersAssignedEnrolmentEmpty))

        (mockTeaSessionCache
          .getEntry(_: String)(
            _: RequestWithUserDetails[AnyContent],
            _: Format[String]
          ))
          .expects("redirectURL", *, *)
          .returning(
            Future.successful(
              None
            )
          )
        val result = controller.view
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(result) shouldBe INTERNAL_SERVER_ERROR
      }
    }
  }
}