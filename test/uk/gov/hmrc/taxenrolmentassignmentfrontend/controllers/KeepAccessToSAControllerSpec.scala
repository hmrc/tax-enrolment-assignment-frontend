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
import play.api.libs.json.Json
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve.{Credentials, Retrieval, ~}
import uk.gov.hmrc.auth.core.{AffinityGroup, Enrolments}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes.{PT_ASSIGNED_TO_OTHER_USER, SA_ASSIGNED_TO_OTHER_USER}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.actions.RequestWithUserDetailsFromSessionAndMongo
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.{IncorrectUserType, UnexpectedResponseFromTaxEnrolments}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.forms.KeepAccessToSAThroughPTAForm.keepAccessToSAThroughPTAForm
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.TestData._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.{TestFixture, ThrottleHelperSpec, UrlPaths}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.forms.KeepAccessToSAThroughPTA
import uk.gov.hmrc.taxenrolmentassignmentfrontend.reporting.AuditEvent
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.SessionKeys.{USER_ASSIGNED_SA_ENROLMENT, accountDetailsForCredential}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.views.html.KeepAccessToSA

import scala.concurrent.{ExecutionContext, Future}

class KeepAccessToSAControllerSpec extends TestFixture with ThrottleHelperSpec {

  val view: KeepAccessToSA = app.injector.instanceOf[KeepAccessToSA]

  val controller = new KeepAccessToSAController(
    mockAuthAction,
    mockAccountMongoDetailsAction,
    mockThrottleAction,
    mockMultipleAccountsOrchestrator,
    mcc,
    logger,
    view,
    mockAuditHandler,
    errorHandler
  )

  "view" when {

    specificThrottleTests(controller.view())

    "the user has multiple accounts, is signed in with one with SA and has no form data in cache" should {
      "render the keep access to sa page with radio buttons unchecked" in {
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
          .getDetailsForKeepAccessToSA(
            _: RequestWithUserDetailsFromSessionAndMongo[_],
            _: HeaderCarrier,
            _: ExecutionContext
          ))
          .expects(*, *, *)
          .returning(createInboundResult(keepAccessToSAThroughPTAForm))
        mockGetDataFromCacheForActionSuccess(randomAccountType)
        mockAccountShouldNotBeThrottled(randomAccountType, NINO, saEnrolmentOnly.enrolments)

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
              ] ~ Option[AffinityGroup]
            ]
          )(_: HeaderCarrier, _: ExecutionContext))
          .expects(predicates, retrievals, *, *)
          .returning(
            Future.successful(retrievalResponse(enrolments = saEnrolmentOnly))
          )

        (mockMultipleAccountsOrchestrator
          .getDetailsForKeepAccessToSA(
            _: RequestWithUserDetailsFromSessionAndMongo[_],
            _: HeaderCarrier,
            _: ExecutionContext
          ))
          .expects(*, *, *)
          .returning(
            createInboundResult(
              keepAccessToSAThroughPTAForm.fill(KeepAccessToSAThroughPTA(true))
            )
          )
        mockGetDataFromCacheForActionSuccess(randomAccountType)
        mockAccountShouldNotBeThrottled(randomAccountType, NINO, saEnrolmentOnly.enrolments)

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
              ] ~ Option[AffinityGroup]
            ]
          )(_: HeaderCarrier, _: ExecutionContext))
          .expects(predicates, retrievals, *, *)
          .returning(
            Future.successful(retrievalResponse(enrolments = saEnrolmentOnly))
          )

        (mockMultipleAccountsOrchestrator
          .getDetailsForKeepAccessToSA(
            _: RequestWithUserDetailsFromSessionAndMongo[_],
            _: HeaderCarrier,
            _: ExecutionContext
          ))
          .expects(*, *, *)
          .returning(
            createInboundResult(
              keepAccessToSAThroughPTAForm.fill(KeepAccessToSAThroughPTA(false))
            )
          )
        mockGetDataFromCacheForActionSuccess(randomAccountType)
        mockAccountShouldNotBeThrottled(randomAccountType, NINO, saEnrolmentOnly.enrolments)

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

    "the user is not a multiple accounts usertype" should {
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
          .getDetailsForKeepAccessToSA(
            _: RequestWithUserDetailsFromSessionAndMongo[_],
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
    "the user ha no redirectUrl stored in session" should {
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
        contentAsString(res) should include("enrolmentError.title")
      }
    }

    "continue" when {

      specificThrottleTests(controller.continue())

      "the user has selected Yes" should {
        s"redirect to ${UrlPaths.saOnOtherAccountSigninAgainPath}" when {
          "they have SA on another account" in {
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
              .handleKeepAccessToSAChoice(_: KeepAccessToSAThroughPTA)(
                _: RequestWithUserDetailsFromSessionAndMongo[_],
                _: HeaderCarrier,
                _: ExecutionContext
              ))
              .expects(KeepAccessToSAThroughPTA(true), *, *, *)
              .returning(createInboundResult(true))
            mockGetDataFromCacheForActionSuccess(randomAccountType)
            mockAccountShouldNotBeThrottled(randomAccountType, NINO, noEnrolments.enrolments)

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
                  ] ~ Option[AffinityGroup]
                ]
              )(_: HeaderCarrier, _: ExecutionContext))
              .expects(predicates, retrievals, *, *)
              .returning(Future.successful(retrievalResponse()))
            (mockMultipleAccountsOrchestrator
              .handleKeepAccessToSAChoice(_: KeepAccessToSAThroughPTA)(
                _: RequestWithUserDetailsFromSessionAndMongo[_],
                _: HeaderCarrier,
                _: ExecutionContext
              ))
              .expects(KeepAccessToSAThroughPTA(true), *, *, *)
              .returning(
                createInboundResultError(
                  IncorrectUserType(UrlPaths.returnUrl, randomAccountType)
                )
              )
            mockGetDataFromCacheForActionSuccess(randomAccountType)
            mockAccountShouldNotBeThrottled(randomAccountType, NINO, noEnrolments.enrolments)

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
      }

      "the user has selected No" should {
        s"be enrolled for PT and redirect to ${UrlPaths.enrolledPTSAOnOtherAccountPath}" when {
          "they have SA on another account" in {
            val additionalCacheData = Map(USER_ASSIGNED_SA_ENROLMENT -> Json.toJson(UsersAssignedEnrolment1),
              accountDetailsForCredential(CREDENTIAL_ID_1) -> Json.toJson(accountDetails))
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
              .handleKeepAccessToSAChoice(_: KeepAccessToSAThroughPTA)(
                _: RequestWithUserDetailsFromSessionAndMongo[_],
                _: HeaderCarrier,
                _: ExecutionContext
              ))
              .expects(KeepAccessToSAThroughPTA(false), *, *, *)
              .returning(createInboundResult(false))
            mockGetDataFromCacheForActionSuccess(SA_ASSIGNED_TO_OTHER_USER, UrlPaths.returnUrl, additionalCacheData)
            mockAccountShouldNotBeThrottled(SA_ASSIGNED_TO_OTHER_USER, NINO, noEnrolments.enrolments)
            val auditEvent = AuditEvent.auditSuccessfullyEnrolledPersonalTax(false
            )(requestWithAccountType(SA_ASSIGNED_TO_OTHER_USER, UrlPaths.returnUrl, additionalCacheData = additionalCacheData))
            (mockAuditHandler
              .audit(_: AuditEvent)(_: HeaderCarrier))
              .expects(auditEvent, *)
              .returning(Future.successful((): Unit))
              .once()

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
                  ] ~ Option[AffinityGroup]
                ]
              )(_: HeaderCarrier, _: ExecutionContext))
              .expects(predicates, retrievals, *, *)
              .returning(Future.successful(retrievalResponse()))
            (mockMultipleAccountsOrchestrator
              .handleKeepAccessToSAChoice(_: KeepAccessToSAThroughPTA)(
                _: RequestWithUserDetailsFromSessionAndMongo[_],
                _: HeaderCarrier,
                _: ExecutionContext
              ))
              .expects(KeepAccessToSAThroughPTA(false), *, *, *)
              .returning(
                createInboundResultError(
                  IncorrectUserType(UrlPaths.returnUrl, randomAccountType)
                )
              )
            mockGetDataFromCacheForActionSuccess(randomAccountType)
            mockAccountShouldNotBeThrottled(randomAccountType, NINO, noEnrolments.enrolments)

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
          "enrolling for PT fails" in {
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
              .handleKeepAccessToSAChoice(_: KeepAccessToSAThroughPTA)(
                _: RequestWithUserDetailsFromSessionAndMongo[_],
                _: HeaderCarrier,
                _: ExecutionContext
              ))
              .expects(KeepAccessToSAThroughPTA(false), *, *, *)
              .returning(
                createInboundResultError(UnexpectedResponseFromTaxEnrolments)
              )
            mockGetDataFromCacheForActionSuccess(randomAccountType)
            mockAccountShouldNotBeThrottled(randomAccountType, NINO, noEnrolments.enrolments)

            val res = controller.continue
              .apply(
                buildFakePOSTRequestWithSessionId(
                  data = Map("select-continue" -> "no")
                )
              )

            status(res) shouldBe INTERNAL_SERVER_ERROR
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
                ] ~ Option[AffinityGroup]
              ]
            )(_: HeaderCarrier, _: ExecutionContext))
            .expects(predicates, retrievals, *, *)
            .returning(Future.successful(retrievalResponse()))
          mockGetDataFromCacheForActionSuccess(randomAccountType)
          mockAccountShouldNotBeThrottled(randomAccountType, NINO, noEnrolments.enrolments)

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
}
