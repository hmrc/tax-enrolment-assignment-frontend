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
import play.api.http.Status.{CREATED}
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpResponse, UpstreamErrorResponse}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.config.testOnly.AppConfigTestOnly
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.service.TEAFResult
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.UpstreamError
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.testOnly.AccountDetailsTestOnly

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class BasStubsConnectorTestOnly @Inject() (httpClient: HttpClient, appConfigTestOnly: AppConfigTestOnly)(implicit
  ec: ExecutionContext
) {
  def putAccount(account: AccountDetailsTestOnly)(implicit hc: HeaderCarrier): TEAFResult[Unit] = {
    val url = s"${appConfigTestOnly.basStubsBaseUrl}/bas-stubs/account"
    val requestBody = Json.obj(
      "credId"        -> account.users.head.credId,
      "userId"        -> account.users.head.credId,
      "isAdmin"       -> true,
      "email"         -> "email@example.com",
      "emailVerified" -> true,
      "profile"       -> "/profile",
      "groupId"       -> account.groupId,
      "groupProfile"  -> "/group/profile",
      "trustId"       -> "trustId",
      "name"          -> "Name",
      "suspended"     -> false
    )

    EitherT(
      httpClient.PUT[JsObject, Either[UpstreamErrorResponse, HttpResponse]](
        url,
        requestBody
      )
    )
      .transform {
        case Right(response) if response.status == CREATED => Right(())
        case Right(response)                               => Left(UpstreamError(UpstreamErrorResponse(response.body, response.status)))
        case Left(error)                                   => Left(UpstreamError(error))
      }

  }
}
