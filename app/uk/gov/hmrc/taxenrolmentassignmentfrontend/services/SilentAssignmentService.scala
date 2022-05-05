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
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.SessionKeys.OTHER_VALID_PTA_ACCOUNTS
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.TEASessionCache

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class SilentAssignmentService @Inject()(
  ivConnector: IVConnector,
  taxEnrolmentsConnector: TaxEnrolmentsConnector,
  eacdConnector: EACDConnector,
  sessionCache: TEASessionCache
) {

  private lazy val saEnrolmentSet: Set[String] =
    Set("IR-SA", "HMRC-MTD-IT", "HMRC-NI")

  private def filterCL200Accounts(
    list: Seq[IVNinoStoreEntry]
  ): Seq[IVNinoStoreEntry] =
    list.filter(_.confidenceLevel.exists(_ >= 200))

  def getOtherAccountsWithPTAAccess(
    implicit requestWithUserDetails: RequestWithUserDetailsFromSession[_],
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): TEAFResult[Seq[IVNinoStoreEntry]] =
    EitherT {
      sessionCache
        .getEntry[Seq[IVNinoStoreEntry]](OTHER_VALID_PTA_ACCOUNTS)
        .flatMap {
          case Some(otherCreds) => Future.successful(Right(otherCreds))
          case None             => getOtherAccountsValidForPTA.value
        }
    }

  def enrolUser()(implicit request: RequestWithUserDetailsFromSession[_],
                  hc: HeaderCarrier,
                  ec: ExecutionContext): TEAFResult[Unit] = {
    val details = request.userDetails
    taxEnrolmentsConnector.assignPTEnrolmentWithKnownFacts(details.nino)
  }

  private def getOtherAccountsValidForPTA(
    implicit requestWithUserDetails: RequestWithUserDetailsFromSession[_],
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): TEAFResult[Seq[IVNinoStoreEntry]] = {
    for {
      allCreds <- ivConnector.getCredentialsWithNino(
        requestWithUserDetails.userDetails.nino
      )
      otherValidPTACreds <- EitherT.right[TaxEnrolmentAssignmentErrors](
        getOtherNoneBusinessAccounts(
          allCreds,
          requestWithUserDetails.userDetails.credId
        )
      )
      _ <- EitherT.right[TaxEnrolmentAssignmentErrors](
        sessionCache.save[Seq[IVNinoStoreEntry]](
          OTHER_VALID_PTA_ACCOUNTS,
          otherValidPTACreds
        )
      )
    } yield {
      otherValidPTACreds
    }
  }

  private def getOtherNoneBusinessAccounts(list: Seq[IVNinoStoreEntry],
                                           currentCredId: String)(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Seq[IVNinoStoreEntry]] = {
    val otherCreds = list.filterNot(_.credId == currentCredId)
    lazy val filteredCL200List: Seq[IVNinoStoreEntry] = filterCL200Accounts(
      otherCreds
    )
    if (otherCreds.nonEmpty && filteredCL200List.nonEmpty && filteredCL200List.size < 10) {
      filterNoneBusinessAccounts(filteredCL200List)
    } else {
      Future.successful(Seq.empty[IVNinoStoreEntry])

    }
  }

  private def filterNoneBusinessAccounts(list: Seq[IVNinoStoreEntry])(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Seq[IVNinoStoreEntry]] = {

    val validPtaAccountList = list.map { ninoEntry =>
      isNotBusinessAccount(ninoEntry).map {
        case true  => Some(ninoEntry)
        case false => None
      }
    }

    Future.sequence(validPtaAccountList).map(_.flatten)
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
