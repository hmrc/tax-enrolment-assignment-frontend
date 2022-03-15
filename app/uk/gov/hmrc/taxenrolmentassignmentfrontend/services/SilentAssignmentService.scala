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
import javax.inject.Inject
import play.api.mvc.AnyContent
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.service.TEAFResult
import uk.gov.hmrc.taxenrolmentassignmentfrontend.connectors.{
  EACDConnector,
  IVConnector,
  TaxEnrolmentsConnector
}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.auth.RequestWithUserDetails
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.TaxEnrolmentAssignmentErrors
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.IVNinoStoreEntry
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.IVNinoStoreEntry._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.TEASessionCache

import scala.concurrent.{ExecutionContext, Future}

class SilentAssignmentService @Inject()(
  ivConnector: IVConnector,
  taxEnrolmentsConnector: TaxEnrolmentsConnector,
  eacdConnector: EACDConnector,
  sessionCache: TEASessionCache
) {

  private val saEnrolmentSet: Set[String] =
    Set("IR-SA", "HMRC-MTD-IT", "HMRC-NI")

  private def filterCL200Accounts(
    list: Seq[IVNinoStoreEntry]
  ): Seq[IVNinoStoreEntry] =
    list.filter(_.confidenceLevel.exists(_ >= 200))

  lazy val sessionKey = "OTHER_VALID_PTA_ACCOUNTS"

  def getOtherAccountsWithPTAAccess(
    implicit requestWithUserDetails: RequestWithUserDetails[AnyContent],
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): TEAFResult[Seq[IVNinoStoreEntry]] =
    EitherT {
      sessionCache.getEntry[Seq[IVNinoStoreEntry]](sessionKey).flatMap {
        case Some(otherCreds) => Future.successful(Right(otherCreds))
        case None             => getOtherAccountsValidForPTA.value
      }
    }

  def enrolUser()(implicit request: RequestWithUserDetails[AnyContent],
                  hc: HeaderCarrier,
                  ec: ExecutionContext): TEAFResult[Unit] = {
    val details = request.userDetails
    taxEnrolmentsConnector.assignPTEnrolment(
      details.groupId,
      details.credId,
      details.nino
    )
  }

  private def getOtherAccountsValidForPTA(
    implicit requestWithUserDetails: RequestWithUserDetails[AnyContent],
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): TEAFResult[Seq[IVNinoStoreEntry]] = {
    for {
      allCreds <- ivConnector.getCredentialsWithNino(
        requestWithUserDetails.userDetails.nino
      )
      otherCreds <- EitherT.right[TaxEnrolmentAssignmentErrors](
        Future.successful(
          filterCL200Accounts(
            allCreds
              .filterNot(_.credId == requestWithUserDetails.userDetails.credId)
          )
        )
      )
      otherValidPTACreds <- EitherT.right[TaxEnrolmentAssignmentErrors](
        getOtherValidPtaAccounts(otherCreds)
      )
    } yield {
      sessionCache.save[Seq[IVNinoStoreEntry]](sessionKey, otherValidPTACreds)
      otherValidPTACreds
    }
  }

  private def getOtherValidPtaAccounts(list: Seq[IVNinoStoreEntry])(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Seq[IVNinoStoreEntry]] = {

    val filteredCL200List: Seq[IVNinoStoreEntry] = filterCL200Accounts(list)

    val validPtaAccountList: Seq[Future[Option[IVNinoStoreEntry]]] =
      if (filteredCL200List.nonEmpty && filteredCL200List.size < 10) {
        filteredCL200List.map { ninoEntry =>
          checkIfAccountValidForPTA(ninoEntry)
        }
      } else {
        Seq(Future.successful(None))
      }

    Future.sequence(validPtaAccountList).map(_.flatten)
  }

  private def checkIfAccountValidForPTA(ninoEntry: IVNinoStoreEntry)(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Option[IVNinoStoreEntry]] = {
    eacdConnector
      .queryEnrolmentsAssignedToUser(ninoEntry.credId)
      .value
      .map {
        case Right(Some(enrolmentsList)) =>
          val hasNoBusinessEnrolments = enrolmentsList.enrolments
            .map(_.service)
            .forall(e => saEnrolmentSet(e))
          if (hasNoBusinessEnrolments) Some(ninoEntry) else None
        case Right(None) => Some(ninoEntry)
        case Left(_)     => None
      }
  }

}
