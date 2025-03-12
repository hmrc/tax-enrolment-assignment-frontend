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
import play.api.http.Status.{BAD_REQUEST, CREATED, NOT_FOUND, NO_CONTENT, OK}
import play.api.libs.json.{JsArray, JsObject, Json}
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps, UpstreamErrorResponse}
import uk.gov.hmrc.service.TEAFResult
import uk.gov.hmrc.taxenrolmentassignmentfrontend.config.AppConfig
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.{UpstreamError, UpstreamUnexpected2XX}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.IdentifiersOrVerifiers
import uk.gov.hmrc.taxenrolmentassignmentfrontend.testOnly.models.EnrolmentDetailsTestOnly

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class EnrolmentStoreConnectorTestOnly @Inject() (httpClient: HttpClientV2, appConfig: AppConfig)(implicit
  ec: ExecutionContext
) extends Logging {
  //ES0
  def deleteGroup(groupId: String)(implicit hc: HeaderCarrier): TEAFResult[Unit] = {
    val url = s"${appConfig.EACD_BASE_URL_TESTONLY}/enrolment-store/data/$groupId"
    EitherT(
      httpClient
        .delete(url"$url")
        .execute[Either[UpstreamErrorResponse, HttpResponse]]
    )
      .transform {
        case Right(response) if response.status == NO_CONTENT => Right(())
        case Right(response) =>
          val ex = new RuntimeException(s"Unexpected ${response.status} status")
          logger.error(ex.getMessage, ex)
          Left(UpstreamUnexpected2XX(response.body, response.status))
        case Left(upstreamError) if upstreamError.statusCode == NOT_FOUND => Right(())
        case Left(upstreamError) =>
          logger.error(upstreamError.message)
          Left(UpstreamError(upstreamError))
      }
  }

  //ES0
  def getUsersFromEnrolment(enrolmentKey: String)(implicit hc: HeaderCarrier): TEAFResult[List[String]] = {
    val url = s"${appConfig.EACD_BASE_URL}/enrolment-store/enrolments/$enrolmentKey/users"
    EitherT(
      httpClient
        .get(url"$url")
        .execute[Either[UpstreamErrorResponse, HttpResponse]]
    )
      .transform {
        case Right(response) if response.status == NO_CONTENT => Right(List.empty)
        case Right(response) =>
          val principals = (response.json \ "principalUserIds").as[List[String]]
          val delegated = (response.json \ "delegatedUserIds").as[List[String]]
          Right(principals ++ delegated)

        case Left(upstreamError) =>
          logger.error(upstreamError.message)
          Left(UpstreamError(upstreamError))
      }
  }

  //ES1
  def getGroupsFromEnrolment(enrolmentKey: String)(implicit hc: HeaderCarrier): TEAFResult[List[String]] = {
    val url = s"${appConfig.EACD_BASE_URL}/enrolment-store/enrolments/$enrolmentKey/groups"
    EitherT(
      httpClient.get(url"$url").execute[Either[UpstreamErrorResponse, HttpResponse]]
    )
      .transform {
        case Right(response) if response.status == NO_CONTENT => Right(List.empty)
        case Right(response) =>
          val principals = (response.json \ "principalGroupIds").as[List[String]]
          val delegated = (response.json \ "delegatedGroupIds").as[List[String]]
          Right(principals ++ delegated)

        case Left(upstreamError) =>
          logger.error(upstreamError.message)
          Left(UpstreamError(upstreamError))
      }
  }

  //ES2
  def getEnrolmentsFromUser(credId: String)(implicit hc: HeaderCarrier): TEAFResult[List[String]] = {
    val url = s"${appConfig.EACD_BASE_URL}/enrolment-store/users/$credId/enrolments"
    EitherT(
      httpClient.get(url"$url").execute[Either[UpstreamErrorResponse, HttpResponse]]
    )
      .transform {
        case Right(response) if response.status == NO_CONTENT => Right(List.empty)
        case Right(response) =>
          Right((response.json \ "enrolments").as[JsArray].value.toList.map { enrolment =>
            val service = (enrolment \ "service").as[String]
            val identifier = (enrolment \ "identifiers").as[JsArray].value.toList.head
            val key = (identifier \ "key").as[String]
            val value = (identifier \ "value").as[String]
            s"$service~$key~$value"
          })
        case Left(upstreamError) if upstreamError.statusCode == NOT_FOUND => Right(List.empty)
        case Left(upstreamError) =>
          logger.error(upstreamError.message)
          Left(UpstreamError(upstreamError))
      }
  }

  //ES3
  def getEnrolmentsFromGroup(groupId: String)(implicit hc: HeaderCarrier): TEAFResult[List[String]] = {
    val url = s"${appConfig.EACD_BASE_URL}/enrolment-store/groups/$groupId/enrolments"
    EitherT(
      httpClient.get(url"$url").execute[Either[UpstreamErrorResponse, HttpResponse]]
    )
      .transform {
        case Right(response) if response.status == NO_CONTENT => Right(List.empty)
        case Right(response) =>
          Right((response.json \ "enrolments").as[JsArray].value.toList.map { enrolment =>
            val service = (enrolment \ "service").as[String]
            val identifier = (enrolment \ "identifiers").as[JsArray].value.toList.head
            val key = (identifier \ "key").as[String]
            val value = (identifier \ "value").as[String]
            s"$service~$key~$value"
          })
        case Left(upstreamError) if upstreamError.statusCode == NOT_FOUND => Right(List.empty)
        case Left(upstreamError) =>
          logger.error(upstreamError.message)
          Left(UpstreamError(upstreamError))
      }
  }

  //ES6
  def upsertEnrolment(enrolment: EnrolmentDetailsTestOnly)(implicit hc: HeaderCarrier): TEAFResult[Unit] = {
    val verifiers = Json.obj(
      "verifiers" -> Json.toJson(enrolment.verifiers)
    )

    val identifierKey = enrolment.identifiers.key
    val identifierValue = enrolment.identifiers.value

    val url =
      s"${appConfig.EACD_BASE_URL}/enrolment-store/enrolments/${enrolment.serviceName}~$identifierKey~$identifierValue"

    EitherT(
      httpClient.put(url"$url").withBody(verifiers).execute[Either[UpstreamErrorResponse, HttpResponse]]
    )
      .transform {
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

  //ES8
  def addEnrolmentToGroup(groupId: String, credId: String, enrolment: EnrolmentDetailsTestOnly)(implicit
    hc: HeaderCarrier
  ): TEAFResult[Unit] = {
    val payload = Json.obj(
      "userId" -> credId,
      "type"   -> "principal",
      "action" -> "enrolAndActivate"
    )

    val identifierKey = enrolment.identifiers.key
    val identifierValue = enrolment.identifiers.value

    val url =
      s"${appConfig.EACD_BASE_URL}/enrolment-store/groups/$groupId/enrolments/${enrolment.serviceName}~$identifierKey~$identifierValue"

    EitherT(
      httpClient
        .post(url"$url")
        .withBody(payload)
        .execute[Either[UpstreamErrorResponse, HttpResponse]]
    )
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

  //ES9
  def deleteEnrolmentFromGroup(enrolmentKey: String, groupId: String)(implicit hc: HeaderCarrier): TEAFResult[Unit] = {
    val url = s"${appConfig.EACD_BASE_URL}/enrolment-store/groups/$groupId/enrolments/$enrolmentKey"
    EitherT(
      httpClient.delete(url"$url").execute[Either[UpstreamErrorResponse, HttpResponse]]
    )
      .transform {
        case Left(response) if response.statusCode == NOT_FOUND => Right(())
        case Left(response) if response.statusCode == BAD_REQUEST && response.message.contains("INVALID_SERVICE") =>
          Right(())
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

  //ES12
  def deleteEnrolmentFromUser(enrolmentKey: String, credId: String)(implicit hc: HeaderCarrier): TEAFResult[Unit] = {
    val url = s"${appConfig.EACD_BASE_URL}/enrolment-store/users/$credId/enrolments/$enrolmentKey"
    EitherT(
      httpClient
        .delete(
          url"$url"
        )
        .execute[Either[UpstreamErrorResponse, HttpResponse]]
    )
      .transform {
        case Left(response) if response.statusCode == NOT_FOUND => Right(())
        case Right(response) if response.status == NO_CONTENT   => Right(())
        case Right(response) =>
          val ex = new RuntimeException(s"Unexpected ${response.status} status")
          logger.error(ex.getMessage, ex)
          Left(UpstreamUnexpected2XX(response.body, response.status))
        case Left(upstreamError)
            if upstreamError.statusCode == BAD_REQUEST && upstreamError.message.contains("INVALID_SERVICE") =>
          logger.error(upstreamError.message)
          Right(())
        case Left(upstreamError) =>
          logger.error(upstreamError.message)
          Left(UpstreamError(upstreamError))
      }
  }

  //ES7
  def deleteEnrolment(enrolmentKey: String)(implicit hc: HeaderCarrier): TEAFResult[Unit] = {
    val url = s"${appConfig.EACD_BASE_URL}/enrolment-store/enrolments/$enrolmentKey"
    EitherT(
      httpClient.delete(url"$url").execute[Either[UpstreamErrorResponse, HttpResponse]]
    )
      .transform {
        case Left(response) if response.statusCode == NOT_FOUND => Right(())
        case Right(response) if response.status == NO_CONTENT   => Right(())
        case Right(response) =>
          val ex = new RuntimeException(s"Unexpected ${response.status} status")
          logger.error(ex.getMessage, ex)
          Left(UpstreamUnexpected2XX(response.body, response.status))
        case Left(upstreamError) =>
          logger.error(upstreamError.message)
          Left(UpstreamError(upstreamError))
      }
  }

  //ES20 Query known facts that match the supplied query parameters
  def queryKnownFactsByVerifiers(service: String, identifiersOrVerifiers: List[IdentifiersOrVerifiers])(implicit
    ec: ExecutionContext,
    hc: HeaderCarrier
  ): TEAFResult[List[String]] = {
    val url = s"${appConfig.EACD_BASE_URL}/enrolment-store/enrolments"

    val requestBody = Json.obj(
      "service"    -> service,
      "knownFacts" -> Json.toJson(identifiersOrVerifiers)
    )

    EitherT(
      httpClient
        .post(url"$url")
        .withBody(requestBody)
        .execute[Either[UpstreamErrorResponse, HttpResponse]]
    )
      .transform {
        case Right(response) if response.status == OK =>
          Right((response.json \ "enrolments").as[List[JsObject]].map { enrolment =>
            val key = (enrolment \ "identifiers" \\ "key").head.as[String]
            val value = (enrolment \ "identifiers" \\ "value").head.as[String]
            s"$service~$key~$value"
          })
        case Right(response) if response.status == NO_CONTENT => Right(List.empty)
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
