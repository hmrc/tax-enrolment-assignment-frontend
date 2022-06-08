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
import com.google.inject.Singleton
import play.api.Logger
import play.api.http.Status.OK
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpResponse}
import uk.gov.hmrc.service.TEAFResult
import uk.gov.hmrc.taxenrolmentassignmentfrontend.config.AppConfig
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.{ResponseBodyInvalidFromAddTaxesFrontendSASetup, UnexpectedResponseFromAddTaxesFrontendSASetup}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.logging.{EventLoggerService, LoggingEvent}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.setupSAJourney.{SASetupJourneyRequest, SASetupJourneyResponse}
import uk.gov.hmrc.http.HttpReads.Implicits._
import javax.inject.Inject
import scala.concurrent.ExecutionContext


@Singleton
class AddTaxesFrontendConnector @Inject()(httpClient: HttpClient,
                                logger: EventLoggerService,
                                appConfig: AppConfig) {

  implicit val baseLogger: Logger = Logger(this.getClass.getName)

  def saSetupJourney(saSetupJourneyRequest: SASetupJourneyRequest)(implicit ec: ExecutionContext,
  hc: HeaderCarrier): TEAFResult[SASetupJourneyResponse] = EitherT {
    httpClient.POST[SASetupJourneyRequest, HttpResponse](
      appConfig.ADD_TAXES_FRONTEND_SA_INIT_URL,
      saSetupJourneyRequest).map(response =>
      response.status match {
        case OK => response.json.validate[SASetupJourneyResponse].asEither.left.map{_ =>
          logger.logEvent(LoggingEvent.logBadResponseFromSASetupJourney(response.body, response.status))
          ResponseBodyInvalidFromAddTaxesFrontendSASetup}
        case _ =>
          logger.logEvent(LoggingEvent.logBadResponseFromSASetupJourney(response.body, response.status))
          Left(UnexpectedResponseFromAddTaxesFrontendSASetup)
      })
      }
}
