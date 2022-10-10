/*
 * Copyright 2022 HM Revenue & Customs
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
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.github.tomakehurst.wiremock.matching.StringValuePattern
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatestplus.play.ServerProvider
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.libs.json.{JsString, Json}
import play.api.libs.ws.{WSClient, WSRequest}
import uk.gov.hmrc.auth.core.AuthProvider.GovernmentGateway
import uk.gov.hmrc.auth.core.{AuthProviders, ConfidenceLevel}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.reporting.AuditEvent

object WiremockHelper extends Eventually with IntegrationPatience {
  val wiremockPort = 1111
  val wiremockHost = "localhost"
  val wiremockURL = s"http://$wiremockHost:$wiremockPort"

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
      equalToJson(jsonRequest.toString()),
      status,
      responseBody
    )
  }

  def stubAuthorizePostUnauthorised(failureReason: String): StubMapping = {
    val failureReasonMsg = s"""MDTP detail=\"$failureReason\""""
    stubFor(
      post(urlMatching("/auth/authorise"))
        .willReturn(
          aResponse()
            .withStatus(401)
            .withHeader("WWW-Authenticate", failureReasonMsg)
        )
    )
  }

  def stubPost(url: String,
               requestBody: StringValuePattern,
               status: Integer,
               responseBody: String): StubMapping =
    stubFor(
      post(urlMatching(url))
        .withRequestBody(requestBody)
        .willReturn(
          aResponse()
            .withStatus(status)
            .withHeader("Content-Type", "application/json")
            .withBody(responseBody)
        )
    )

  def stubPostWithAuthorizeHeaders(url: String,
                                   authorizeHeaderValue: String,
                                   status: Integer): StubMapping =
    stubFor(
      post(urlMatching(url))
        .withHeader("Authorization", equalTo(authorizeHeaderValue))
        .willReturn(
          aResponse()
            .withStatus(status)
        )
    )

  def stubPutWithAuthorizeHeaders(url: String,
                                  authorizeHeaderValue: String,
                                  status: Integer): StubMapping =
    stubFor(
      put(urlPathEqualTo(url))
        .withHeader("Authorization", equalTo(authorizeHeaderValue))
        .willReturn(
          aResponse()
            .withStatus(status)
        )
    )

  def stubPost(url: String,
               status: Integer,
               responseBody: String): StubMapping =
    stubFor(
      post(urlMatching(url))
        .willReturn(aResponse().withStatus(status).withBody(responseBody))
    )

  def stubPut(url: String, status: Integer, responseBody: String): StubMapping =
    stubFor(
      put(urlMatching(url))
        .willReturn(aResponse().withStatus(status).withBody(responseBody))
    )
  def stubPutWithRequestBody(url: String,
                             status: Integer,
                             requestBody: String,
                             responseBody: String): StubMapping =
    stubFor(
      put(urlMatching(url))
        .withRequestBody(equalToJson(requestBody))
        .willReturn(aResponse().withStatus(status).withBody(responseBody))
    )
  def stubGetWithQueryParam(url: String,
                            queryParamKey: String,
                            queryParamValue: String,
                            status: Integer,
                            responseBody: String): StubMapping =
    stubFor(
      get(urlPathEqualTo(url))
        .withQueryParam(queryParamKey, equalTo(queryParamValue))
        .willReturn(aResponse().withStatus(status).withBody(responseBody))
    )

  def stubGet(url: String, status: Integer, responseBody: String): StubMapping =
    stubFor(
      get(urlPathEqualTo(url))
        .willReturn(aResponse().withStatus(status).withBody(responseBody))
    )

  def stubGetMatching(url: String,
                      status: Integer,
                      responseBody: String): StubMapping =
    stubFor(
      get(urlEqualTo(url))
        .willReturn(aResponse().withStatus(status).withBody(responseBody))
    )

  def verifyNoPOSTmade(url: String) = {
    eventually(
      verify(0,
        postRequestedFor(urlMatching(url))
      ))
  }
  def verifyAuditEventSent(auditEvent: AuditEvent) = {
    eventually(
      verify(
        postRequestedFor(urlMatching("/write/audit"))
          .withRequestBody(
            containing(s""""auditType":"${auditEvent.auditType}"""")
          )
          .withRequestBody(containing(auditEvent.detail.toString()))
      )
    )
  }
}

trait WiremockHelper extends ServerProvider {
  self: GuiceOneServerPerSuite =>

  import WiremockHelper._

  lazy val ws: WSClient = app.injector.instanceOf[WSClient]

  lazy val wmConfig: WireMockConfiguration = wireMockConfig().port(wiremockPort)
  lazy val wireMockServer = new WireMockServer(wmConfig)

  def startWiremock(): Unit = {
    WireMock.configureFor(wiremockHost, wiremockPort)
    wireMockServer.start()
  }

  def stopWiremock(): Unit = wireMockServer.stop()

  def resetWiremock(): Unit = WireMock.reset()

  def buildRequest(path: String,
                   followRedirects: Boolean = false): WSRequest = {
    ws.url(s"http://localhost:$port$path")
      .withFollowRedirects(followRedirects)
  }

  def buildTestOnlyRequest(path: String,
                           followRedirects: Boolean = false): WSRequest =
    ws.url(s"http://localhost:$port$path")
      .withFollowRedirects(followRedirects)
}
