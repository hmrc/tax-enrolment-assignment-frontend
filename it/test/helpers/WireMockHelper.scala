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

package helpers

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import helpers.TestITData.usergroupsResponseJson
import org.scalatest.concurrent.Eventually
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, Suite}
import play.api.http.Status.{INTERNAL_SERVER_ERROR, NON_AUTHORITATIVE_INFORMATION, OK}
import play.api.libs.json.{JsString, Json}
import uk.gov.hmrc.auth.core.AuthProvider.GovernmentGateway
import uk.gov.hmrc.auth.core.{AuthProviders, ConfidenceLevel}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.UsersGroupResponse
import uk.gov.hmrc.taxenrolmentassignmentfrontend.reporting.AuditEvent

trait WireMockHelper extends Eventually with BeforeAndAfterAll with BeforeAndAfterEach {
  this: Suite =>

  protected val server: WireMockServer = new WireMockServer(wireMockConfig().dynamicPort())

  override def beforeAll(): Unit = {
    super.beforeAll()
    server.start()
  }

  override def beforeEach(): Unit = {
    super.beforeEach()
    server.resetAll()
    server.stubFor(
      post(urlMatching("/write/audit/merged"))
        .willReturn(
          aResponse()
            .withStatus(OK)
            .withHeader("Content-Type", "application/json")
            .withBody("{}")
        )
    )
    stubFor(
      any(anyUrl())
        .atPriority(10)
        .willReturn(
          aResponse()
            .withStatus(INTERNAL_SERVER_ERROR)
        )
    )
  }

  override def afterAll(): Unit = {
    super.afterAll()
    server.stop()
  }

  def stubGetMatching(url: String, status: Integer, responseBody: String): StubMapping =
    server.stubFor(
      get(urlMatching(url))
        .willReturn(aResponse().withStatus(status).withBody(responseBody))
    )

  def stubAuthorizePost(status: Integer, responseBody: String): StubMapping = {
    val authorizePath = "/auth/authorise"
    val jsonRequest = Json.obj(
      "authorise" -> (AuthProviders(GovernmentGateway) and ConfidenceLevel.L200).toJson,
      "retrieve" -> Json.arr(
        JsString("nino"),
        JsString("optionalCredentials"),
        JsString("allEnrolments"),
        JsString("groupIdentifier"),
        JsString("affinityGroup"),
        JsString("email")
      )
    )
    stubPost(
      authorizePath,
      jsonRequest.toString(),
      status,
      responseBody
    )
  }

  def stubAuthorizePostUnauthorised(failureReason: String): StubMapping = {
    val failureReasonMsg = s"""MDTP detail=\"$failureReason\""""
    server.stubFor(
      post(urlMatching("/auth/authorise"))
        .willReturn(
          aResponse()
            .withStatus(401)
            .withHeader("WWW-Authenticate", failureReasonMsg)
        )
    )
  }

  def stubPost(url: String, requestBody: String, status: Integer, responseBody: String): StubMapping =
    server.stubFor(
      post(urlMatching(url))
        .withRequestBody(
          equalToJson(requestBody)
        )
        .willReturn(
          aResponse()
            .withStatus(status)
            .withHeader("Content-Type", "application/json")
            .withBody(responseBody)
        )
    )

  def stubDelete(url: String, status: Integer, body: String = ""): StubMapping =
    server.stubFor(
      delete(urlMatching(url))
        .willReturn(
          aResponse()
            .withStatus(status)
            .withBody(body)
        )
    )

  def stubPostWithAuthorizeHeaders(url: String, authorizeHeaderValue: String, status: Integer): StubMapping =
    server.stubFor(
      post(urlMatching(url))
        .withHeader("Authorization", equalTo(authorizeHeaderValue))
        .willReturn(
          aResponse()
            .withStatus(status)
        )
    )

  def stubPutWithAuthorizeHeaders(url: String, authorizeHeaderValue: String, status: Integer): StubMapping =
    server.stubFor(
      put(urlPathEqualTo(url))
        .withHeader("Authorization", equalTo(authorizeHeaderValue))
        .willReturn(
          aResponse()
            .withStatus(status)
        )
    )

  def stubPost(url: String, status: Integer, responseBody: String): StubMapping =
    server.stubFor(
      post(urlMatching(url))
        .willReturn(aResponse().withStatus(status).withBody(responseBody))
    )

  def stubPut(url: String, status: Integer, responseBody: String): StubMapping =
    server.stubFor(
      put(urlMatching(url))
        .willReturn(aResponse().withStatus(status).withBody(responseBody))
    )
  def stubPutWithRequestBody(url: String, status: Integer, requestBody: String, responseBody: String): StubMapping =
    server.stubFor(
      put(urlMatching(url))
        .withRequestBody(equalToJson(requestBody))
        .willReturn(aResponse().withStatus(status).withBody(responseBody))
    )
  def stubGetWithQueryParam(
    url: String,
    queryParamKey: String,
    queryParamValue: String,
    status: Integer,
    responseBody: String
  ): StubMapping =
    server.stubFor(
      get(urlPathEqualTo(url))
        .withQueryParam(queryParamKey, equalTo(queryParamValue))
        .willReturn(aResponse().withStatus(status).withBody(responseBody))
    )

  def stubGet(url: String, status: Integer, responseBody: String, delay: Int = 1): StubMapping =
    server.stubFor(
      get(urlPathEqualTo(url))
        .willReturn(
          aResponse()
            .withStatus(status)
            .withBody(responseBody)
            .withFixedDelay(delay)
        )
    )

  def verifyNoPOSTmade(url: String): Unit =
    eventually(server.verify(0, postRequestedFor(urlMatching(url))))
  def verifyAuditEventSent(auditEvent: AuditEvent): Unit =
    eventually(
      server.verify(
        postRequestedFor(urlMatching("/write/audit"))
          .withRequestBody(
            containing(s""""auditType":"${auditEvent.auditType}"""")
          )
          .withRequestBody(containing(auditEvent.detail.toString()))
      )
    )

  def stubUserGroupSearchSuccess(
    credId: String,
    usersGroupResponse: UsersGroupResponse
  ): StubMapping = stubGet(
    s"/users-groups-search/users/$credId",
    NON_AUTHORITATIVE_INFORMATION,
    usergroupsResponseJson(usersGroupResponse).toString()
  )

  def stubUserGroupSearchFailure(credId: String): StubMapping =
    stubGet(s"/users-groups-search/users/$credId", INTERNAL_SERVER_ERROR, "")

}
