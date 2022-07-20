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
import uk.gov.hmrc.taxenrolmentassignmentfrontend.connectors.{EACDConnector, IVConnector, TaxEnrolmentsConnector}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.actions.RequestWithUserDetailsFromSession
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.TaxEnrolmentAssignmentErrors
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.IVNinoStoreEntry
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.IVNinoStoreEntry._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.enums.EnrolmentEnum.saEnrolmentSet
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.SessionKeys.{HAS_OTHER_VALID_PTA_ACCOUNTS}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.TEASessionCache

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class SilentAssignmentService @Inject()(
  ivConnector: IVConnector,
  taxEnrolmentsConnector: TaxEnrolmentsConnector,
  eacdConnector: EACDConnector,
  sessionCache: TEASessionCache
) {


  private def filterCL200Accounts(
    list: Seq[IVNinoStoreEntry]
  ): Seq[IVNinoStoreEntry] =
    list.filter(_.confidenceLevel.exists(_ >= 200))

  def enrolUser()(implicit request: RequestWithUserDetailsFromSession[_],
                  hc: HeaderCarrier,
                  ec: ExecutionContext): TEAFResult[Unit] = {
    val details = request.userDetails
    taxEnrolmentsConnector.assignPTEnrolmentWithKnownFacts(details.nino)
  }

  def hasOtherAccountsWithPTAAccess(
    implicit requestWithUserDetails: RequestWithUserDetailsFromSession[_],
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): TEAFResult[Boolean] = {
    for {
      allCreds <- ivConnector.getCredentialsWithNino(
        requestWithUserDetails.userDetails.nino
      )
      hasOtherValidPTACreds <- EitherT.right[TaxEnrolmentAssignmentErrors](
        hasOtherNoneBusinessAccounts(
          allCreds,
          requestWithUserDetails.userDetails.credId
        )
      )
      _ <- EitherT.right[TaxEnrolmentAssignmentErrors](
        sessionCache.save[Boolean](
          HAS_OTHER_VALID_PTA_ACCOUNTS,
          hasOtherValidPTACreds
        )
      )
    } yield {
      hasOtherValidPTACreds
    }
  }

  private def hasOtherNoneBusinessAccounts(list: Seq[IVNinoStoreEntry],
                                           currentCredId: String)(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Boolean] = {
    val otherCreds = list.filterNot(_.credId == currentCredId)
    lazy val filteredCL200List: Seq[IVNinoStoreEntry] = filterCL200Accounts(
      otherCreds
    )
    checkIfAnyOtherNoneBusinessAccounts(filteredCL200List)
  }

  private def checkIfAnyOtherNoneBusinessAccounts(list: Seq[IVNinoStoreEntry], attemptsRemaining: Int = 10)
                                                 (implicit hc: HeaderCarrier,
                                                  ec: ExecutionContext): Future[Boolean] = {
    if(attemptsRemaining == 0) {
      Future.successful(false)
    } else list match {
      case ivStoreEntry :: tail if tail.isEmpty => isNotBusinessAccount(ivStoreEntry)
      case ivStoreEntry :: tail => isNotBusinessAccount(ivStoreEntry).flatMap{
        case true => Future.successful(true)
        case false => checkIfAnyOtherNoneBusinessAccounts(tail, attemptsRemaining - 1)
      }
      case Nil =>  Future.successful(false)
    }
  }

  private def isNotBusinessAccount(
    ninoEntry: IVNinoStoreEntry
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Boolean] = {
    eacdConnector
      .queryEnrolmentsAssignedToUser(ninoEntry.credId)
      .value
      .map {
        case Right(Some(enrolmentsList)) =>
          enrolmentsList.enrolments
            .map(_.service)
            .forall(e => saEnrolmentSet(e))
        case Right(_) => true
        case _        => false
      }
  }

}
