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
import play.api.http.Status.{CREATED, NOT_FOUND, NO_CONTENT}
import play.api.libs.json.JsObject
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpResponse, UpstreamErrorResponse}
import uk.gov.hmrc.service.TEAFResult
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.{UpstreamError, UpstreamUnexpected2XX}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.testOnly.config.AppConfigTestOnly
import uk.gov.hmrc.taxenrolmentassignmentfrontend.testOnly.models.AccountDetailsTestOnly

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class BasStubsConnectorTestOnly @Inject() (httpClient: HttpClient, appConfigTestOnly: AppConfigTestOnly)(implicit
  ec: ExecutionContext
) extends Logging {
  def putAccount(account: AccountDetailsTestOnly)(implicit hc: HeaderCarrier): TEAFResult[Unit] = {
    val url = s"${appConfigTestOnly.basStubsBaseUrl}/bas-stubs/account"

    EitherT(
      httpClient.PUT[JsObject, Either[UpstreamErrorResponse, HttpResponse]](
        url,
        account.basStubAccountDetailsRequestBody
      )
    )
      .transform {
        case Right(response) if response.status == CREATED => Right(())
        case Right(response)                               =>
          val ex = new RuntimeException(s"Unexpected ${response.status} status")
          logger.error(ex.getMessage, ex)
          Left(UpstreamUnexpected2XX(response.body, response.status))
        case Left(upstreamError)                                   =>
          logger.error(upstreamError.message)
          Left(UpstreamError(upstreamError))
      }
  }

  def putAdditionalFactors(account: AccountDetailsTestOnly)(implicit hc: HeaderCarrier): TEAFResult[Unit] = {
    val url = s"${appConfigTestOnly.basStubsBaseUrl}/bas-stubs/credentials/factors"
    EitherT(httpClient.PUT[JsObject, Either[UpstreamErrorResponse, HttpResponse]](url, account.mfaAccountRequestBody))
      .transform {
        case Right(response) if response.status == CREATED => Right(())
        case Right(response) =>
          val ex = new RuntimeException(s"Unexpected ${response.status} status")
          logger.error(ex.getMessage, ex)
          Left(UpstreamUnexpected2XX(response.body, response.status))
        case Left(upstreamError) =>
          logger.error(upstreamError.message)
          Left(UpstreamError(upstreamError))
      }
  }

  def deleteAdditionalFactors(credId: String)(implicit hc: HeaderCarrier): TEAFResult[Unit] = {
    val url = s"${appConfigTestOnly.basStubsBaseUrl}/bas-stubs/credentials/$credId/factors/additionalfactors"
    EitherT(httpClient.DELETE[Either[UpstreamErrorResponse, HttpResponse]](url))
      .transform {
        case Left(error) if error.statusCode == NOT_FOUND     => Right(())
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
}
