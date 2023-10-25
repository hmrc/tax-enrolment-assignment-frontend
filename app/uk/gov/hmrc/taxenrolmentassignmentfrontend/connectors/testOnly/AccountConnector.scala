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

import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.http.HttpReads.Implicits.readRaw
import uk.gov.hmrc.http.HttpReads.is2xx
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpResponse}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.config.testOnly.AppConfigTestOnly

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class AccountConnector @Inject() (appConfig: AppConfigTestOnly, http: HttpClient)(implicit ec: ExecutionContext) {

  def putAccount(accountPayload: JsObject)(implicit headerCarrier: HeaderCarrier): Future[HttpResponse] = {
    val url = s"${appConfig.basStubsBaseUrl}/bas-stubs/account"
    http.PUT(url, accountPayload).map(mapHttpResponse(url, "PUT", _))
  }

  def postMfaAccount(mfaAccountPayload: JsObject)(implicit headerCarrier: HeaderCarrier): Future[HttpResponse] = {
    val url = s"${appConfig.basStubsBaseUrl}/bas-stubs/credentials/factors"
    http.PUT(url, mfaAccountPayload).map(mapHttpResponse(url, "PUT", _))
  }

  def postToken(tokenPayload: Map[String, Seq[String]])(implicit headerCarrier: HeaderCarrier): Future[HttpResponse] = {
    val url = s"${appConfig.basStubsBaseUrl}/bas-stubs/token"
    http
      .POSTForm(url, tokenPayload, Seq("Content-type" -> "application/x-www-form-urlencoded"))
      .map(mapHttpResponse(url, "POST", _))
  }

  def postKnownFacts(knownFactsPayload: JsObject)(implicit headerCarrier: HeaderCarrier): Future[HttpResponse] = {
    val url = s"${appConfig.esStubBaseUrl}/enrolment-store-stub/known-facts"
    http.POST(url, knownFactsPayload).map(mapHttpResponse(url, "POST", _))
  }

  def postEnrolmentData(enrolmentDataPayload: JsObject)(implicit headerCarrier: HeaderCarrier): Future[HttpResponse] = {
    val url = s"${appConfig.esStubBaseUrl}/enrolment-store-stub/data"
    http.POST(url, enrolmentDataPayload).map(mapHttpResponse(url, "POST", _))
  }

  def putNinoStore(ninoStorePayload: JsObject, credId: String)(implicit
    headerCarrier: HeaderCarrier
  ): Future[HttpResponse] = {
    val url = s"${appConfig.ivBaseUrl}/identity-verification/nino/$credId?updateAuth=false"
    http.PUT(url, ninoStorePayload).map(mapHttpResponse(url, "PUT", _))
  }

  def deleteCredId(credId: String)(implicit hc: HeaderCarrier): Future[HttpResponse] = {
    val url = s"${appConfig.basStubsBaseUrl}/bas-stubs/account/$credId"
    http.DELETE(url).map(mapHttpResponse(url, "DELETE", _))
  }

  def deleteFactors(credId: String)(implicit hc: HeaderCarrier): Future[HttpResponse] = {
    val url = s"${appConfig.basStubsBaseUrl}/bas-stubs/credentials/$credId/factors/additionalfactors"
    http.DELETE(url).map(mapHttpResponse(url, "DELETE", _))
  }

  def deleteGroupId(groupId: String)(implicit hc: HeaderCarrier): Future[HttpResponse] = {
    val url = s"${appConfig.esStubBaseUrl}/enrolment-store-stub/data/group/$groupId"
    http.DELETE(url).map(mapHttpResponse(url, "DELETE", _))
  }

  def deleteKnownFact(deleteKnownFactPayload: JsObject)(implicit hc: HeaderCarrier): Future[HttpResponse] = {
    val url = s"${appConfig.esStubBaseUrl}/enrolment-store-stub/known-fact/delete"
    http.POST(url, deleteKnownFactPayload).map(mapHttpResponse(url, "POST", _))
  }

  def deleteAffinityGroup(groupId: String)(implicit hc: HeaderCarrier): Future[HttpResponse] = {
    val url = s"${appConfig.affinityGroupUrl}/affinity-group/group-records/$groupId"
    http.DELETE(url).map(mapHttpResponse(url, "DELETE", _))
  }

  def addAffinityGroup(groupId: String, accountType: String)(implicit hc: HeaderCarrier): Future[HttpResponse] = {
    val requestBody = Json.obj("groupId" -> groupId, "accountType" -> accountType)
    val url = s"${appConfig.affinityGroupUrl}/affinity-group/groups"
    http.POST(url, requestBody).map(mapHttpResponse(url, "POST", _))
  }

  private def mapHttpResponse(url: String, method: String, response: HttpResponse) =
    response.status match {
      case status if is2xx(status) => response
      case status =>
        throw new Exception(s"$method to $url failed with status $status. Response body: '${response.body}'")
    }

}
