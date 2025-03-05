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
import play.api.Logging
import play.api.http.Status.OK
import play.api.libs.json.Json
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps, UpstreamErrorResponse}
import uk.gov.hmrc.service.TEAFResult
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.{UpstreamError, UpstreamUnexpected2XX}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.testOnly.config.AppConfigTestOnly

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class IdentityVerificationConnectorTestOnly @Inject() (httpClient: HttpClientV2, appConfigTestOnly: AppConfigTestOnly)(
  implicit ec: ExecutionContext
) extends Logging {

  def deleteCredId(credId: String)(implicit hc: HeaderCarrier): TEAFResult[Unit] = {
    val url = s"${appConfigTestOnly.identityVerification}/test-only/nino/$credId"
    EitherT(
      httpClient.delete(url"$url").execute[Either[UpstreamErrorResponse, HttpResponse]]
    ).transform {
      case Right(response) if response.status == OK => Right(())
      case Right(response) =>
        val ex = new RuntimeException(s"Unexpected ${response.status} status")
        logger.error(ex.getMessage, ex)
        Left(UpstreamUnexpected2XX(response.body, response.status))
      case Left(upstreamError) =>
        logger.error(upstreamError.message)
        Left(UpstreamError(upstreamError))
    }
  }

  def insertCredId(credId: String, nino: Nino)(implicit hc: HeaderCarrier): TEAFResult[Unit] = {
    val requestBody = Json.obj(
      "credId"          -> credId,
      "nino"            -> nino.nino,
      "confidenceLevel" -> 200
    )
    val url = s"${appConfigTestOnly.identityVerification}/identity-verification/nino/$credId"
    EitherT(
      httpClient.put(url"$url").withBody(requestBody).execute[Either[UpstreamErrorResponse, HttpResponse]]
    ).transform {
      case Right(response) if response.status == OK => Right(())
      case Right(response) =>
        val ex = new RuntimeException(s"Unexpected ${response.status} status")
        logger.error(ex.getMessage, ex)
        Left(UpstreamUnexpected2XX(response.body, response.status))
      case Left(upstreamError) =>
        logger.error(upstreamError.message)
        Left(UpstreamError(upstreamError))
    }
  }
}
