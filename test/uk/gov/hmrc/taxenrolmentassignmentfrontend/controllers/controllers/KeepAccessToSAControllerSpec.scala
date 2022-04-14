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

import org.jsoup.Jsoup
import play.api.http.Status.{INTERNAL_SERVER_ERROR, OK}
import play.api.libs.json.Format
import play.api.mvc.AnyContent
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.Enrolments
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve.{Credentials, Retrieval, ~}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes.SA_ASSIGNED_TO_OTHER_USER
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.auth.RequestWithUserDetails
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.helpers.TestData._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.helpers.TestFixture
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.{
  EnrolledForPTController,
  KeepAccessToSAController,
  testOnly
}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.{
  InvalidUserType,
  UnexpectedResponseFromUsersGroupSearch
}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.forms.KeepAccessToSAThroughPTA
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.SessionKeys.KEEP_ACCESS_TO_SA_THROUGH_PTA_FORM
import uk.gov.hmrc.taxenrolmentassignmentfrontend.views.html.{
  EnrolledForPTPage,
  KeepAccessToSA
}

import scala.concurrent.{ExecutionContext, Future}

class KeepAccessToSAControllerSpec extends TestFixture {

  val view: KeepAccessToSA = app.injector.instanceOf[KeepAccessToSA]

  val controller = new KeepAccessToSAController(
    mockAuthAction,
    mockMultipleAccountsOrchestrator,
    mcc,
    logger,
    mockTeaSessionCache,
    view,
    errorView
  )

  "view" when {
    "the user has multiple accounts, is signed in with one with SA and has no form data in cache" should {
      "render the keep access to sa page with radio buttons unchecked" in {
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
          .expects(List(SA_ASSIGNED_TO_OTHER_USER), *, *, *)
          .returning(createInboundResult(SA_ASSIGNED_TO_OTHER_USER))

        (mockTeaSessionCache
          .getEntry(_: String)(
            _: RequestWithUserDetails[_],
            _: Format[KeepAccessToSAThroughPTA]
          ))
          .expects(KEEP_ACCESS_TO_SA_THROUGH_PTA_FORM, *, *)
          .returning(Future.successful(None))

        val result = controller.view
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(result) shouldBe OK
        val page = Jsoup
          .parse(contentAsString(result))

        page.title() shouldBe "keepAccessToSA.title"
        val radioInputs = page.getElementsByClass("govuk-radios__input")
        radioInputs.size() shouldBe 2
        radioInputs.get(0).text() shouldBe "Yes"
        radioInputs.get(0).hasAttr("checked") shouldBe false
        radioInputs.get(1).text() shouldBe "No"
        radioInputs.get(1).hasAttr("checked") shouldBe false
      }
    }

    "the user has multiple accounts, is signed in with one with SA and has previously selected Yes" should {
      "render the keep access to sa page with Yes checked" in {
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
          .expects(List(SA_ASSIGNED_TO_OTHER_USER), *, *, *)
          .returning(createInboundResult(SA_ASSIGNED_TO_OTHER_USER))

        (mockTeaSessionCache
          .getEntry(_: String)(
            _: RequestWithUserDetails[_],
            _: Format[KeepAccessToSAThroughPTA]
          ))
          .expects(KEEP_ACCESS_TO_SA_THROUGH_PTA_FORM, *, *)
          .returning(Future.successful(Some(KeepAccessToSAThroughPTA(true))))

        val result = controller.view
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(result) shouldBe OK
        val page = Jsoup
          .parse(contentAsString(result))

        page.title() shouldBe "keepAccessToSA.title"
        val radioInputs = page.getElementsByClass("govuk-radios__input")
        radioInputs.size() shouldBe 2
        radioInputs.get(0).text() shouldBe "Yes"
        radioInputs.get(0).hasAttr("checked") shouldBe true
        radioInputs.get(1).text() shouldBe "No"
        radioInputs.get(1).hasAttr("checked") shouldBe false
      }
    }

    "the user has multiple accounts, is signed in with one with SA and has previously selected No" should {
      "render the keep access to sa page with No checked" in {
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
          .expects(List(SA_ASSIGNED_TO_OTHER_USER), *, *, *)
          .returning(createInboundResult(SA_ASSIGNED_TO_OTHER_USER))

        (mockTeaSessionCache
          .getEntry(_: String)(
            _: RequestWithUserDetails[_],
            _: Format[KeepAccessToSAThroughPTA]
          ))
          .expects(KEEP_ACCESS_TO_SA_THROUGH_PTA_FORM, *, *)
          .returning(Future.successful(Some(KeepAccessToSAThroughPTA(false))))

        val result = controller.view
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(result) shouldBe OK
        val page = Jsoup
          .parse(contentAsString(result))

        page.title() shouldBe "keepAccessToSA.title"
        val radioInputs = page.getElementsByClass("govuk-radios__input")
        radioInputs.size() shouldBe 2
        radioInputs.get(0).text() shouldBe "Yes"
        radioInputs.get(0).hasAttr("checked") shouldBe false
        radioInputs.get(1).text() shouldBe "No"
        radioInputs.get(1).hasAttr("checked") shouldBe true
      }
    }

    "the user does not have SA on another account" should {
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
          .checkValidAccountTypeRedirectUrlInCache(_: List[AccountTypes.Value])(
            _: RequestWithUserDetails[AnyContent],
            _: HeaderCarrier,
            _: ExecutionContext
          ))
          .expects(List(SA_ASSIGNED_TO_OTHER_USER), *, *, *)
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
          "/tax-enrolment-assignment-frontend?redirectUrl=%2Ftax-enrolment-assignment-frontend%2Ftest-only%2Fsuccessful"
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

        val res = controller.view
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(res) shouldBe OK
        contentAsString(res) should include("enrolmentError.title")
      }
    }
  }

  "continue" when {
    "the user has selected Yes" should {
      "redirect to signin again" when {
        "they have SA on another account" in {
          (mockMultipleAccountsOrchestrator
            .checkValidAccountTypeRedirectUrlInCache(
              _: List[AccountTypes.Value]
            )(
              _: RequestWithUserDetails[AnyContent],
              _: HeaderCarrier,
              _: ExecutionContext
            ))
            .expects(List(SA_ASSIGNED_TO_OTHER_USER), *, *, *)
            .returning(createInboundResult(SA_ASSIGNED_TO_OTHER_USER))

          val res = controller.continue
            .apply(
              buildFakePOSTRequestWithSessionId(
                data = Map("keepAccessToSAThroughPTA" -> "yes")
              )
            )

          status(res) shouldBe SEE_OTHER
          redirectLocation(res) shouldBe Some(
            "/tax-enrolment-assignment-frontend/"
          )
        }
      }
    }
  }
}
