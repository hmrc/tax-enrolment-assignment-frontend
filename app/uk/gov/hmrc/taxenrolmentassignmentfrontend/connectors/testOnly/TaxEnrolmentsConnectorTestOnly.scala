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
import cats.implicits.toTraverseOps
import play.api.{Logger, Logging}
import play.api.http.Status._
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpResponse, UpstreamErrorResponse}
import uk.gov.hmrc.service.TEAFResult
import uk.gov.hmrc.taxenrolmentassignmentfrontend.config.AppConfig
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.{TaxEnrolmentAssignmentErrors, Upstream2xxError, UpstreamError}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.testOnly.SimpleEnrolment

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class TaxEnrolmentsConnectorTestOnly @Inject() (
  httpClient: HttpClient,
  appConfig: AppConfig
) extends Logging {

  implicit val baseLogger: Logger = Logger(this.getClass.getName)

  def deleteEnrolmentFromUserES12(credId: String, key: String)(implicit
    ec: ExecutionContext,
    hc: HeaderCarrier
  ): TEAFResult[Unit] = {

    val url = s"${appConfig.TAX_ENROLMENTS_BASE_URL_REAL}/groups/$credId/enrolments/$key"

    EitherT(
      httpClient
        .DELETE[Either[UpstreamErrorResponse, HttpResponse]](url)
    )
      .bimap(UpstreamError, _ => ())
  }

  def upsertKnownFactES6(enrolment: SimpleEnrolment)(implicit
    ec: ExecutionContext,
    hc: HeaderCarrier
  ): TEAFResult[Unit] =
    enrolment.identifiers
      .map { identifier =>
        val url =
          s"${appConfig.TAX_ENROLMENTS_BASE_URL_REAL}/enrolments/${enrolment.serviceName}~${identifier.key}~${identifier.value}"
        val payload = Json.obj("verifiers" -> enrolment.verifiers.map(x => Json.toJson(x)))

        EitherT(
          httpClient
            .PUT[JsObject, Either[UpstreamErrorResponse, HttpResponse]](url, payload)
        ).transform {
          case Right(response) if response.status == NO_CONTENT => Right(())
          case Right(_) =>
            val ex = new RuntimeException(s"Unexpected 2XX response from $url")
            logger.error(ex.getMessage, ex)
            Left(Upstream2xxError: TaxEnrolmentAssignmentErrors)
          case Left(error) => Left(UpstreamError(error): TaxEnrolmentAssignmentErrors)
        }
      }
      .sequence
      .map(_ => ())

  def assignGroupSimpleEnrolment(userId: String, groupId: String, enrolment: SimpleEnrolment)(implicit
    ec: ExecutionContext,
    hc: HeaderCarrier
  ): TEAFResult[Unit] =
    enrolment.identifiers
      .map { identifier =>
        val url =
          s"${appConfig.TAX_ENROLMENTS_BASE_URL_REAL}/groups/$groupId/enrolments/${enrolment.serviceName}~${identifier.key}~${identifier.value}"
        val payload = Json.obj(
          "userId" -> userId,
          "type"   -> "principal",
          "action" -> "enrolAndActivate"
        )

        EitherT(
          httpClient
            .POST[JsObject, Either[UpstreamErrorResponse, HttpResponse]](url, payload)
        ).transform {
          case Right(response) if response.status == CREATED => Right(())
          case Right(_) =>
            val ex = new RuntimeException(s"Unexpected 2XX response from $url")
            logger.error(ex.getMessage, ex)
            Left(Upstream2xxError: TaxEnrolmentAssignmentErrors)
          case Left(error) => Left(UpstreamError(error): TaxEnrolmentAssignmentErrors)
        }
      }
      .sequence
      .map(_ => ())
}
