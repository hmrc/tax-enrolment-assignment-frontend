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
import uk.gov.hmrc.http.HeaderCarrier
import play.api.Logger
import uk.gov.hmrc.service.TEAFResult
import uk.gov.hmrc.taxenrolmentassignmentfrontend.connectors.{EACDConnector, TaxEnrolmentsConnector}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.actions.RequestWithUserDetailsFromSession
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.TaxEnrolmentAssignmentErrors
import uk.gov.hmrc.taxenrolmentassignmentfrontend.logging.EventLoggerService
import uk.gov.hmrc.taxenrolmentassignmentfrontend.logging.LoggingEvent.logCurrentUserhasMultipleAccountsDebug
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.IVNinoStoreEntry
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.enums.EnrolmentEnum.saEnrolmentSet
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.SessionKeys.HAS_OTHER_VALID_PTA_ACCOUNTS
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.TEASessionCache

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class SilentAssignmentService @Inject() (
  taxEnrolmentsConnector: TaxEnrolmentsConnector
) {

  implicit val baseLogger: Logger = Logger(this.getClass.getName)

  def enrolUser()(implicit
    request: RequestWithUserDetailsFromSession[_],
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): TEAFResult[Unit] = {
    val details = request.userDetails
    taxEnrolmentsConnector.assignPTEnrolmentWithKnownFacts(details.nino)
  }

}
