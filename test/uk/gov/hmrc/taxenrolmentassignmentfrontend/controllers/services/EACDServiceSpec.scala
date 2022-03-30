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

package uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.services

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import play.api.libs.json.Format
import play.api.mvc.AnyContent
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.auth.RequestWithUserDetails
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.helpers.TestData._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.helpers.TestFixture
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.UnexpectedResponseFromEACD
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.UsersAssignedEnrolment
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.SessionKeys.USER_ASSIGNED_PT_ENROLMENT
import uk.gov.hmrc.taxenrolmentassignmentfrontend.services.EACDService

import scala.concurrent.{ExecutionContext, Future}

class EACDServiceSpec extends TestFixture with ScalaFutures {

  implicit val defaultPatience = PatienceConfig(
    timeout = Span(TIME_OUT, Seconds),
    interval = Span(INTERVAL, Millis)
  )

  val service = new EACDService(mockEacdConnector, mockTeaSessionCache)

  "getUsersAssignedPTEnrolment" when {
    "the user assigned PT enrolment are already in the cache" should {
      "not make a call the eacd and return value from cache" in {
        (mockTeaSessionCache
          .getEntry(_: String)(
            _: RequestWithUserDetails[AnyContent],
            _: Format[UsersAssignedEnrolment]
          ))
          .expects(USER_ASSIGNED_PT_ENROLMENT, *, *)
          .returning(Future.successful(Some(UsersAssignedEnrolment1)))
        val result = service.getUsersAssignedPTEnrolment
        whenReady(result.value) { res =>
          res shouldBe Right(UsersAssignedEnrolment1)
        }
      }
    }
    "the user assigned PT enrolment are not already in the cache" should {
      "call the EACD, save to cache and return the account details" in {
        (mockTeaSessionCache
          .getEntry(_: String)(
            _: RequestWithUserDetails[AnyContent],
            _: Format[UsersAssignedEnrolment]
          ))
          .expects(USER_ASSIGNED_PT_ENROLMENT, *, *)
          .returning(Future.successful(None))
        (mockEacdConnector
          .getUsersWithPTEnrolment(_: String)(
            _: ExecutionContext,
            _: HeaderCarrier
          ))
          .expects(NINO, *, *)
          .returning(createInboundResult(UsersAssignedEnrolment1))
        (mockTeaSessionCache
          .save(_: String, _: UsersAssignedEnrolment)(
            _: RequestWithUserDetails[AnyContent],
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

    "the user assigned PT enrolment are not already in the cache and users-group-search returns an error" should {
      "return an error" in {
        (mockTeaSessionCache
          .getEntry(_: String)(
            _: RequestWithUserDetails[AnyContent],
            _: Format[UsersAssignedEnrolment]
          ))
          .expects(USER_ASSIGNED_PT_ENROLMENT, *, *)
          .returning(Future.successful(None))
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
}
