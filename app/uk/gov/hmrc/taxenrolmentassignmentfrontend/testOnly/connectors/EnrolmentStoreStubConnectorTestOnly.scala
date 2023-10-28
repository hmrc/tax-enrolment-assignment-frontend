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
import play.api.http.Status.{NOT_FOUND, NO_CONTENT, OK}
import play.api.libs.json.JsObject
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpResponse, UpstreamErrorResponse}
import uk.gov.hmrc.service.TEAFResult
import uk.gov.hmrc.taxenrolmentassignmentfrontend.config.testOnly.AppConfigTestOnly
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.{UpstreamError, UpstreamUnexpected2XX}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.testOnly.models.AccountDetailsTestOnly

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class EnrolmentStoreStubConnectorTestOnly @Inject() (appConfig: AppConfigTestOnly, httpClient: HttpClient)(implicit
  val executionContext: ExecutionContext
) {

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
        case Right(response)                                  => Left(UpstreamUnexpected2XX(response.body, response.status))
        case Left(error)                                      => Left(UpstreamError(error))
      }
  }

  def getStubAccount(groupId: String)(implicit hc: HeaderCarrier): TEAFResult[Option[AccountDetailsTestOnly]] = {
    val url = s"${appConfig.enrolmentStoreStub}/data/group/$groupId"

    EitherT(httpClient.GET[Either[UpstreamErrorResponse, HttpResponse]](url)).transform {
      case Right(response) if response.status == OK           => Right(Some(response.json.as[AccountDetailsTestOnly]))
      case Left(response) if response.statusCode == NOT_FOUND => Right(None)
      case Right(response)                                    => Left(UpstreamUnexpected2XX(response.body, response.status))
      case Left(error)                                        => Left(UpstreamError(error))
    }
  }

  def deleteStubAccount(groupId: String)(implicit hc: HeaderCarrier): TEAFResult[Unit] = {
    val url = s"${appConfig.enrolmentStoreStub}/data/group/$groupId"

    EitherT(httpClient.DELETE[Either[UpstreamErrorResponse, HttpResponse]](url)).transform {
      case Right(response) if response.status == NO_CONTENT => Right(())
      case Right(response)                                  => Left(UpstreamUnexpected2XX(response.body, response.status))
      case Left(error)                                      => Left(UpstreamError(error))
    }
  }
}
