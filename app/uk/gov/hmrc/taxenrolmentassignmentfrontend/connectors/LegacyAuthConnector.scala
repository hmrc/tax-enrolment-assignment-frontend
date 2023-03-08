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
import play.api.Logger
import play.api.http.Status._
import uk.gov.hmrc.auth.core.Enrolment
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpResponse}
import uk.gov.hmrc.service.TEAFResult
import uk.gov.hmrc.taxenrolmentassignmentfrontend.config.AppConfig
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.UnexpectedResponseAssigningTemporaryPTAEnrolment
import uk.gov.hmrc.taxenrolmentassignmentfrontend.logging.{EventLoggerService, LoggingEvent}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.formats.EnrolmentsFormats

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class LegacyAuthConnector @Inject()(httpClient: HttpClient,
                                     logger: EventLoggerService,
                                     appConfig: AppConfig) {

  implicit val baseLogger: Logger = Logger(this.getClass.getName)

  def updateEnrolments(enrolments: Set[Enrolment])(
                        implicit ec: ExecutionContext,
                        hc: HeaderCarrier): TEAFResult[Unit] = EitherT {
    httpClient
      .PUT[Set[Enrolment], HttpResponse](s"${appConfig.AUTH_BASE_URL}/enrolments", enrolments)(wts = EnrolmentsFormats.writes, implicitly, implicitly, implicitly)
      .map(httpResponse =>
        httpResponse.status match {
        case OK => Right(Unit)
        case _ =>
          logger.logEvent(LoggingEvent.logUnexpectedErrorFromAuthWhenUsingLegacyEndpoint(httpResponse.status))
          Left(UnexpectedResponseAssigningTemporaryPTAEnrolment)
      })
  }
}
