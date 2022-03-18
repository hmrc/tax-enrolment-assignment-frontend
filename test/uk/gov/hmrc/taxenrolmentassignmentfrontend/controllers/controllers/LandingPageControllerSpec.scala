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

import play.api.http.Status.{INTERNAL_SERVER_ERROR, OK}
import play.api.libs.json.Format
import play.api.mvc.AnyContent
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.Enrolments
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve.{Credentials, Retrieval, ~}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes.{
  MULTIPLE_ACCOUNTS,
  PT_ASSIGNED_TO_CURRENT_USER,
  PT_ASSIGNED_TO_OTHER_USER,
  SINGLE_ACCOUNT
}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.auth.RequestWithUserDetails
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.helpers.TestData._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.helpers.TestFixture
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.{
  LandingPageController,
  testOnly
}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.views.html.LandingPage

import scala.concurrent.{ExecutionContext, Future}

class LandingPageControllerSpec extends TestFixture {

  val landingView: LandingPage = app.injector.instanceOf[LandingPage]

  val controller = new LandingPageController(
    mockAuthAction,
    mcc,
    mockTeaSessionCache,
    landingView
  )

  "view" when {
    "the cache returns an accountType of multiple accounts" should {
      "render the landing page" in {
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
            _: RequestWithUserDetails[AnyContent],
            _: Format[AccountTypes.Value]
          ))
          .expects("ACCOUNT_TYPE", *, *)
          .returning(Future.successful(Some(MULTIPLE_ACCOUNTS)))

        val result = controller.view
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(result) shouldBe OK
        contentAsString(result) shouldBe landingPageView()(
          fakeRequest,
          stubMessages()
        ).toString
      }
    }

    List(SINGLE_ACCOUNT, PT_ASSIGNED_TO_CURRENT_USER, PT_ASSIGNED_TO_OTHER_USER)
      .foreach { accountType =>
        s"the cache returns an accountType of $accountType" should {
          "redirect to the landing page" in {
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
                _: RequestWithUserDetails[AnyContent],
                _: Format[AccountTypes.Value]
              ))
              .expects("ACCOUNT_TYPE", *, *)
              .returning(Future.successful(Some(accountType)))

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
      }

    s"the cache has no accountType or redirectUrl" should {
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

        (mockTeaSessionCache
          .getEntry(_: String)(
            _: RequestWithUserDetails[AnyContent],
            _: Format[AccountTypes.Value]
          ))
          .expects("ACCOUNT_TYPE", *, *)
          .returning(Future.successful(None))

        (mockTeaSessionCache
          .getEntry(_: String)(
            _: RequestWithUserDetails[AnyContent],
            _: Format[String]
          ))
          .expects("redirectURL", *, *)
          .returning(Future.successful(None))

        val result = controller.view
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(result) shouldBe INTERNAL_SERVER_ERROR
      }
    }
  }
}
