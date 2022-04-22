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

import org.jsoup.Jsoup
import play.api.http.Status.OK
import play.api.mvc.AnyContent
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.Enrolments
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve.{Credentials, Retrieval, ~}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.auth.RequestWithUserDetails
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.{
  InvalidUserType,
  UnexpectedResponseFromUsersGroupsSearch
}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.TestData.{
  accountDetails,
  buildFakeRequestWithSessionId,
  predicates,
  retrievalResponse,
  retrievals,
  saEnrolmentOnly
}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.TestFixture
import uk.gov.hmrc.taxenrolmentassignmentfrontend.views.html.EnrolledForPTWithSAOnOtherAccount

import scala.concurrent.{ExecutionContext, Future}

class EnrolledPTWithSAOnOtherAccountControllerSpec extends TestFixture {

  val view: EnrolledForPTWithSAOnOtherAccount =
    app.injector.instanceOf[EnrolledForPTWithSAOnOtherAccount]

  val controller = new EnrolledPTWithSAOnOtherAccountController(
    mockAuthAction,
    mockMultipleAccountsOrchestrator,
    mockTeaSessionCache,
    mcc,
    view,
    logger,
    errorView
  )

  "view" when {
    "the user has enrolled for PT after reporting fraud" should {
      "render the EnrolledForPTWithSAOnOtherAccount page without SA" in {
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
          .getDetailsForEnrolledPTWithSAOnOtherAccount(
            _: RequestWithUserDetails[AnyContent],
            _: HeaderCarrier,
            _: ExecutionContext
          ))
          .expects(*, *, *)
          .returning(createInboundResult(accountDetails))

        (mockMultipleAccountsOrchestrator
          .getSACredentialIfNotFraud(
            _: RequestWithUserDetails[AnyContent],
            _: HeaderCarrier,
            _: ExecutionContext
          ))
          .expects(*, *, *)
          .returning(createInboundResult(None))

        val result = controller.view
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(result) shouldBe OK
        val content = Jsoup
          .parse(contentAsString(result))

        content.title() shouldBe "enrolledForPTWithSAOnOtherAccount.title"
        content.body().text() shouldNot include(
          "enrolledForPTWithSAOnOtherAccount.h2.paragraph1"
        )
      }
    }

    "the user has enrolled for PT after choosing to have SA separate" should {
      "render the EnrolledForPTWithSAOnOtherAccount page with SA details" in {
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
          .getDetailsForEnrolledPTWithSAOnOtherAccount(
            _: RequestWithUserDetails[AnyContent],
            _: HeaderCarrier,
            _: ExecutionContext
          ))
          .expects(*, *, *)
          .returning(createInboundResult(accountDetails))

        (mockMultipleAccountsOrchestrator
          .getSACredentialIfNotFraud(
            _: RequestWithUserDetails[AnyContent],
            _: HeaderCarrier,
            _: ExecutionContext
          ))
          .expects(*, *, *)
          .returning(
            createInboundResult(
              Some(accountDetails.copy(userId = "********1234"))
            )
          )

        val result = controller.view
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(result) shouldBe OK
        val content = Jsoup
          .parse(contentAsString(result))

        content.title() shouldBe "enrolledForPTWithSAOnOtherAccount.title"
        content.body().text() should include(
          "enrolledForPTWithSAOnOtherAccount.h2.paragraph1"
        )
      }
    }

    "the user is the wrong usertype and has redirectUrl stored in session" should {
      "redirect to accountCheck" in {
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
          .getDetailsForEnrolledPTWithSAOnOtherAccount(
            _: RequestWithUserDetails[AnyContent],
            _: HeaderCarrier,
            _: ExecutionContext
          ))
          .expects(*, *, *)
          .returning(
            createInboundResultError(
              InvalidUserType(
                Some(testOnly.routes.TestOnlyController.successfulCall.url)
              )
            )
          )

        val result = controller.view
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(
          "/protect-tax-info?redirectUrl=%2Fprotect-tax-info%2Ftest-only%2Fsuccessful"
        )
      }
    }

    "the user is the wrong usertype and has no redirectUrl stored in session" should {
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

        (mockMultipleAccountsOrchestrator
          .getDetailsForEnrolledPTWithSAOnOtherAccount(
            _: RequestWithUserDetails[AnyContent],
            _: HeaderCarrier,
            _: ExecutionContext
          ))
          .expects(*, *, *)
          .returning(createInboundResultError(InvalidUserType(None)))

        val res = controller.view
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(res) shouldBe OK
        contentAsString(res) should include("enrolmentError.title")
      }
    }

    "the call to users-group-search fails" should {
      "render error view" in {
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
          .getDetailsForEnrolledPTWithSAOnOtherAccount(
            _: RequestWithUserDetails[AnyContent],
            _: HeaderCarrier,
            _: ExecutionContext
          ))
          .expects(*, *, *)
          .returning(
            createInboundResultError(UnexpectedResponseFromUsersGroupsSearch)
          )

        val res = controller.view
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(res) shouldBe OK
        contentAsString(res) should include("enrolmentError.title")
      }
    }
  }
}