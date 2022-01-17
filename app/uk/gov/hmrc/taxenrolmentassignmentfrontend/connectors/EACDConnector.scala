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

package uk.gov.hmrc.taxenrolmentassignmentfrontend.connectors

import cats.data.EitherT
import com.google.inject.Inject
import play.api.Logger
import play.api.http.Status._
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpResponse}
import uk.gov.hmrc.service.TEAFResult
import uk.gov.hmrc.taxenrolmentassignmentfrontend.config.AppConfig
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.UnexpectedResponseFromEACD
import uk.gov.hmrc.taxenrolmentassignmentfrontend.logging.EventLoggerService
import uk.gov.hmrc.taxenrolmentassignmentfrontend.logging.LoggingEvent.logUnexpectedResponseFromIV
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.UsersAssignedEnrolment
import uk.gov.hmrc.http.HttpReads.Implicits._

import scala.concurrent.ExecutionContext

class EACDConnector @Inject()(httpClient: HttpClient,
                              logger: EventLoggerService,
                              appConfig: AppConfig) {

  implicit val baseLogger: Logger = Logger(this.getClass.getName)

  def getUsersWithPTEnrolment(nino: String)(
    implicit ec: ExecutionContext,
    hc: HeaderCarrier
  ): TEAFResult[Option[UsersAssignedEnrolment]] = {
    val enrolmentKey = s"HMRC-PT~NINO~$nino"
    getUsersWithAssignedEnrolment(enrolmentKey)
  }

  def getUsersWithAssignedEnrolment(enrolmentKey: String)(
    implicit ec: ExecutionContext,
    hc: HeaderCarrier
  ): TEAFResult[Option[UsersAssignedEnrolment]] = EitherT {
    val url =
      s"${appConfig.EACD_BASE_URL}/enrolment-store/enrolments/$enrolmentKey/users"
    httpClient
      .GET[HttpResponse](url)
      .map(
        httpResponse =>
          httpResponse.status match {
            case OK         => Right(Some(httpResponse.json.as[UsersAssignedEnrolment]))
            case NO_CONTENT => Right(None)
            case status =>
              logger.logEvent(
                logUnexpectedResponseFromIV(
                  enrolmentKey.split("~").head,
                  status
                )
              )
              Left(UnexpectedResponseFromEACD)
        }
      )

  }

}
