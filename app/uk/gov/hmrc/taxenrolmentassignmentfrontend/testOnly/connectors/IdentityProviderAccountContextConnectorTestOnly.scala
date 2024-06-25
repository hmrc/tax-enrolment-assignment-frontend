/*
 * Copyright 2024 HM Revenue & Customs
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
import play.api.http.Status.{CONFLICT, CREATED}
import play.api.libs.json.JsObject
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpResponse, UpstreamErrorResponse}
import uk.gov.hmrc.service.TEAFResult
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.{UpstreamError, UpstreamUnexpected2XX}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.testOnly.config.AppConfigTestOnly
import uk.gov.hmrc.taxenrolmentassignmentfrontend.testOnly.models.AccountDetailsTestOnly
import uk.gov.hmrc.http.HttpReads.Implicits._

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class IdentityProviderAccountContextConnectorTestOnly @Inject() (
  httpClient: HttpClient,
  appConfigTestOnly: AppConfigTestOnly
)(implicit
  ec: ExecutionContext
) extends Logging {
  def postAccount(account: AccountDetailsTestOnly)(implicit hc: HeaderCarrier): TEAFResult[String] = {
    val url =
      s"${appConfigTestOnly.identityProviderAccountContextBaseUrl}/identity-provider-account-context/test-only/accounts"

    EitherT(
      httpClient.POST[JsObject, Either[UpstreamErrorResponse, HttpResponse]](
        url,
        account.identityProviderAccountContextRequestBody
      )
    )
      .transform {
        case Right(response) if response.status == CREATED =>
          Right(
            (response.json \ "caUserId").as[String]
          )
        case Right(response) =>
          val ex = new RuntimeException(s"Unexpected ${response.status} status")
          logger.error(ex.getMessage, ex)
          Left(UpstreamUnexpected2XX(response.body, response.status))
        case Left(upstreamError) =>
          logger.error(upstreamError.message)
          Left(UpstreamError(upstreamError))
      }
  }

  def postIndividual(account: AccountDetailsTestOnly, caUserId: String)(implicit
    hc: HeaderCarrier
  ): TEAFResult[Unit] = {
    val url =
      s"${appConfigTestOnly.identityProviderAccountContextBaseUrl}/identity-provider-account-context/contexts/individual"

    EitherT(
      httpClient.POST[JsObject, Either[UpstreamErrorResponse, HttpResponse]](
        url,
        account.individualContextUpdateRequestBody(caUserId)
      )
    )
      .transform {
        case Right(response) if response.status == CREATED => Right(())
        case Left(error) if error.statusCode == CONFLICT   => Right(())
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
