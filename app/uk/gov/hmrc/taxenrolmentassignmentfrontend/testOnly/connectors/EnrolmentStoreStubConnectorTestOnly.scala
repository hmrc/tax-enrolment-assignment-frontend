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

package uk.gov.hmrc.taxenrolmentassignmentfrontend.testOnly.connectors

import cats.data.EitherT
import play.api.{Logger, Logging}
import play.api.http.Status.{NOT_FOUND, NO_CONTENT, OK}
import play.api.libs.json.JsObject
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpResponse, UpstreamErrorResponse}
import uk.gov.hmrc.service.TEAFResult
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.{UnexpectedResponseFromEACD, UpstreamError, UpstreamUnexpected2XX}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.logging.EventLoggerService
import uk.gov.hmrc.taxenrolmentassignmentfrontend.logging.LoggingEvent.logUnexpectedResponseFromEACD
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.UsersAssignedEnrolment
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.enums.EnrolmentEnum.hmrcPTKey
import uk.gov.hmrc.taxenrolmentassignmentfrontend.testOnly.config.AppConfigTestOnly
import uk.gov.hmrc.taxenrolmentassignmentfrontend.testOnly.models.AccountDetailsTestOnly

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class EnrolmentStoreStubConnectorTestOnly @Inject() (
  appConfig: AppConfigTestOnly,
  httpClient: HttpClient,
  eventLogger: EventLoggerService
)(implicit
  val executionContext: ExecutionContext
) extends Logging {

  implicit val baseLogger: Logger = Logger(this.getClass.getName)

  def addStubAccount(account: AccountDetailsTestOnly)(implicit hc: HeaderCarrier): TEAFResult[Unit] = {
    val url = appConfig.enrolmentStoreStub + "/enrolment-store-stub/data"

    EitherT(
      httpClient
        .POST[JsObject, Either[UpstreamErrorResponse, HttpResponse]](
          url,
          account.enrolmentStoreStubAccountDetailsRequestBody
        )
    )
      .transform {
        case Right(response) if response.status == NO_CONTENT => Right(())
        case Right(response) =>
          val ex = new RuntimeException(s"Unexpected ${response.status} status")
          logger.error(ex.getMessage, ex)
          Left(UpstreamUnexpected2XX(response.body, response.status))
        case Left(upstreamError) =>
          logger.error(upstreamError.message)
          Left(UpstreamError(upstreamError))
      }
  }

  def deleteStubAccount(groupId: String)(implicit hc: HeaderCarrier): TEAFResult[Unit] = {
    val url = s"${appConfig.enrolmentStoreStub}/data/group/$groupId"

    EitherT(httpClient.DELETE[Either[UpstreamErrorResponse, HttpResponse]](url)).transform {
      case Right(response) if response.status == NO_CONTENT => Right(())
      case Left(error) if error.statusCode == NOT_FOUND     => Right(())
      case Right(response) =>
        val ex = new RuntimeException(s"Unexpected ${response.status} status")
        logger.error(ex.getMessage, ex)
        Left(UpstreamUnexpected2XX(response.body, response.status))
      case Left(upstreamError) =>
        logger.error(upstreamError.message)
        Left(UpstreamError(upstreamError))
    }
  }

  def getUsersWithPTEnrolment(nino: Nino)(implicit hc: HeaderCarrier): TEAFResult[UsersAssignedEnrolment] = EitherT {

    val enrolmentKey = s"$hmrcPTKey~NINO~${nino.nino}"
    val url =
      s"${appConfig.enrolmentStoreStub}/enrolment-store-proxy/enrolment-store/enrolments/$enrolmentKey/users"

    httpClient
      .GET[HttpResponse](url)
      .map(httpResponse =>
        httpResponse.status match {
          case OK =>
            Right(
              httpResponse.json
                .as[UsersAssignedEnrolment](UsersAssignedEnrolment.reads)
            )
          case NO_CONTENT => Right(UsersAssignedEnrolment(None))
          case status =>
            eventLogger.logEvent(
              logUnexpectedResponseFromEACD(
                enrolmentKey.split("~").head,
                status
              )
            )
            Left(UnexpectedResponseFromEACD)
        }
      )
  }
}
