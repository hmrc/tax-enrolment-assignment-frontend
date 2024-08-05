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
import cats.implicits._
import play.api.mvc.AnyContent
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.service.TEAFResult
import uk.gov.hmrc.taxenrolmentassignmentfrontend.connectors.EACDConnector
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.actions.DataRequest
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.TaxEnrolmentAssignmentErrors
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.UsersAssignedEnrolment
import uk.gov.hmrc.taxenrolmentassignmentfrontend.pages.{UserAssignedPtaEnrolmentPage, UserAssignedSaEnrolmentPage}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.JourneyCacheRepository

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class EACDService @Inject() (eacdConnector: EACDConnector, journeyCacheRepository: JourneyCacheRepository) {

  def getUsersAssignedPTEnrolment(implicit
    request: DataRequest[AnyContent],
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): TEAFResult[UsersAssignedEnrolment] =
    for {
      userWithPTEnrolment <- eacdConnector
                               .getUsersWithPTEnrolment(request.userDetails.nino)
      _ <- EitherT.right[TaxEnrolmentAssignmentErrors](
             journeyCacheRepository.set(
               request.userAnswers.setOrException(UserAssignedPtaEnrolmentPage, userWithPTEnrolment)
             )
           )
    } yield userWithPTEnrolment

  def getUsersAssignedSAEnrolment(implicit
    request: DataRequest[_],
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): TEAFResult[UsersAssignedEnrolment] =
    for {
      optUTR <- eacdConnector.queryKnownFactsByNinoVerifier(
                  request.userDetails.nino
                )
      usersWithSAEnrolment <- optUTR match {
                                case Some(utr) => eacdConnector.getUsersWithSAEnrolment(utr)
                                case None =>
                                  EitherT.right[TaxEnrolmentAssignmentErrors](
                                    Future.successful(UsersAssignedEnrolment(None))
                                  )
                              }
      _ <- EitherT.right[TaxEnrolmentAssignmentErrors](
             journeyCacheRepository.set(
               request.userAnswers.setOrException(UserAssignedSaEnrolmentPage, usersWithSAEnrolment)
             )
           )
    } yield usersWithSAEnrolment
}
