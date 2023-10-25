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
import play.api.http.Status.{NO_CONTENT, OK}
import play.api.libs.json.{Format, Json}
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpResponse, UpstreamErrorResponse}
import uk.gov.hmrc.service.TEAFResult
import uk.gov.hmrc.taxenrolmentassignmentfrontend.config.testOnly.AppConfigTestOnly
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.UpstreamError
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.EACDEnrolment

import javax.inject.Inject
import scala.concurrent.ExecutionContext

case class Users(
  credId: String,
  name: String = "Default User",
  email: String = "default@example.com",
  credentialRole: String = "Admin",
  description: String = "User Description"
)

case class EACDStubAddData(
  groupId: String,
  affinityGroup: String = "Individual",
  users: List[Users],
  enrolments: List[EACDEnrolment] = List.empty
)

object EACDStubAddData {
  def apply(groupId: String, credId: String): EACDStubAddData = new EACDStubAddData(
    groupId = groupId,
    users = List(Users(credId))
  )

  implicit val format: Format[EACDStubAddData] = Json.format[EACDStubAddData]
}

object Users {
  implicit val format: Format[Users] = Json.format[Users]
}

class EnrolmentStoreStubConnector @Inject() (appConfig: AppConfigTestOnly, httpClient: HttpClient)(implicit
  val executionContext: ExecutionContext
) {

  def addStubAccount(account: EACDStubAddData)(implicit hc: HeaderCarrier): TEAFResult[Unit] = {
    val url = appConfig.enrolmentStoreStub + "/data"

    EitherT(httpClient.POST[EACDStubAddData, Either[UpstreamErrorResponse, HttpResponse]](url, account)).transform {
      case Right(response) if response.status == NO_CONTENT => Right(())
      case Right(response)                                  => Left(UpstreamError(UpstreamErrorResponse(response.body, response.status)))
      case Left(error)                                      => Left(UpstreamError(error))
    }
  }

  def getStubAccount(groupId: String)(implicit hc: HeaderCarrier): TEAFResult[EACDStubAddData] = {
    val url = s"${appConfig.enrolmentStoreStub}/data/group/$groupId"

    EitherT(httpClient.GET[Either[UpstreamErrorResponse, HttpResponse]](url)).transform {
      case Right(response) if response.status == OK => Right(response.json.as[EACDStubAddData])
      case Right(response)                          => Left(UpstreamError(UpstreamErrorResponse(response.body, response.status)))
      case Left(error)                              => Left(UpstreamError(error))
    }
  }
}
