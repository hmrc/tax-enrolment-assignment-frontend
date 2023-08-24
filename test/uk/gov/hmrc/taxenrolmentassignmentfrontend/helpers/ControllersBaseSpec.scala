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

package uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers

import org.scalamock.handlers.{CallHandler1, CallHandler5}
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{Action, AnyContent}
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve.{Credentials, Retrieval, ~}
import uk.gov.hmrc.auth.core.{AffinityGroup, AuthConnector, Enrolment, Enrolments}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.service.TEAFResult
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.actions.RequestWithUserDetailsFromSession
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.UnexpectedError
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.TestData._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.SessionKeys.ACCOUNT_TYPE
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.TEASessionCache
import uk.gov.hmrc.taxenrolmentassignmentfrontend.services.{ThrottleApplied, ThrottleDoesNotApply, ThrottleResult, ThrottlingService}

import scala.concurrent.{ExecutionContext, Future}

trait ControllersBaseSpec extends BaseSpec {

  lazy val mockAuthConnector = mock[AuthConnector]
  lazy val mockThrottlingService = mock[ThrottlingService]
  lazy val mockTeaSessionCache = mock[TEASessionCache]

  def mockGetDataFromCacheForActionNoRedirectUrl = {
    val data = Map(ACCOUNT_TYPE -> Json.toJson(randomAccountType))
    val cacheMap = CacheMap("id", data)
    (mockTeaSessionCache
      .fetch()(
        _: RequestWithUserDetailsFromSession[_]
      ))
      .expects(*)
      .returning(Future.successful(Some(cacheMap)))
  }

  def mockErrorFromThrottlingService(accountTypes: AccountTypes.Value, nino: String, enrolments: Set[Enrolment]) =
    (mockThrottlingService
      .throttle(_: AccountTypes.Value, _: String, _: Set[Enrolment])(_: ExecutionContext, _: HeaderCarrier))
      .expects(
        accountTypes,
        nino,
        enrolments,
        *,
        *
      )
      .returning(createInboundResultError(UnexpectedError))
      .once()

  def mockGetDataFromCacheForActionSuccess(
    accountType: AccountTypes.Value,
    redirectUrl: String = "foo",
    additionCacheData: Map[String, JsValue] = Map()
  ) = {
    val data = generateBasicCacheData(accountType, redirectUrl) ++ additionCacheData
    val cacheMap = CacheMap("id", data)
    (mockTeaSessionCache
      .fetch()(
        _: RequestWithUserDetailsFromSession[_]
      ))
      .expects(*)
      .returning(Future.successful(Some(cacheMap)))
  }

  def mockAccountShouldBeThrottled(
    accountTypes: AccountTypes.Value,
    nino: String,
    enrolments: Set[Enrolment]
  ): CallHandler5[AccountTypes.Value, String, Set[Enrolment], ExecutionContext, HeaderCarrier, TEAFResult[
    ThrottleResult
  ]] =
    (mockThrottlingService
      .throttle(_: AccountTypes.Value, _: String, _: Set[Enrolment])(_: ExecutionContext, _: HeaderCarrier))
      .expects(
        accountTypes,
        nino,
        enrolments,
        *,
        *
      )
      .returning(createInboundResult(ThrottleApplied))
      .once()

  def mockDeleteDataFromCache: CallHandler1[RequestWithUserDetailsFromSession[_], Future[Boolean]] =
    (mockTeaSessionCache
      .removeRecord(_: RequestWithUserDetailsFromSession[_]))
      .expects(*)
      .returning(Future.successful(true))
      .once()

  def mockAccountShouldNotBeThrottled(
    accountTypes: AccountTypes.Value,
    nino: String,
    enrolments: Set[Enrolment]
  ): CallHandler5[AccountTypes.Value, String, Set[Enrolment], ExecutionContext, HeaderCarrier, TEAFResult[
    ThrottleResult
  ]] =
    (mockThrottlingService
      .throttle(_: AccountTypes.Value, _: String, _: Set[Enrolment])(_: ExecutionContext, _: HeaderCarrier))
      .expects(
        accountTypes,
        nino,
        enrolments,
        *,
        *
      )
      .returning(createInboundResult(ThrottleDoesNotApply))
      .once()

  def specificThrottleTests(controllerFunction: Action[AnyContent]): Unit = {
    s"$ThrottleApplied" should {
      "redirect user to their RedirectURL" in {
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

        mockGetDataFromCacheForActionSuccess(accountType = randomAccountType, redirectUrl = "redirect")
        mockAccountShouldBeThrottled(randomAccountType, NINO, noEnrolments.enrolments)
        mockDeleteDataFromCache
        val res = controllerFunction.apply(buildFakeRequestWithSessionId("", ""))

        redirectLocation(res).get shouldBe "redirect"
        status(res) shouldBe SEE_OTHER
      }
    }
    s"Error from throttling service" should {
      s"return $INTERNAL_SERVER_ERROR" in {
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

        mockGetDataFromCacheForActionSuccess(accountType = randomAccountType, redirectUrl = "redirect")
        mockErrorFromThrottlingService(randomAccountType, NINO, noEnrolments.enrolments)

        val res = controllerFunction.apply(buildFakeRequestWithSessionId("", ""))

        status(res) shouldBe INTERNAL_SERVER_ERROR
      }
    }
  }
}
