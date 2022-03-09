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

import play.api.mvc.{AnyContent, Result}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.service.TEAFResult
import uk.gov.hmrc.taxenrolmentassignmentfrontend.connectors.{EACDConnector, TaxEnrolmentsConnector}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.auth.RequestWithUserDetails
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.IVNinoStoreEntry

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class SilentAssignmentService @Inject()(
                                         taxEnrolmentsConnector: TaxEnrolmentsConnector,
                                         eacdConnector: EACDConnector
                                       ) {

  private val saEnrolmentSet: Set[String] = Set("IR-SA", "HMRC-MTD-IT", "HMRC-NI")

  def filterCL200Accounts(list: Seq[IVNinoStoreEntry]): Seq[IVNinoStoreEntry] = list.filter(_.confidenceLevel.exists(_ >= 200))


  def getValidPtaAccounts(list: Seq[IVNinoStoreEntry])
               (implicit hc: HeaderCarrier,
                ec: ExecutionContext): Future[Seq[Option[IVNinoStoreEntry]]] = {

    val filteredCL200List: Seq[IVNinoStoreEntry] = filterCL200Accounts(list)

    val validPtaAccountList: Seq[Future[Option[IVNinoStoreEntry]]] = if (filteredCL200List.size < 10) {
      filteredCL200List.map { ninoEntry =>
        eacdConnector.queryEnrolmentsAssignedToUser(ninoEntry.credId).value.map {
          case Right(Some(enrolmentsList)) =>
            val hasNoBusinessEnrolments = enrolmentsList.enrolments.map(_.service).forall(e => saEnrolmentSet(e))
            if (hasNoBusinessEnrolments) Some(ninoEntry) else None
          case Right(None) => Some(ninoEntry)
          case Left(_) => None
        }
      }
    } else {
      Seq(Future.successful(None))
    }

    Future.sequence(validPtaAccountList)
  }

  def enrolUser()(implicit request: RequestWithUserDetails[AnyContent],
                  hc: HeaderCarrier,
                  ec: ExecutionContext): TEAFResult[Unit] = {
    val details = request.userDetails
    taxEnrolmentsConnector.assignPTEnrolment(details.groupId, details.credId, details.nino)
  }

}
