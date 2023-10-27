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
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpResponse, UpstreamErrorResponse}
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.service.TEAFResult
import uk.gov.hmrc.taxenrolmentassignmentfrontend.config.testOnly.AppConfigTestOnly
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.UpstreamError

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class IdentityVerificationConnectorTestOnly @Inject() (httpClient: HttpClient, appConfigTestOnly: AppConfigTestOnly)(
  implicit ec: ExecutionContext
) {

  def deleteCredId(credId: String)(implicit hc: HeaderCarrier): TEAFResult[Unit] =
    EitherT(
      httpClient.DELETE[Either[UpstreamErrorResponse, HttpResponse]](
        s"${appConfigTestOnly.identityVerification}/test-only/nino/$credId"
      )
    )
      .bimap(error => UpstreamError(error), _ => ())

  def insertCredId(credId: String, nino: Nino)(implicit hc: HeaderCarrier): TEAFResult[Unit] = {
    val requestBody = Json.obj(
      "credId"          -> credId,
      "nino"            -> nino.nino,
      "confidenceLevel" -> 200
    )

    EitherT(
      httpClient.PUT[JsObject, Either[UpstreamErrorResponse, HttpResponse]](
        s"${appConfigTestOnly.identityVerification}/identity-verification/nino/$credId",
        requestBody
      )
    )
      .bimap(error => UpstreamError(error), _ => ())
  }
}
