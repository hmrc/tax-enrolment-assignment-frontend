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
import play.api.http.Status.NON_AUTHORITATIVE_INFORMATION
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpResponse}
import uk.gov.hmrc.service.TEAFResult
import uk.gov.hmrc.taxenrolmentassignmentfrontend.config.AppConfig
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.UnexpectedResponseFromUsersGroupsSearch
import uk.gov.hmrc.taxenrolmentassignmentfrontend.logging.EventLoggerService
import uk.gov.hmrc.taxenrolmentassignmentfrontend.logging.LoggingEvent._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.UsersGroupResponse

import scala.concurrent.ExecutionContext

@Singleton
class UsersGroupsSearchConnector @Inject()(httpClient: HttpClient,
                                           logger: EventLoggerService,
                                           appConfig: AppConfig) {

  implicit val baseLogger: Logger = Logger(this.getClass.getName)

  def getUserDetails(credId: String)(
    implicit ec: ExecutionContext,
    hc: HeaderCarrier
  ): TEAFResult[UsersGroupResponse] = EitherT {
    val url = if (appConfig.useTestOnlyUsersGroupSearch) {
      s"${appConfig.tenBaseUrl}/users-groups-search/test-only/users/$credId"
    } else {
      s"${appConfig.usersGroupsSearchBaseURL}/users/$credId"
    }

    httpClient
      .GET[HttpResponse](url)
      .map(
        httpResponse =>
          httpResponse.status match {
            case NON_AUTHORITATIVE_INFORMATION =>
              Right(httpResponse.json.as[UsersGroupResponse])
            case status =>
              logger.logEvent(
                logUnexpectedResponseFromUsersGroupsSearch(credId, status)
              )
              Left(UnexpectedResponseFromUsersGroupsSearch)
        }
      )
  }
}
