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
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes.SA_ASSIGNED_TO_OTHER_USER
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.auth.RequestWithUserDetails
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.{
  InvalidUserType,
  UnexpectedResponseFromTaxEnrolments
}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.forms.KeepAccessToSAThroughPTAForm.keepAccessToSAThroughPTAForm
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.TestData._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.{
  TestFixture,
  UrlPaths
}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.forms.KeepAccessToSAThroughPTA
import uk.gov.hmrc.taxenrolmentassignmentfrontend.views.html.KeepAccessToSA

import scala.concurrent.{ExecutionContext, Future}

class KeepAccessToSAControllerSpec extends TestFixture {

  val view: KeepAccessToSA = app.injector.instanceOf[KeepAccessToSA]

  val controller = new KeepAccessToSAController(
    mockAuthAction,
    mockMultipleAccountsOrchestrator,
    mcc,
    logger,
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
          .getDetailsForKeepAccessToSA(
            _: RequestWithUserDetails[AnyContent],
            _: HeaderCarrier,
            _: ExecutionContext
          ))
          .expects(*, *, *)
          .returning(createInboundResult(keepAccessToSAThroughPTAForm))

        val result = controller.view
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(result) shouldBe OK
        val page = Jsoup
          .parse(contentAsString(result))

        page.title() shouldBe "keepAccessToSA.title"
        val radioInputs = page.getElementsByClass("govuk-radios__input")
        radioInputs.size() shouldBe 2
        radioInputs.get(0).attr("value") shouldBe "yes"
        radioInputs.get(0).hasAttr("checked") shouldBe false
        radioInputs.get(1).attr("value") shouldBe "no"
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
          .getDetailsForKeepAccessToSA(
            _: RequestWithUserDetails[AnyContent],
            _: HeaderCarrier,
            _: ExecutionContext
          ))
          .expects(*, *, *)
          .returning(
            createInboundResult(
              keepAccessToSAThroughPTAForm.fill(KeepAccessToSAThroughPTA(true))
            )
          )

        val result = controller.view
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(result) shouldBe OK
        val page = Jsoup
          .parse(contentAsString(result))

        page.title() shouldBe "keepAccessToSA.title"
        val radioInputs = page.getElementsByClass("govuk-radios__input")
        radioInputs.size() shouldBe 2
        radioInputs.get(0).attr("value") shouldBe "yes"
        radioInputs.get(0).hasAttr("checked") shouldBe true
        radioInputs.get(1).attr("value") shouldBe "no"
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
          .getDetailsForKeepAccessToSA(
            _: RequestWithUserDetails[AnyContent],
            _: HeaderCarrier,
            _: ExecutionContext
          ))
          .expects(*, *, *)
          .returning(
            createInboundResult(
              keepAccessToSAThroughPTAForm.fill(KeepAccessToSAThroughPTA(false))
            )
          )

        val result = controller.view
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(result) shouldBe OK
        val page = Jsoup
          .parse(contentAsString(result))

        page.title() shouldBe "keepAccessToSA.title"
        val radioInputs = page.getElementsByClass("govuk-radios__input")
        radioInputs.size() shouldBe 2
        radioInputs.get(0).attr("value") shouldBe "yes"
        radioInputs.get(0).hasAttr("checked") shouldBe false
        radioInputs.get(1).attr("value") shouldBe "no"
        radioInputs.get(1).hasAttr("checked") shouldBe true
      }
    }

    "the user does not have SA on another account" should {
      s"redirect to ${UrlPaths.accountCheckPath}" in {
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
          .getDetailsForKeepAccessToSA(
            _: RequestWithUserDetails[AnyContent],
            _: HeaderCarrier,
            _: ExecutionContext
          ))
          .expects(*, *, *)
          .returning(
            createInboundResultError(InvalidUserType(Some(UrlPaths.returnUrl)))
          )

        val result = controller.view
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(UrlPaths.accountCheckPath)
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
          .getDetailsForKeepAccessToSA(
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
  }

  "continue" when {
    "the user has selected Yes" should {
      s"redirect to ${UrlPaths.saOnOtherAccountSigninAgainPath}" when {
        "they have SA on another account" in {
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
            .handleKeepAccessToSAChoice(_: KeepAccessToSAThroughPTA)(
              _: RequestWithUserDetails[AnyContent],
              _: HeaderCarrier,
              _: ExecutionContext
            ))
            .expects(KeepAccessToSAThroughPTA(true), *, *, *)
            .returning(createInboundResult(true))

          val res = controller.continue
            .apply(
              buildFakePOSTRequestWithSessionId(
                data = Map("select-continue" -> "yes")
              )
            )

          status(res) shouldBe SEE_OTHER
          redirectLocation(res) shouldBe Some(
            UrlPaths.saOnOtherAccountSigninAgainPath
          )
        }
      }
      s"redirect to ${UrlPaths.accountCheckPath}" when {
        "they don't have SA on another account" in {
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
            .handleKeepAccessToSAChoice(_: KeepAccessToSAThroughPTA)(
              _: RequestWithUserDetails[AnyContent],
              _: HeaderCarrier,
              _: ExecutionContext
            ))
            .expects(KeepAccessToSAThroughPTA(true), *, *, *)
            .returning(
              createInboundResultError(
                InvalidUserType(Some(UrlPaths.returnUrl))
              )
            )

          val res = controller.continue
            .apply(
              buildFakePOSTRequestWithSessionId(
                data = Map("select-continue" -> "yes")
              )
            )

          status(res) shouldBe SEE_OTHER
          redirectLocation(res) shouldBe Some(UrlPaths.accountCheckPath)
        }
      }

      "render the error page" when {
        "they don't have SA on another account or redirect url in cache" in {
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
            .handleKeepAccessToSAChoice(_: KeepAccessToSAThroughPTA)(
              _: RequestWithUserDetails[AnyContent],
              _: HeaderCarrier,
              _: ExecutionContext
            ))
            .expects(KeepAccessToSAThroughPTA(true), *, *, *)
            .returning(createInboundResultError(InvalidUserType(None)))

          val res = controller.continue
            .apply(
              buildFakePOSTRequestWithSessionId(
                data = Map("select-continue" -> "yes")
              )
            )

          status(res) shouldBe OK
          contentAsString(res) should include("enrolmentError.title")
        }
      }
    }

    "the user has selected No" should {
      s"be enrolled for PT and redirect to ${UrlPaths.enrolledPTSAOnOtherAccountPath}" when {
        "they have SA on another account" in {
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
            .handleKeepAccessToSAChoice(_: KeepAccessToSAThroughPTA)(
              _: RequestWithUserDetails[AnyContent],
              _: HeaderCarrier,
              _: ExecutionContext
            ))
            .expects(KeepAccessToSAThroughPTA(false), *, *, *)
            .returning(createInboundResult(false))

          val res = controller.continue
            .apply(
              buildFakePOSTRequestWithSessionId(
                data = Map("select-continue" -> "no")
              )
            )

          status(res) shouldBe SEE_OTHER
          redirectLocation(res) shouldBe Some(
            UrlPaths.enrolledPTSAOnOtherAccountPath
          )
        }
      }
      s"redirect to ${UrlPaths.accountCheckPath}" when {
        "they don't have SA on another account" in {
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
            .handleKeepAccessToSAChoice(_: KeepAccessToSAThroughPTA)(
              _: RequestWithUserDetails[AnyContent],
              _: HeaderCarrier,
              _: ExecutionContext
            ))
            .expects(KeepAccessToSAThroughPTA(false), *, *, *)
            .returning(
              createInboundResultError(
                InvalidUserType(Some(UrlPaths.returnUrl))
              )
            )

          val res = controller.continue
            .apply(
              buildFakePOSTRequestWithSessionId(
                data = Map("select-continue" -> "no")
              )
            )

          status(res) shouldBe SEE_OTHER
          redirectLocation(res) shouldBe Some(UrlPaths.accountCheckPath)
        }
      }

      "render the error page" when {
        "they don't have SA on another account or redirect url in cache" in {
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
            .handleKeepAccessToSAChoice(_: KeepAccessToSAThroughPTA)(
              _: RequestWithUserDetails[AnyContent],
              _: HeaderCarrier,
              _: ExecutionContext
            ))
            .expects(KeepAccessToSAThroughPTA(false), *, *, *)
            .returning(createInboundResultError(InvalidUserType(None)))

          val res = controller.continue
            .apply(
              buildFakePOSTRequestWithSessionId(
                data = Map("select-continue" -> "no")
              )
            )

          status(res) shouldBe OK
          contentAsString(res) should include("enrolmentError.title")
        }
      }

      "render the error page" when {
        "enrolling for PT fails" in {
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
            .handleKeepAccessToSAChoice(_: KeepAccessToSAThroughPTA)(
              _: RequestWithUserDetails[AnyContent],
              _: HeaderCarrier,
              _: ExecutionContext
            ))
            .expects(KeepAccessToSAThroughPTA(false), *, *, *)
            .returning(
              createInboundResultError(UnexpectedResponseFromTaxEnrolments)
            )

          val res = controller.continue
            .apply(
              buildFakePOSTRequestWithSessionId(
                data = Map("select-continue" -> "no")
              )
            )

          status(res) shouldBe OK
          contentAsString(res) should include("enrolmentError.title")
        }
      }
    }
    "a form error occurs" should {
      "render the keepAccessToSA page with error summary" in {
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

        val res = controller.continue
          .apply(
            buildFakePOSTRequestWithSessionId(
              data = Map("select-continue" -> "error")
            )
          )

        status(res) shouldBe BAD_REQUEST

        val page = Jsoup.parse(contentAsString(res))
        page
          .getElementsByClass("govuk-error-summary__title")
          .text() shouldBe "validation.summary.heading"
        page
          .getElementsByClass("govuk-list govuk-error-summary__list")
          .first()
          .text() shouldBe "keepAccessToSA.error.required"
      }
    }
  }
}
