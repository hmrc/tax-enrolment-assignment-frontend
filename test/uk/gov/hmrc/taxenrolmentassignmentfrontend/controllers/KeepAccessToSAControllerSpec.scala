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
import play.api.Application
import play.api.http.Status.OK
import play.api.inject.bind
import play.api.libs.json.Json
import play.api.mvc.BodyParsers
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve.{Credentials, Retrieval, ~}
import uk.gov.hmrc.auth.core.{AffinityGroup, AuthConnector, Enrolments}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes.SA_ASSIGNED_TO_OTHER_USER
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.actions.RequestWithUserDetailsFromSessionAndMongo
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.{IncorrectUserType, UnexpectedPTEnrolment, UnexpectedResponseFromTaxEnrolments}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.forms.KeepAccessToSAThroughPTAForm.keepAccessToSAThroughPTAForm
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.TestData._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.{ControllersBaseSpec, UrlPaths}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.forms.KeepAccessToSAThroughPTA
import uk.gov.hmrc.taxenrolmentassignmentfrontend.orchestrators.{AccountCheckOrchestrator, MultipleAccountsOrchestrator}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.reporting.{AuditEvent, AuditHandler}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.SessionKeys.{USER_ASSIGNED_SA_ENROLMENT, accountDetailsForCredential}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.TEASessionCache
import uk.gov.hmrc.taxenrolmentassignmentfrontend.services.{SilentAssignmentService}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.views.html.KeepAccessToSA

import scala.concurrent.{ExecutionContext, Future}

class KeepAccessToSAControllerSpec extends ControllersBaseSpec {

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

  lazy val controller = app.injector.instanceOf[KeepAccessToSAController]

  val view: KeepAccessToSA = app.injector.instanceOf[KeepAccessToSA]

  "view" when {
    "the user has multiple accounts, is signed in with one without SA and has no form data in cache" should {
      "render the keep access to sa page with radio buttons unchecked" in {
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
            Future.successful(retrievalResponse())
          )

        (mockMultipleAccountsOrchestrator
          .getDetailsForKeepAccessToSA(
            _: RequestWithUserDetailsFromSessionAndMongo[_],
            _: ExecutionContext
          ))
          .expects(*, *)
          .returning(createInboundResult(keepAccessToSAThroughPTAForm))
        mockGetDataFromCacheForActionSuccess(SA_ASSIGNED_TO_OTHER_USER)

        val result = controller.view
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(result) shouldBe OK
        val page = Jsoup
          .parse(contentAsString(result))

        page.getElementsByTag("h1").text() shouldBe messages("keepAccessToSA.heading")
        val radioInputs = page.getElementsByClass("govuk-radios__input")
        radioInputs.size() shouldBe 2
        radioInputs.get(0).attr("value") shouldBe "yes"
        radioInputs.get(0).hasAttr("checked") shouldBe false
        radioInputs.get(1).attr("value") shouldBe "no"
        radioInputs.get(1).hasAttr("checked") shouldBe false
      }
    }

    "the user has multiple accounts, is signed in with one without SA and has previously selected Yes" should {
      "render the keep access to sa page with Yes checked" in {
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
            Future.successful(retrievalResponse())
          )

        (mockMultipleAccountsOrchestrator
          .getDetailsForKeepAccessToSA(
            _: RequestWithUserDetailsFromSessionAndMongo[_],
            _: ExecutionContext
          ))
          .expects(*, *)
          .returning(
            createInboundResult(
              keepAccessToSAThroughPTAForm.fill(KeepAccessToSAThroughPTA(true))
            )
          )
        mockGetDataFromCacheForActionSuccess(SA_ASSIGNED_TO_OTHER_USER)

        val result = controller.view
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(result) shouldBe OK
        val page = Jsoup
          .parse(contentAsString(result))

        page.getElementsByTag("h1").text() shouldBe messages("keepAccessToSA.heading")
        val radioInputs = page.getElementsByClass("govuk-radios__input")
        radioInputs.size() shouldBe 2
        radioInputs.get(0).attr("value") shouldBe "yes"
        radioInputs.get(0).hasAttr("checked") shouldBe true
        radioInputs.get(1).attr("value") shouldBe "no"
        radioInputs.get(1).hasAttr("checked") shouldBe false
      }
    }

    "the user has multiple accounts including one with SA, is signed in with one without SA and has previously selected No" should {
      "render the keep access to sa page with No checked" in {
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
            Future.successful(retrievalResponse())
          )

        (mockMultipleAccountsOrchestrator
          .getDetailsForKeepAccessToSA(
            _: RequestWithUserDetailsFromSessionAndMongo[_],
            _: ExecutionContext
          ))
          .expects(*, *)
          .returning(
            createInboundResult(
              keepAccessToSAThroughPTAForm.fill(KeepAccessToSAThroughPTA(false))
            )
          )
        mockGetDataFromCacheForActionSuccess(SA_ASSIGNED_TO_OTHER_USER)

        val result = controller.view
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(result) shouldBe OK
        val page = Jsoup
          .parse(contentAsString(result))

        page.getElementsByTag("h1").text() shouldBe messages("keepAccessToSA.heading")
        val radioInputs = page.getElementsByClass("govuk-radios__input")
        radioInputs.size() shouldBe 2
        radioInputs.get(0).attr("value") shouldBe "yes"
        radioInputs.get(0).hasAttr("checked") shouldBe false
        radioInputs.get(1).attr("value") shouldBe "no"
        radioInputs.get(1).hasAttr("checked") shouldBe true
      }
    }

    "the user has multiple accounts, is signed in with one without SA and has already been enrolled for PT" should {
      s"redirect to ${UrlPaths.enrolledPTSAOnOtherAccountPath}" in {
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
            Future.successful(retrievalResponse(enrolments = ptEnrolmentOnly))
          )

        (mockMultipleAccountsOrchestrator
          .getDetailsForKeepAccessToSA(
            _: RequestWithUserDetailsFromSessionAndMongo[_],
            _: ExecutionContext
          ))
          .expects(*, *)
          .returning(createInboundResultError(UnexpectedPTEnrolment(SA_ASSIGNED_TO_OTHER_USER)))
        mockGetDataFromCacheForActionSuccess(SA_ASSIGNED_TO_OTHER_USER)

        val result = controller.view
          .apply(buildFakeRequestWithSessionId("GET", "Not Used"))

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(UrlPaths.enrolledPTSAOnOtherAccountPath)
      }
    }

    "the user does not have SA on another account" should {
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

        (mockMultipleAccountsOrchestrator
          .getDetailsForKeepAccessToSA(
            _: RequestWithUserDetailsFromSessionAndMongo[_],
            _: ExecutionContext
          ))
          .expects(*, *)
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
    "the user has no redirectUrl stored in session" should {
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

  "continue" when {
    "the user has selected Yes" should {
      s"redirect to ${UrlPaths.saOnOtherAccountSigninAgainPath}" when {
        "they have SA on another account" in {
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
              .handleKeepAccessToSAChoice(_: KeepAccessToSAThroughPTA)(
                _: RequestWithUserDetailsFromSessionAndMongo[_],
                _: HeaderCarrier,
                _: ExecutionContext
              )
            )
            .expects(KeepAccessToSAThroughPTA(true), *, *, *)
            .returning(createInboundResult(true))
          mockGetDataFromCacheForActionSuccess(SA_ASSIGNED_TO_OTHER_USER)
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

      s"redirect to ${UrlPaths.enrolledPTSAOnOtherAccountPath}" when {
        "they have SA on another account" in {
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
            .returning(Future.successful(retrievalResponse(enrolments = ptEnrolmentOnly)))

          (
            mockMultipleAccountsOrchestrator
              .handleKeepAccessToSAChoice(_: KeepAccessToSAThroughPTA)(
                _: RequestWithUserDetailsFromSessionAndMongo[_],
                _: HeaderCarrier,
                _: ExecutionContext
              )
            )
            .expects(KeepAccessToSAThroughPTA(true), *, *, *)
            .returning(createInboundResultError(UnexpectedPTEnrolment(SA_ASSIGNED_TO_OTHER_USER)))
          mockGetDataFromCacheForActionSuccess(SA_ASSIGNED_TO_OTHER_USER)

          val res = controller.continue
            .apply(
              buildFakePOSTRequestWithSessionId(
                data = Map("select-continue" -> "yes")
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
              .handleKeepAccessToSAChoice(_: KeepAccessToSAThroughPTA)(
                _: RequestWithUserDetailsFromSessionAndMongo[_],
                _: HeaderCarrier,
                _: ExecutionContext
              )
            )
            .expects(KeepAccessToSAThroughPTA(true), *, *, *)
            .returning(
              createInboundResultError(
                IncorrectUserType(UrlPaths.returnUrl, randomAccountType)
              )
            )
          mockGetDataFromCacheForActionSuccess(randomAccountType)

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
          val additionalCacheData = Map(
            USER_ASSIGNED_SA_ENROLMENT                   -> Json.toJson(UsersAssignedEnrolment1),
            accountDetailsForCredential(CREDENTIAL_ID_1) -> Json.toJson(accountDetails)
          )
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
              .handleKeepAccessToSAChoice(_: KeepAccessToSAThroughPTA)(
                _: RequestWithUserDetailsFromSessionAndMongo[_],
                _: HeaderCarrier,
                _: ExecutionContext
              )
            )
            .expects(KeepAccessToSAThroughPTA(false), *, *, *)
            .returning(createInboundResult(false))
          mockGetDataFromCacheForActionSuccess(SA_ASSIGNED_TO_OTHER_USER, UrlPaths.returnUrl, additionalCacheData)

          val auditEvent = AuditEvent.auditSuccessfullyEnrolledPTWhenSAOnOtherAccount(false)(
            requestWithAccountType(
              SA_ASSIGNED_TO_OTHER_USER,
              UrlPaths.returnUrl,
              additionalCacheData = additionalCacheData
            ),
            messagesApi
          )
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

      s"not be enrolled for PT and redirect to ${UrlPaths.enrolledPTSAOnOtherAccountPath}" when {
        "they have already been assigned PT enrolment" in {
          val additionalCacheData = Map(
            USER_ASSIGNED_SA_ENROLMENT                   -> Json.toJson(UsersAssignedEnrolment1),
            accountDetailsForCredential(CREDENTIAL_ID_1) -> Json.toJson(accountDetails)
          )
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
            .returning(Future.successful(retrievalResponse(enrolments = ptEnrolmentOnly)))
          (
            mockMultipleAccountsOrchestrator
              .handleKeepAccessToSAChoice(_: KeepAccessToSAThroughPTA)(
                _: RequestWithUserDetailsFromSessionAndMongo[_],
                _: HeaderCarrier,
                _: ExecutionContext
              )
            )
            .expects(KeepAccessToSAThroughPTA(false), *, *, *)
            .returning(createInboundResultError(UnexpectedPTEnrolment(SA_ASSIGNED_TO_OTHER_USER)))
          mockGetDataFromCacheForActionSuccess(SA_ASSIGNED_TO_OTHER_USER, UrlPaths.returnUrl, additionalCacheData)

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
              .handleKeepAccessToSAChoice(_: KeepAccessToSAThroughPTA)(
                _: RequestWithUserDetailsFromSessionAndMongo[_],
                _: HeaderCarrier,
                _: ExecutionContext
              )
            )
            .expects(KeepAccessToSAThroughPTA(false), *, *, *)
            .returning(
              createInboundResultError(
                IncorrectUserType(UrlPaths.returnUrl, randomAccountType)
              )
            )
          mockGetDataFromCacheForActionSuccess(randomAccountType)

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
              .handleKeepAccessToSAChoice(_: KeepAccessToSAThroughPTA)(
                _: RequestWithUserDetailsFromSessionAndMongo[_],
                _: HeaderCarrier,
                _: ExecutionContext
              )
            )
            .expects(KeepAccessToSAThroughPTA(false), *, *, *)
            .returning(
              createInboundResultError(UnexpectedResponseFromTaxEnrolments)
            )
          mockGetDataFromCacheForActionSuccess(randomAccountType)

          val res = controller.continue
            .apply(
              buildFakePOSTRequestWithSessionId(
                data = Map("select-continue" -> "no")
              )
            )

          status(res) shouldBe INTERNAL_SERVER_ERROR
          contentAsString(res) should include(messages("enrolmentError.heading"))
        }
      }
    }
    "a form error occurs" should {
      "render the keepAccessToSA page with error summary" in {
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
        mockGetDataFromCacheForActionSuccess(randomAccountType)

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
          .text() shouldBe messages("validation.summary.heading")
        page
          .getElementsByClass("govuk-list govuk-error-summary__list")
          .first()
          .text() shouldBe messages("keepAccessToSA.error.required")
      }
    }
  }
}
