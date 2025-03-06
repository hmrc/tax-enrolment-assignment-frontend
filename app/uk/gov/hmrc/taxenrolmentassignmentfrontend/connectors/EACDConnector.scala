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
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpException, HttpResponse, StringContextOps, UpstreamErrorResponse}
import uk.gov.hmrc.service.TEAFResult
import uk.gov.hmrc.taxenrolmentassignmentfrontend.config.AppConfig
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.UnexpectedResponseFromEACD
import uk.gov.hmrc.taxenrolmentassignmentfrontend.logging.EventLoggerService
import uk.gov.hmrc.taxenrolmentassignmentfrontend.logging.LoggingEvent.{logES1ErrorFromEACD, logES2ErrorFromEACD, logUnexpectedResponseFromEACD, logUnexpectedResponseFromEACDQueryKnownFacts}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.enums.EnrolmentEnum.{IRSAKey, hmrcPTKey}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.{KnownFactQueryForNINO, KnownFactResponseForNINO, UserEnrolmentsListResponse, UsersAssignedEnrolment}

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class EACDConnector @Inject() (httpClient: HttpClientV2, logger: EventLoggerService, appConfig: AppConfig) {

  implicit val baseLogger: Logger = Logger(this.getClass.getName)

  def getUsersWithPTEnrolment(nino: Nino)(implicit
    ec: ExecutionContext,
    hc: HeaderCarrier
  ): TEAFResult[UsersAssignedEnrolment] = {
    val enrolmentKey = s"$hmrcPTKey~NINO~${nino.nino}"
    getUsersWithAssignedEnrolment(enrolmentKey)
  }

  def getUsersWithSAEnrolment(utr: String)(implicit
    ec: ExecutionContext,
    hc: HeaderCarrier
  ): TEAFResult[UsersAssignedEnrolment] = {
    val enrolmentKey = s"$IRSAKey~UTR~$utr"
    getUsersWithAssignedEnrolment(enrolmentKey)
  }

  def getUsersWithAssignedEnrolment(enrolmentKey: String)(implicit
    ec: ExecutionContext,
    hc: HeaderCarrier
  ): TEAFResult[UsersAssignedEnrolment] = EitherT {
    val url =
      s"${appConfig.EACD_BASE_URL}/enrolment-store/enrolments/$enrolmentKey/users"
    httpClient
      .get(url"$url")
      .execute[HttpResponse]
      .map(httpResponse =>
        httpResponse.status match {
          case OK =>
            Right(
              httpResponse.json
                .as[UsersAssignedEnrolment](UsersAssignedEnrolment.reads)
            )
          case NO_CONTENT =>
            Right(UsersAssignedEnrolment(None))
          case status =>
            logger.logEvent(
              logUnexpectedResponseFromEACD(
                enrolmentKey.split("~").head,
                status
              )
            )
            Left(UnexpectedResponseFromEACD)
        }
      )
  }

  //ES20 Query known facts that match the supplied query parameters
  def queryKnownFactsByNinoVerifier(nino: Nino)(implicit
    ec: ExecutionContext,
    hc: HeaderCarrier
  ): TEAFResult[Option[String]] = EitherT {
    val url = s"${appConfig.EACD_BASE_URL}/enrolment-store/enrolments"
    val request = KnownFactQueryForNINO(nino)
    httpClient
      .post(url"$url")
      .withBody(request)
      .execute[HttpResponse]
      .map(httpResponse =>
        httpResponse.status match {
          case OK =>
            Right(Some(httpResponse.json.as[KnownFactResponseForNINO].getUTR))
          case NO_CONTENT => Right(None)
          case status =>
            logger.logEvent(
              logUnexpectedResponseFromEACDQueryKnownFacts(
                nino,
                status,
                httpResponse.body
              )
            )
            Left(UnexpectedResponseFromEACD)
        }
      )
  }

  def queryEnrolmentsAssignedToUser(userId: String)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): TEAFResult[Option[UserEnrolmentsListResponse]] = EitherT {
    val url =
      s"${appConfig.EACD_BASE_URL}/enrolment-store/users/$userId/enrolments?type=principal"

    httpClient
      .get(url"$url")
      .execute[HttpResponse]
      .map(httpResponse =>
        httpResponse.status match {
          case OK =>
            Right(Some(httpResponse.json.as[UserEnrolmentsListResponse]))
          case NO_CONTENT => Right(None)
          case status =>
            logger.logEvent(
              logES2ErrorFromEACD(userId, status, httpResponse.body)
            )
            Left(UnexpectedResponseFromEACD)
        }
      )
  }

  //ES1 Query groups who have an allocated enrolment
  def getGroupsFromEnrolment(
    enrolmentKey: String
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): EitherT[Future, UpstreamErrorResponse, HttpResponse] = {
    val url = s"${appConfig.EACD_BASE_URL}/enrolment-store/enrolments/$enrolmentKey/groups?ignore-assignments=true"
    EitherT(
      httpClient
        .get(
          url"$url"
        )
        .execute[Either[UpstreamErrorResponse, HttpResponse]]
        .recover { case exception: HttpException =>
          Left(UpstreamErrorResponse(exception.message, BAD_GATEWAY, BAD_GATEWAY))
        }
    ).leftMap { error =>
      logger.logEvent(logES1ErrorFromEACD(enrolmentKey, error.message))
      error
    }
  }
}
