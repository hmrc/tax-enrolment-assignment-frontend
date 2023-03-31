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

package uk.gov.hmrc.taxenrolmentassignmentfrontend.connectors

import cats.data.EitherT
import javax.inject.{Inject, Singleton}
import play.api.Logger
import play.api.http.Status._
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpResponse}
import uk.gov.hmrc.service.TEAFResult
import uk.gov.hmrc.taxenrolmentassignmentfrontend.config.AppConfig
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.UnexpectedResponseFromTaxEnrolments
import uk.gov.hmrc.taxenrolmentassignmentfrontend.logging.EventLoggerService
import uk.gov.hmrc.taxenrolmentassignmentfrontend.logging.LoggingEvent.{logPTEnrolmentHasAlreadyBeenAssigned, logUnexpectedResponseFromTaxEnrolmentsKnownFacts}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.enums.EnrolmentEnum.hmrcPTKey
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.{AssignHMRCPTRequest, IdentifiersOrVerifiers}

import scala.concurrent.ExecutionContext

@Singleton
class TaxEnrolmentsConnector @Inject() (httpClient: HttpClient, logger: EventLoggerService, appConfig: AppConfig) {

  implicit val baseLogger: Logger = Logger(this.getClass.getName)

  def assignPTEnrolmentWithKnownFacts(nino: String)(implicit
    ec: ExecutionContext,
    hc: HeaderCarrier
  ): TEAFResult[Unit] = EitherT {

    val request = AssignHMRCPTRequest(
      identifiers = Seq(IdentifiersOrVerifiers("NINO", nino)),
      verifiers = Seq(IdentifiersOrVerifiers("NINO1", nino))
    )
    val url = s"${appConfig.TAX_ENROLMENTS_BASE_URL}/service/$hmrcPTKey/enrolment"

    httpClient
      .PUT[AssignHMRCPTRequest, HttpResponse](url, request)
      .map(httpResponse =>
        httpResponse.status match {
          case NO_CONTENT => Right(())
          case CONFLICT =>
            logger.logEvent(logPTEnrolmentHasAlreadyBeenAssigned(nino))
            Right(())
          case status =>
            logger
              .logEvent(logUnexpectedResponseFromTaxEnrolmentsKnownFacts(nino, status))
            Left(UnexpectedResponseFromTaxEnrolments)
        }
      )
  }

}
