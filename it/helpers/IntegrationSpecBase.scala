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

import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, containing, equalTo, equalToJson, get, post, postRequestedFor, put, stubFor, urlEqualTo, urlMatching, urlPathEqualTo, verify}
import com.github.tomakehurst.wiremock.matching.StringValuePattern
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import helpers.TestITData._
import org.mockito.MockitoSugar
import org.mongodb.scala.bson.BsonDocument
import org.mongodb.scala.model.Filters
import org.scalatest.concurrent.{Eventually, IntegrationPatience, PatienceConfiguration, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, GivenWhenThen, OneInstancePerTest, Suite, TestSuite}
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.{GuiceOneAppPerSuite, GuiceOneServerPerSuite}
import play.api.http.Status.{INTERNAL_SERVER_ERROR, NON_AUTHORITATIVE_INFORMATION}
import play.api.i18n.{Lang, Messages, MessagesApi}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{Format, JsString, JsValue, Json}
import play.api.mvc.{AnyContent, CookieHeaderEncoding, Request, Session, SessionCookieBaker}
import play.api.test.Helpers.await
import play.api.test.{DefaultAwaitTimeout, FakeRequest, FutureAwaits, Injecting}
import play.api.{Application, Environment, Mode}
import uk.gov.hmrc.auth.core.AuthProvider.GovernmentGateway
import uk.gov.hmrc.auth.core.{AuthProviders, ConfidenceLevel}
import uk.gov.hmrc.crypto.PlainText
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.http.{Authorization, HeaderCarrier}
import uk.gov.hmrc.play.bootstrap.binders.RedirectUrl
import uk.gov.hmrc.play.bootstrap.frontend.filters.crypto.SessionCookieCrypto
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes.SA_ASSIGNED_TO_OTHER_USER
import uk.gov.hmrc.taxenrolmentassignmentfrontend.config.AppConfig
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.actions.{AccountDetailsFromMongo, RequestWithUserDetailsFromSession, RequestWithUserDetailsFromSessionAndMongo, UserDetailsFromSession}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.{routes, testOnly}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.{DatedCacheMap, UsersGroupResponse}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.reporting.AuditEvent
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.{CascadeUpsert, DefaultTEASessionCache, TEASessionCache}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.SessionKeys.{ACCOUNT_TYPE, REDIRECT_URL}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.services.TENCrypto
import uk.gov.hmrc.taxenrolmentassignmentfrontend.views.html.templates.ErrorTemplate
import play.api.inject.bind
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport
import uk.gov.hmrc.taxenrolmentassignmentfrontend.config.HmrcModule
import uk.gov.hmrc.domain.{Nino, Generator => NinoGenerator}

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}

trait IntegrationSpecBase
  extends AnyWordSpec with GuiceOneAppPerSuite with Matchers with PatienceConfiguration with BeforeAndAfterEach
    with ScalaFutures with Injecting with IntegrationPatience with SessionCacheOperations with WireMockHelper {

  def generateNino: Nino = new NinoGenerator().nextNino

  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.global
  lazy implicit val hc: HeaderCarrier = HeaderCarrier(
    authorization = Some(Authorization(AUTHORIZE_HEADER_VALUE))
  )
  lazy val crypto = app.injector.instanceOf[TENCrypto]

  lazy val config: Map[String, Any] = Map(
    "play.filters.csrf.header.bypassHeaders.Csrf-Token" -> "nocheck",
    "auditing.consumer.baseUri.port" -> server.port(),
    "microservice.services.auth.port" -> server.port(),
    "microservice.services.auth.isTest" -> "false",
    "microservice.services.identity-verification.port" -> server.port(),
    "microservice.services.enrolment-store-proxy.port" -> server.port(),
    "microservice.services.tax-enrolments.port" -> server.port(),
    "microservice.services.tax-enrolments.isTest" -> "false",
    "microservice.services.users-groups-search.port" -> server.port(),
    "microservice.services.users-groups-search.isTest" -> "false",
    "play.http.router" -> "testOnlyDoNotUseInAppConf.Routes",
    "throttle.percentage" -> "3",
    "mongodb.uri" -> mongoUri
  )

  protected def localGuiceApplicationBuilder =
    GuiceApplicationBuilder()
      .configure(config)

  override implicit lazy val app: Application =
    localGuiceApplicationBuilder
      .build()

  lazy val teaHost = s"localhost:${server.port()}"

  lazy val returnUrl: String = testOnly.routes.TestOnlyController.successfulCall
    .absoluteURL(false, teaHost)

  lazy val accountCheckPath =
    routes.AccountCheckController.accountCheck(RedirectUrl.apply(returnUrl)).url

  val exampleMongoSessionData = Map(ACCOUNT_TYPE -> Json.toJson(SA_ASSIGNED_TO_OTHER_USER), REDIRECT_URL -> JsString("redirectURL"))

  def requestWithAccountType(
                              accountType: AccountTypes.Value,
                              redirectUrl: String = returnUrl,
                              mongoCacheData: Map[String, JsValue] = exampleMongoSessionData,
                            ): RequestWithUserDetailsFromSessionAndMongo[_] =
    RequestWithUserDetailsFromSessionAndMongo(
      FakeRequest().asInstanceOf[Request[AnyContent]],
      userDetailsNoEnrolments,
      sessionId,
      AccountDetailsFromMongo(accountType, redirectUrl, mongoCacheData)(crypto.crypto)
    )

  def messagesApi: MessagesApi = {
    app.injector.instanceOf[MessagesApi]
  }

  implicit lazy val messages: Messages = messagesApi.preferred(List(Lang("en")))
}
