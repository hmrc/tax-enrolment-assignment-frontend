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
import play.api.http.Status.{CREATED, NOT_FOUND, NO_CONTENT, OK}
import play.api.libs.json.{JsArray, JsObject, Json}
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpResponse, UpstreamErrorResponse}
import uk.gov.hmrc.service.TEAFResult
import uk.gov.hmrc.taxenrolmentassignmentfrontend.config.AppConfig
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.{UpstreamError, UpstreamUnexpected2XX}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.testOnly.models.EnrolmentDetailsTestOnly

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

        case Right(response) => Left(UpstreamUnexpected2XX(response.body, response.status))
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

        case Right(response) => Left(UpstreamUnexpected2XX(response.body, response.status))
        case Left(error)     => Left(UpstreamError(error))
      }

  //ES2
  def getEnrolmentsFromUser(credId: String)(implicit hc: HeaderCarrier): TEAFResult[List[String]] =
    EitherT(
      httpClient.GET[Either[UpstreamErrorResponse, HttpResponse]](
        s"${appConfig.EACD_BASE_URL}/enrolment-store/users/$credId/enrolments"
      )
    )
      .transform {
        case Right(response) if response.status == NO_CONTENT => Right(List.empty)
        case Right(response) if response.status == OK =>
          Right((response.json \ "enrolments").as[JsArray].value.toList.map { enrolment =>
            val service = (enrolment \ "service").as[String]
            val identifier = (enrolment \ "identifiers").as[JsArray].value.toList.head
            val key = (identifier \ "key").as[String]
            val value = (identifier \ "value").as[String]
            s"$service~$key~$value"
          })

        case Right(response) => Left(UpstreamUnexpected2XX(response.body, response.status))
        case Left(error)     => Left(UpstreamError(error))
      }

  //ES3
  def getEnrolmentsFromGroup(groupId: String)(implicit hc: HeaderCarrier): TEAFResult[List[String]] =
    EitherT(
      httpClient.GET[Either[UpstreamErrorResponse, HttpResponse]](
        s"${appConfig.EACD_BASE_URL}/enrolment-store/groups/$groupId/enrolments"
      )
    )
      .transform {
        case Right(response) if response.status == NO_CONTENT => Right(List.empty)
        case Right(response) if response.status == OK =>
          Right((response.json \ "enrolments").as[JsArray].value.toList.map { enrolment =>
            val service = (enrolment \ "service").as[String]
            val identifier = (enrolment \ "identifiers").as[JsArray].value.toList.head
            val key = (identifier \ "key").as[String]
            val value = (identifier \ "value").as[String]
            s"$service~$key~$value"
          })

        case Right(response) => Left(UpstreamUnexpected2XX(response.body, response.status))
        case Left(error)     => Left(UpstreamError(error))
      }

  //ES6
  def upsertEnrolment(enrolment: EnrolmentDetailsTestOnly)(implicit hc: HeaderCarrier): TEAFResult[Unit] = {
    val verifiers = Json.obj(
      "verifiers" -> Json.toJson(enrolment.verifiers)
    )

    val identifierKey = enrolment.identifiers.key
    val identifierValue = enrolment.identifiers.value

    EitherT(
      httpClient.PUT[JsObject, Either[UpstreamErrorResponse, HttpResponse]](
        s"${appConfig.EACD_BASE_URL}/enrolment-store/enrolments/${enrolment.serviceName}~$identifierKey~$identifierValue",
        verifiers
      )
    )
      .transform {
        case Right(response) if response.status == NO_CONTENT => Right(())
        case Right(response)                                  => Left(UpstreamUnexpected2XX(response.body, response.status))
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

    val identifierKey = enrolment.identifiers.key
    val identifierValue = enrolment.identifiers.value

    EitherT(
      httpClient.POST[JsObject, Either[UpstreamErrorResponse, HttpResponse]](
        s"${appConfig.EACD_BASE_URL}/enrolment-store/groups/$groupId/enrolments/${enrolment.serviceName}~$identifierKey~$identifierValue",
        payload
      )
    )
      .transform {
        case Right(response) if response.status == CREATED => Right(())
        case Right(response)                               => Left(UpstreamUnexpected2XX(response.body, response.status))
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
        case Right(response)                                    => Left(UpstreamUnexpected2XX(response.body, response.status))
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
        case Right(response)                                    => Left(UpstreamUnexpected2XX(response.body, response.status))
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
        case Right(response)                                    => Left(UpstreamUnexpected2XX(response.body, response.status))
        case Left(error)                                        => Left(UpstreamError(error))
      }

}
