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

<<<<<<< HEAD
import cats.data.EitherT
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.time.{Millis, Seconds, Span}
import play.api.http.Status
import play.api.http.Status.{BAD_REQUEST, NO_CONTENT}
=======
>>>>>>> main
import play.api.libs.json.Format
import play.api.mvc.AnyContent
import play.api.test.Helpers.status
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, UpstreamErrorResponse}
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.taxenrolmentassignmentfrontend.connectors.EACDConnector
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.actions.RequestWithUserDetailsFromSession
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.UnexpectedResponseFromEACD
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.TestData._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.BaseSpec
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.UsersAssignedEnrolment
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.SessionKeys.{USER_ASSIGNED_PT_ENROLMENT, USER_ASSIGNED_SA_ENROLMENT}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.TEASessionCache

import scala.concurrent.{ExecutionContext, Future}

class EACDServiceSpec extends BaseSpec {

  lazy val mockEacdConnector: EACDConnector = mock[EACDConnector]
  lazy val mockTeaSessionCache = mock[TEASessionCache]

  val service = new EACDService(mockEacdConnector, mockTeaSessionCache)

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
        (mockTeaSessionCache
          .save(_: String, _: UsersAssignedEnrolment)(
            _: RequestWithUserDetailsFromSession[AnyContent],
            _: Format[UsersAssignedEnrolment]
          ))
          .expects(USER_ASSIGNED_PT_ENROLMENT, UsersAssignedEnrolment1, *, *)
          .returning(Future(CacheMap(request.sessionID, Map())))
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
        (mockEacdConnector
          .getUsersWithSAEnrolment(_: String)(
            _: ExecutionContext,
            _: HeaderCarrier
          ))
          .expects(UTR, *, *)
          .returning(createInboundResult(UsersAssignedEnrolment1))
        (mockTeaSessionCache
          .save(_: String, _: UsersAssignedEnrolment)(
            _: RequestWithUserDetailsFromSession[AnyContent],
            _: Format[UsersAssignedEnrolment]
          ))
          .expects(USER_ASSIGNED_SA_ENROLMENT, UsersAssignedEnrolment1, *, *)
          .returning(Future(CacheMap(request.sessionID, Map())))
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
        val result = service.getUsersAssignedSAEnrolment
        whenReady(result.value) { res =>
          res shouldBe Left(UnexpectedResponseFromEACD)
        }
      }
    }
  }

  "deallocateEnrolment" when {
    "there is an existing HMRC-PT enrolment to delete" should {
      "return true if the request was successful" in {

        (mockEacdConnector
          .deallocateEnrolment(_: String, _: String)(
            _: HeaderCarrier,
            _: ExecutionContext
          ))
          .expects("testId", s"HMRC-PT~NINO~$NINO", *, *)
          .returning(EitherT[Future, UpstreamErrorResponse, HttpResponse](Future.successful(Right(HttpResponse(NO_CONTENT, "")))))

        val result = service.deallocateEnrolment("testId", s"HMRC-PT~NINO~$NINO")
          .value
          .futureValue
          .getOrElse(HttpResponse(BAD_REQUEST, ""))
        result.status shouldBe NO_CONTENT
      }

      List(
        Status.BAD_REQUEST,
        Status.NOT_FOUND,
        Status.INTERNAL_SERVER_ERROR,
        Status.BAD_GATEWAY,
        Status.SERVICE_UNAVAILABLE
      ).foreach { errorStatus =>
        s"return false if the request was unsuccessful due to a $errorStatus response" in {

          (mockEacdConnector
            .deallocateEnrolment(_: String, _: String)(
              _: HeaderCarrier,
              _: ExecutionContext
            ))
            .expects("testId", s"HMRC-PT~NINO~$NINO", *, *)
            .returning(EitherT[Future, UpstreamErrorResponse, HttpResponse](Future.successful(Left(UpstreamErrorResponse("", errorStatus)))))

          val result = service.deallocateEnrolment("testId", s"HMRC-PT~NINO~$NINO")
          result.value.futureValue shouldBe Left(UpstreamErrorResponse("", errorStatus))
        }
      }
    }
  }
}
