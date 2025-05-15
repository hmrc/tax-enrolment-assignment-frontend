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
import play.api.http.Status.{CREATED, OK}
import play.api.libs.ws.writeableOf_JsValue
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps, UpstreamErrorResponse}
import uk.gov.hmrc.service.TEAFResult
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.{UpstreamError, UpstreamUnexpected2XX}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.testOnly.config.AppConfigTestOnly
import uk.gov.hmrc.taxenrolmentassignmentfrontend.testOnly.models.AccountDetailsTestOnly

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class OneLoginStubConnectorTestOnly @Inject() (
  httpClient: HttpClientV2,
  appConfigTestOnly: AppConfigTestOnly
)(implicit
  ec: ExecutionContext
) extends Logging {
  def postAccount(account: AccountDetailsTestOnly)(implicit hc: HeaderCarrier): TEAFResult[String] = {
    val url =
      s"${appConfigTestOnly.oneLoginStubBaseUrl}/one-login-stub/test/accounts"

    EitherT(
      httpClient
        .post(url"$url")
        .withBody(account.identityProviderAccountContextRequestBody)
        .execute[Either[UpstreamErrorResponse, HttpResponse]]
    )
      .transform {
        case Right(response) if response.status == CREATED =>
          Right(
            (response.json \ "centralAuthUser" \ "_id").as[String]
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

  def deleteAccount(caUserId: String)(implicit hc: HeaderCarrier): TEAFResult[Unit] = {
    val url =
      s"${appConfigTestOnly.oneLoginStubBaseUrl}/one-login-stub/test/accounts/$caUserId"

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

  def getAccount(identityProviderId: String)(implicit hc: HeaderCarrier): TEAFResult[Option[String]] = {
    val url =
      s"${appConfigTestOnly.oneLoginStubBaseUrl}/one-login-stub/test/accounts?identityProviderId=$identityProviderId"

    EitherT(
      httpClient.get(url"$url").execute[Either[UpstreamErrorResponse, HttpResponse]]
    ).transform {
      case Right(response) =>
        Right(Some((response.json \ "caUserId").as[String]))
      case Left(_) =>
        logger.warn(s"No account found for identityProviderId: $identityProviderId")
        Right(None)
    }
  }
}
