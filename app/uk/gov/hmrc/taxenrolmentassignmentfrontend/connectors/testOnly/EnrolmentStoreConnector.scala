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

package uk.gov.hmrc.taxenrolmentassignmentfrontend.connectors.testOnly

import cats.data.EitherT
import play.api.Logging
import play.api.http.Status.{NO_CONTENT, OK}
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpResponse, UpstreamErrorResponse}
import uk.gov.hmrc.service.TEAFResult
import uk.gov.hmrc.taxenrolmentassignmentfrontend.config.AppConfig
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.{Upstream2xxError, UpstreamError}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.UserEnrolmentsListResponse

import javax.inject.Inject
import scala.concurrent.ExecutionContext

class EnrolmentStoreConnector @Inject() (httpClient: HttpClient, appConfig: AppConfig) extends Logging {
  def getEnrolmentsFromGroupES3(groupId: String)(implicit
    ec: ExecutionContext,
    hc: HeaderCarrier
  ): TEAFResult[UserEnrolmentsListResponse] = {
    val url = s"${appConfig.EACD_BASE_URL}/enrolment-store-proxy/enrolment-store/groups/$groupId/enrolments"
    EitherT(
      httpClient.GET[Either[UpstreamErrorResponse, HttpResponse]](url)
    ).transform {
      case Right(response) if response.status == OK         => Right(response.json.as[UserEnrolmentsListResponse])
      case Right(response) if response.status == NO_CONTENT => Right(UserEnrolmentsListResponse(List.empty))
      case Right(_) =>
        val ex = new RuntimeException(s"Unexpected 2XX response from $url")
        logger.error(ex.getMessage, ex)
        Left(Upstream2xxError)
      case Left(error) => Left(UpstreamError(error))
    }
  }
}
