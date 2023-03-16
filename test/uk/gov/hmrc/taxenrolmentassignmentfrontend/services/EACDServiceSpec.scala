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

package uk.gov.hmrc.taxenrolmentassignmentfrontend.services

import cats.data.EitherT
import org.mockito.ArgumentMatchers.any
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import play.api.{Application, Configuration}
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Format
import play.api.mvc.{AnyContent, Request}
import play.api.test.FakeRequest
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.service.TEAFResult
import uk.gov.hmrc.taxenrolmentassignmentfrontend.connectors.EACDConnector
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.actions.RequestWithUserDetailsFromSession
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.{TaxEnrolmentAssignmentErrors, UnexpectedResponseFromEACD}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.TestData._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.{BaseSpec, TestFixture}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.UsersAssignedEnrolment
import uk.gov.hmrc.taxenrolmentassignmentfrontend.orchestrators.AccountCheckOrchestrator
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.SessionKeys.{USER_ASSIGNED_PT_ENROLMENT, USER_ASSIGNED_SA_ENROLMENT}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.TEASessionCache

import scala.concurrent.{ExecutionContext, Future}

class EACDServiceSpec extends BaseSpec {

  lazy val mockTeaSessionCache: TEASessionCache = mock[TEASessionCache]
  lazy val mockEacdConnector: EACDConnector = mock[EACDConnector]

  override implicit lazy val app: Application = GuiceApplicationBuilder()
    .overrides(
      bind[EACDConnector].toInstance(mockEacdConnector),
      bind[TEASessionCache].toInstance(mockTeaSessionCache)
    )
    .build()

  val service = app.injector.instanceOf[EACDService]

  implicit val request: RequestWithUserDetailsFromSession[AnyContent] =
    new RequestWithUserDetailsFromSession[AnyContent](
      FakeRequest().asInstanceOf[Request[AnyContent]],
      userDetailsNoEnrolments,
      "sessionId"
    )

  def createInboundResultError[T](
                                   error: TaxEnrolmentAssignmentErrors
                                 ): TEAFResult[T] = EitherT.left(Future.successful(error))

  def createInboundResult[T](result: T): TEAFResult[T] =
    EitherT.right[TaxEnrolmentAssignmentErrors](Future.successful(result))

  "getUsersAssignedPTEnrolment" when {
    "the a PT enrolment has been assigned for the nino" should {
      "call the EACD, save to cache and return the account details" in {
        (mockEacdConnector
          .getUsersWithPTEnrolment(_: String)(
            _: ExecutionContext,
            _: HeaderCarrier
          ))
          .expects(NINO, *, *)
          .returning(createInboundResult(UsersAssignedEnrolment1))
        /* when(mockEacdConnector.getUsersWithPTEnrolment(any())(any(), any()))
          .thenReturn(createInboundResult(UsersAssignedEnrolment1)) */

        (mockTeaSessionCache
          .save(_: String, _: UsersAssignedEnrolment)(
            _: RequestWithUserDetailsFromSession[AnyContent],
            _: Format[UsersAssignedEnrolment]
          ))
          .expects(USER_ASSIGNED_PT_ENROLMENT, UsersAssignedEnrolment1, *, *)
          .returning(Future(CacheMap(request.sessionID, Map())))
        /* when(mockTeaSessionCache.save(any(), any())(any(), any()))
          .thenReturn(Future.successful(CacheMap(request.sessionID, Map()))) */

        val result = service.getUsersAssignedPTEnrolment
        whenReady(result.value) { res =>
          res shouldBe Right(UsersAssignedEnrolment1)
        }
      }
    }

    "EACD returns an error" should {
      "return an error" in {
        (mockEacdConnector
          .getUsersWithPTEnrolment(_: String)(
            _: ExecutionContext,
            _: HeaderCarrier
          ))
          .expects(NINO, *, *)
          .returning(createInboundResultError(UnexpectedResponseFromEACD))
        /* when(mockEacdConnector.getUsersWithPTEnrolment(any())(any(), any()))
          .thenReturn(createInboundResultError(UnexpectedResponseFromEACD)) */

        val result = service.getUsersAssignedPTEnrolment
        whenReady(result.value) { res =>
          res shouldBe Left(UnexpectedResponseFromEACD)
        }
      }
    }
  }

  "getUsersAssignedSAEnrolment" when {

    "the there is a user assigned SA enrolment" should {
      "call the EACD twice to get the UTR and then enrolled credentials, save to cache and return the credentialId" in {

        (mockEacdConnector
          .queryKnownFactsByNinoVerifier(_: String)(
            _: ExecutionContext,
            _: HeaderCarrier
          ))
          .expects(NINO, *, *)
          .returning(createInboundResult(Some(UTR)))
        /* when(mockEacdConnector.queryKnownFactsByNinoVerifier(any())(any(), any()))
          .thenReturn(createInboundResult(Some(UTR))) */

        (mockEacdConnector
          .getUsersWithSAEnrolment(_: String)(
            _: ExecutionContext,
            _: HeaderCarrier
          ))
          .expects(UTR, *, *)
          .returning(createInboundResult(UsersAssignedEnrolment1))
        /* when(mockEacdConnector.getUsersWithSAEnrolment(any())(any(), any()))
          .thenReturn(createInboundResult(UsersAssignedEnrolment1)) */

        (mockTeaSessionCache
          .save(_: String, _: UsersAssignedEnrolment)(
            _: RequestWithUserDetailsFromSession[AnyContent],
            _: Format[UsersAssignedEnrolment]
          ))
          .expects(USER_ASSIGNED_SA_ENROLMENT, UsersAssignedEnrolment1, *, *)
          .returning(Future(CacheMap(request.sessionID, Map())))
        /* when(mockTeaSessionCache.save(any(), any())(any(), any()))
          .thenReturn(Future.successful(CacheMap(request.sessionID, Map()))) */


        val result = service.getUsersAssignedSAEnrolment
        whenReady(result.value) { res =>
          res shouldBe Right(UsersAssignedEnrolment1)
        }
      }
    }

    "the user has no SA enrolments associated with their nino" should {
      "call EACD to get the UTR, then save no creds with enrolment to cache and return None" in {

        (mockEacdConnector
          .queryKnownFactsByNinoVerifier(_: String)(
            _: ExecutionContext,
            _: HeaderCarrier
          ))
          .expects(NINO, *, *)
          .returning(createInboundResult(None))
        /* when(mockEacdConnector.queryKnownFactsByNinoVerifier(any())(any(), any()))
          .thenReturn(createInboundResult(None)) */


        (mockTeaSessionCache
          .save(_: String, _: UsersAssignedEnrolment)(
            _: RequestWithUserDetailsFromSession[AnyContent],
            _: Format[UsersAssignedEnrolment]
          ))
          .expects(
            USER_ASSIGNED_SA_ENROLMENT,
            UsersAssignedEnrolmentEmpty,
            *,
            *
          )
          .returning(Future(CacheMap(request.sessionID, Map())))
        /* when(mockTeaSessionCache.save(any(), any())(any(), any()))
          .thenReturn(Future.successful(CacheMap(request.sessionID, Map()))) */

        val result = service.getUsersAssignedSAEnrolment
        whenReady(result.value) { res =>
          res shouldBe Right(UsersAssignedEnrolmentEmpty)
        }
      }
    }

    "EACD returns an error" should {
      "return an error" in {

        (mockEacdConnector
          .queryKnownFactsByNinoVerifier(_: String)(
            _: ExecutionContext,
            _: HeaderCarrier
          ))
          .expects(NINO, *, *)
          .returning(createInboundResultError(UnexpectedResponseFromEACD))
        /* when(mockEacdConnector.queryKnownFactsByNinoVerifier(any())(any(), any()))
          .thenReturn(createInboundResultError(UnexpectedResponseFromEACD)) */

        val result = service.getUsersAssignedSAEnrolment
        whenReady(result.value) { res =>
          res shouldBe Left(UnexpectedResponseFromEACD)
        }
      }
    }
  }
}
