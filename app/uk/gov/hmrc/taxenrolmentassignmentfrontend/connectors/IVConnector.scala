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
import com.google.inject.{Inject, Singleton}
import play.api.Logger
import play.api.http.Status.OK
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpResponse}
import uk.gov.hmrc.service.TEAFResult
import uk.gov.hmrc.taxenrolmentassignmentfrontend.config.AppConfig
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.UnexpectedResponseFromIV
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.IVNinoStoreEntry
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.logging.EventLoggerService
import uk.gov.hmrc.taxenrolmentassignmentfrontend.logging.LoggingEvent._

import scala.concurrent.ExecutionContext

@Singleton
class IVConnector @Inject()(httpClient: HttpClient,
                            logger: EventLoggerService,
                            appConfig: AppConfig) {

  implicit val baseLogger: Logger = Logger(this.getClass.getName)

  def getCredentialsWithNino(nino: String)(
    implicit ec: ExecutionContext,
    hc: HeaderCarrier
  ): TEAFResult[List[IVNinoStoreEntry]] = EitherT {
    val url = s"${appConfig.IV_BASE_URL}/nino"

    httpClient
      .GET[HttpResponse](url, queryParams = Seq("nino" -> nino))
      .map(
        httpResponse =>
          httpResponse.status match {
            case OK =>
              logger.logEvent(
                logResponseFromIV(nino, httpResponse.json.toString())
              )
              Right(httpResponse.json.as[List[IVNinoStoreEntry]])
            case status =>
              logger.logEvent(logUnexpectedResponseFromIV(nino, status))
              Left(UnexpectedResponseFromIV)
        }
      )
  }
}
