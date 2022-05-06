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

package uk.gov.hmrc.taxenrolmentassignmentfrontend.services

import cats.data.EitherT
import cats.implicits._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.service.TEAFResult
import uk.gov.hmrc.taxenrolmentassignmentfrontend.connectors.EACDConnector
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.actions.RequestWithUserDetailsFromSession
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.TaxEnrolmentAssignmentErrors
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.UsersAssignedEnrolment
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.SessionKeys.{USER_ASSIGNED_PT_ENROLMENT, USER_ASSIGNED_SA_ENROLMENT}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.TEASessionCache

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class EACDService @Inject()(eacdConnector: EACDConnector,
                            sessionCache: TEASessionCache) {

  def getUsersAssignedPTEnrolment(
    implicit requestWithUserDetails: RequestWithUserDetailsFromSession[_],
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): TEAFResult[UsersAssignedEnrolment] =
    EitherT {
      sessionCache
        .getEntry[UsersAssignedEnrolment](USER_ASSIGNED_PT_ENROLMENT)
        .flatMap {
          case Some(record) => Future.successful(Right(record))
          case None         => getUsersWithPTEnrolmentFromEACD.value
        }
    }

  def getUsersAssignedSAEnrolment(
    implicit requestWithUserDetails: RequestWithUserDetailsFromSession[_],
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): TEAFResult[UsersAssignedEnrolment] =
    EitherT {
      sessionCache
        .getEntry[UsersAssignedEnrolment](USER_ASSIGNED_SA_ENROLMENT)
        .flatMap {
          case Some(record) => Future.successful(Right(record))
          case None         => getUsersWithSAEnrolmentFromEACD.value
        }
    }

  private def getUsersWithPTEnrolmentFromEACD(
    implicit requestWithUserDetails: RequestWithUserDetailsFromSession[_],
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): TEAFResult[UsersAssignedEnrolment] = {
    for {
      userWithPTEnrolment <- eacdConnector
        .getUsersWithPTEnrolment(requestWithUserDetails.userDetails.nino)
      _ <- EitherT.right[TaxEnrolmentAssignmentErrors](
        sessionCache
          .save[UsersAssignedEnrolment](
            USER_ASSIGNED_PT_ENROLMENT,
            userWithPTEnrolment
          )
      )
    } yield userWithPTEnrolment
  }

  private def getUsersWithSAEnrolmentFromEACD(
    implicit requestWithUserDetails: RequestWithUserDetailsFromSession[_],
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): TEAFResult[UsersAssignedEnrolment] = {

    for {
      optUTR <- eacdConnector.queryKnownFactsByNinoVerifier(
        requestWithUserDetails.userDetails.nino
      )
      usersWithSAEnrolment <- optUTR match {
        case Some(utr) => eacdConnector.getUsersWithSAEnrolment(utr)
        case None =>
          EitherT.right[TaxEnrolmentAssignmentErrors](
            Future.successful(UsersAssignedEnrolment(None))
          )
      }
      _ <- EitherT.right[TaxEnrolmentAssignmentErrors](
        sessionCache.save[UsersAssignedEnrolment](
          USER_ASSIGNED_SA_ENROLMENT,
          usersWithSAEnrolment
        )
      )
    } yield {
      usersWithSAEnrolment
    }
  }

}
