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
import play.api.http.Status.{NON_AUTHORITATIVE_INFORMATION, NOT_FOUND, OK}
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps, UpstreamErrorResponse}
import uk.gov.hmrc.service.TEAFResult
import uk.gov.hmrc.taxenrolmentassignmentfrontend.config.AppConfig
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.UnexpectedResponseFromUsersGroupsSearch
import uk.gov.hmrc.taxenrolmentassignmentfrontend.logging.EventLoggerService
import uk.gov.hmrc.taxenrolmentassignmentfrontend.logging.LoggingEvent._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.{IdentityProviderWithCredId, UsersGroupResponse}

import scala.concurrent.ExecutionContext

@Singleton
class UsersGroupsSearchConnector @Inject() (
  httpClient: HttpClientV2,
  logger: EventLoggerService,
  appConfig: AppConfig
) {

  implicit val baseLogger: Logger = Logger(this.getClass.getName)

  def getUserDetails(credId: String)(implicit
    ec: ExecutionContext,
    hc: HeaderCarrier
  ): TEAFResult[UsersGroupResponse] = EitherT {
    val url = s"${appConfig.usersGroupsSearchBaseURL}/users/$credId"

    httpClient
      .get(url"$url")
      .execute[HttpResponse]
      .map(httpResponse =>
        httpResponse.status match {
          case NON_AUTHORITATIVE_INFORMATION =>
            Right(httpResponse.json.as[UsersGroupResponse])
          case status                        =>
            logger.logEvent(
              logUnexpectedResponseFromUsersGroupsSearch(credId, status)
            )
            Left(UnexpectedResponseFromUsersGroupsSearch)
        }
      )
  }

  def getAllCredIdsByNino(nino: String)(implicit
    ec: ExecutionContext,
    hc: HeaderCarrier
  ): TEAFResult[Seq[IdentityProviderWithCredId]] = EitherT {
    val url = s"${appConfig.usersGroupsSearchBaseURL}/users/nino/$nino/credIds"

    httpClient
      .get(url"$url")
      .execute[Either[UpstreamErrorResponse, HttpResponse]]
      .map {
        case Right(httpResponse) if httpResponse.status == OK =>
          Right(httpResponse.json.as[Seq[IdentityProviderWithCredId]](IdentityProviderWithCredId.readList))
        case Left(error) if error.statusCode == NOT_FOUND     => Right(Seq.empty)
        case _                                                => Left(UnexpectedResponseFromUsersGroupsSearch)
      }
  }
}
