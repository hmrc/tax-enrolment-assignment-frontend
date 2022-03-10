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

import cats.data.EitherT
import play.api.http.Status.{INTERNAL_SERVER_ERROR, OK, SEE_OTHER}
import play.api.mvc.AnyContent
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.Enrolments
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve.{Credentials, Retrieval, ~}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.auth.RequestWithUserDetails
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.helpers.TestData._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.helpers.TestFixture
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.{
  LandingPageController,
  testOnly
}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.UnexpectedResponseFromIV
import uk.gov.hmrc.taxenrolmentassignmentfrontend.views.html.LandingPage
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.{
  LandingPageController,
  testOnly
}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.{
  TaxEnrolmentAssignmentErrors,
  UnexpectedResponseFromIV,
  UnexpectedResponseFromTaxEnrolments
}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.IVNinoStoreEntry
import uk.gov.hmrc.taxenrolmentassignmentfrontend.services.SilentAssignmentService
import uk.gov.hmrc.taxenrolmentassignmentfrontend.views.html.{
  ErrorTemplate,
  LandingPage
}

import scala.concurrent.{ExecutionContext, Future}

class LandingPageControllerSpec extends TestFixture {

  val testTeaSessionCache = new TestTeaSessionCache
  val landingView: LandingPage = app.injector.instanceOf[LandingPage]
  val errorView: ErrorTemplate = app.injector.instanceOf[ErrorTemplate]
  val mockSilentAssignmentService: SilentAssignmentService =
    mock[SilentAssignmentService]

  val controller = new LandingPageController(
    mockAuthAction,
    testAppConfig,
    mockIVConnector,
    mockSilentAssignmentService,
    mcc,
    testTeaSessionCache,
    landingView,
    errorView
  )

  "showLandingPage" when {
    "a single credential exists for a given nino" should {
      "silently assign the HMRC-PT Enrolment and redirect to PTA" in {
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
        (mockIVConnector
          .getCredentialsWithNino(_: String)(
            _: ExecutionContext,
            _: HeaderCarrier
          ))
          .expects(NINO, *, *)
          .returning(createInboundResult(List(ivNinoStoreEntry4)))

        (mockSilentAssignmentService
          .getValidPtaAccounts(_: Seq[IVNinoStoreEntry])(
            _: HeaderCarrier,
            _: ExecutionContext
          ))
          .expects(*, *, *)
          .returning((Future.successful(Seq(Some(ivNinoStoreEntry4)))))

        (mockSilentAssignmentService
          .enrolUser()(
            _: RequestWithUserDetails[AnyContent],
            _: HeaderCarrier,
            _: ExecutionContext
          ))
          .expects(*, *, *)
          .returning(
            EitherT.right[TaxEnrolmentAssignmentErrors](Future.successful(Unit))
          )

        val result = controller
          .showLandingPage(
            testOnly.routes.TestOnlyController.successfulCall.url
          )
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(
          "http://localhost:9232/personal-account"
        )
      }

      "return an error page if there was an error assigning the enrolment" in {
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
        (mockIVConnector
          .getCredentialsWithNino(_: String)(
            _: ExecutionContext,
            _: HeaderCarrier
          ))
          .expects(NINO, *, *)
          .returning(createInboundResult(List(ivNinoStoreEntry4)))

        (mockSilentAssignmentService
          .getValidPtaAccounts(_: Seq[IVNinoStoreEntry])(
            _: HeaderCarrier,
            _: ExecutionContext
          ))
          .expects(*, *, *)
          .returning((Future.successful(Seq(Some(ivNinoStoreEntry4)))))

        (mockSilentAssignmentService
          .enrolUser()(
            _: RequestWithUserDetails[AnyContent],
            _: HeaderCarrier,
            _: ExecutionContext
          ))
          .expects(*, *, *)
          .returning(
            createInboundResultError(UnexpectedResponseFromTaxEnrolments)
          )

        val result = controller
          .showLandingPage(
            testOnly.routes.TestOnlyController.successfulCall.url
          )
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(result) shouldBe OK
        contentAsString(result) should include("enrolmentError.title")
      }
    }

    "multiple credentials exists for a given nino" should {
      "present the landing page" in {
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
        (mockIVConnector
          .getCredentialsWithNino(_: String)(
            _: ExecutionContext,
            _: HeaderCarrier
          ))
          .expects(NINO, *, *)
          .returning(createInboundResult(multiIVCreds))
        (mockSilentAssignmentService
          .getValidPtaAccounts(_: Seq[IVNinoStoreEntry])(
            _: HeaderCarrier,
            _: ExecutionContext
          ))
          .expects(*, *, *)
          .returning((Future.successful(multiOptionalIVCreds)))

        val result = controller
          .showLandingPage(
            testOnly.routes.TestOnlyController.successfulCall.url
          )
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(result) shouldBe OK
        contentAsString(result) should include("landingPage.title")
      }
    }

    "a single credential exists for a given nino that is already enrolled for PT" should {
      "redirect to the return url" in {
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

        val result = controller
          .showLandingPage(
            testOnly.routes.TestOnlyController.successfulCall.url
          )
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(
          "/tax-enrolment-assignment-frontend/test-only/successful"
        )
      }
    }

    "a no credentials exists in IV for a given nino" should {
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
        (mockIVConnector
          .getCredentialsWithNino(_: String)(
            _: ExecutionContext,
            _: HeaderCarrier
          ))
          .expects(NINO, *, *)
          .returning(createInboundResultError(UnexpectedResponseFromIV))

        val result = controller
          .showLandingPage(
            testOnly.routes.TestOnlyController.successfulCall.url
          )
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(result) shouldBe INTERNAL_SERVER_ERROR
      }
    }
  }

}
