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
import play.api.http.Status.{CREATED, NOT_FOUND, NO_CONTENT, OK}
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpResponse, UpstreamErrorResponse}
import uk.gov.hmrc.service.TEAFResult
import uk.gov.hmrc.taxenrolmentassignmentfrontend.config.AppConfig
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.UpstreamError
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.testOnly.EnrolmentDetailsTestOnly

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class EnrolmentStoreConnectorTestOnly @Inject() (httpClient: HttpClient, appConfig: AppConfig)(implicit
  ec: ExecutionContext
) {
  //ES0
  def getUsersFromEnrolment(enrolmentKey: String)(implicit hc: HeaderCarrier): TEAFResult[List[String]] =
    EitherT(
      httpClient.GET[Either[UpstreamErrorResponse, HttpResponse]](
        s"${appConfig.EACD_BASE_URL}/enrolment-store/enrolments/$enrolmentKey/users"
      )
    )
      .transform {
        case Right(response) if response.status == NO_CONTENT => Right(List.empty)
        case Right(response) if response.status == OK =>
          val principals = (response.json \ "principalUserIds").as[List[String]]
          val delegated = (response.json \ "delegatedUserIds").as[List[String]]
          Right(principals ++ delegated)

        case Right(response) => Left(UpstreamError(UpstreamErrorResponse(response.body, response.status)))
        case Left(error)     => Left(UpstreamError(error))
      }

  //ES1
  def getGroupsFromEnrolment(enrolmentKey: String)(implicit hc: HeaderCarrier): TEAFResult[List[String]] =
    EitherT(
      httpClient.GET[Either[UpstreamErrorResponse, HttpResponse]](
        s"${appConfig.EACD_BASE_URL}/enrolment-store/enrolments/$enrolmentKey/groups"
      )
    )
      .transform {
        case Right(response) if response.status == NO_CONTENT => Right(List.empty)
        case Right(response) if response.status == OK =>
          val principals = (response.json \ "principalGroupIds").as[List[String]]
          val delegated = (response.json \ "delegatedGroupIds").as[List[String]]
          Right(principals ++ delegated)

        case Right(response) => Left(UpstreamError(UpstreamErrorResponse(response.body, response.status)))
        case Left(error)     => Left(UpstreamError(error))
      }

  //ES6
  def upsertEnrolment(enrolment: EnrolmentDetailsTestOnly)(implicit hc: HeaderCarrier): TEAFResult[Unit] = {
    val verifiers = Json.obj(
      "verifiers" -> Json.toJson(enrolment.verifiers)
    )

    if (enrolment.identifiers.length != 1) throw new RuntimeException("There should be exactly one identifier")

    val identifierKey = enrolment.identifiers.head.key
    val identifierValue = enrolment.identifiers.head.value

    EitherT(
      httpClient.PUT[JsObject, Either[UpstreamErrorResponse, HttpResponse]](
        s"${appConfig.EACD_BASE_URL}/enrolment-store/enrolments/${enrolment.serviceName}~$identifierKey~$identifierValue",
        verifiers
      )
    )
      .transform {
        case Right(response) if response.status == NO_CONTENT => Right(())
        case Right(response)                                  => Left(UpstreamError(UpstreamErrorResponse(response.body, response.status)))
        case Left(error)                                      => Left(UpstreamError(error))
      }
  }

  //ES8
  def addEnrolmentToGroup(groupId: String, credId: String, enrolment: EnrolmentDetailsTestOnly)(implicit
    hc: HeaderCarrier
  ): TEAFResult[Unit] = {
    val payload = Json.obj(
      "userId" -> credId,
      "type"   -> "principal",
      "action" -> "enrolOnly"
    )

    if (enrolment.identifiers.length != 1) throw new RuntimeException("There should be exactly one identifier")

    val identifierKey = enrolment.identifiers.head.key
    val identifierValue = enrolment.identifiers.head.value

    EitherT(
      httpClient.POST[JsObject, Either[UpstreamErrorResponse, HttpResponse]](
        s"${appConfig.EACD_BASE_URL}/enrolment-store/groups/$groupId/enrolments/${enrolment.serviceName}~$identifierKey~$identifierValue",
        payload
      )
    )
      .transform {
        case Right(response) if response.status == CREATED => Right(())
        case Right(response)                               => Left(UpstreamError(UpstreamErrorResponse(response.body, response.status)))
        case Left(error)                                   => Left(UpstreamError(error))
      }
  }

  //ES9
  def deleteEnrolmentFromGroup(enrolmentKey: String, groupId: String)(implicit hc: HeaderCarrier): TEAFResult[Unit] =
    EitherT(
      httpClient.DELETE[Either[UpstreamErrorResponse, HttpResponse]](
        s"${appConfig.EACD_BASE_URL}/enrolment-store/groups/$groupId/enrolments/$enrolmentKey"
      )
    )
      .transform {
        case Left(response) if response.statusCode == NOT_FOUND => Right(())
        case Right(response) if response.status == NO_CONTENT   => Right(())
        case Right(response)                                    => Left(UpstreamError(UpstreamErrorResponse(response.body, response.status)))
        case Left(error)                                        => Left(UpstreamError(error))
      }

  //ES12
  def deleteEnrolmentFromUser(enrolmentKey: String, credId: String)(implicit hc: HeaderCarrier): TEAFResult[Unit] =
    EitherT(
      httpClient.DELETE[Either[UpstreamErrorResponse, HttpResponse]](
        s"${appConfig.EACD_BASE_URL}/enrolment-store/users/$credId/enrolments/$enrolmentKey"
      )
    )
      .transform {
        case Left(response) if response.statusCode == NOT_FOUND => Right(())
        case Right(response) if response.status == NO_CONTENT   => Right(())
        case Right(response)                                    => Left(UpstreamError(UpstreamErrorResponse(response.body, response.status)))
        case Left(error)                                        => Left(UpstreamError(error))
      }

  //ES7
  def deleteEnrolment(enrolmentKey: String)(implicit hc: HeaderCarrier): TEAFResult[Unit] =
    EitherT(
      httpClient.DELETE[Either[UpstreamErrorResponse, HttpResponse]](
        s"${appConfig.EACD_BASE_URL}/enrolment-store/enrolments/$enrolmentKey"
      )
    )
      .transform {
        case Left(response) if response.statusCode == NOT_FOUND => Right(())
        case Right(response) if response.status == NO_CONTENT   => Right(())
        case Right(response)                                    => Left(UpstreamError(UpstreamErrorResponse(response.body, response.status)))
        case Left(error)                                        => Left(UpstreamError(error))
      }

}
