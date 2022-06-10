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

import helpers.TestITData._
import helpers.WiremockHelper.wiremockURL
import org.mongodb.scala.bson.BsonDocument
import org.scalatest.concurrent.{Eventually, IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, GivenWhenThen, TestSuite}
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.{CookieHeaderEncoding, Session, SessionCookieBaker}
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}
import play.api.{Application, Environment, Mode}
import uk.gov.hmrc.crypto.PlainText
import uk.gov.hmrc.http.{Authorization, HeaderCarrier}
import uk.gov.hmrc.play.bootstrap.frontend.filters.crypto.SessionCookieCrypto
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.{CascadeUpsert, SessionRepository}

import scala.concurrent.ExecutionContext

trait IntegrationSpecBase
    extends AnyWordSpec
    with GivenWhenThen
    with TestSuite
    with ScalaFutures
    with IntegrationPatience
    with Matchers
    with WiremockHelper
    with GuiceOneServerPerSuite
    with BeforeAndAfterEach
    with BeforeAndAfterAll
    with Eventually
    with FutureAwaits
    with DefaultAwaitTimeout
    with SessionCacheOperations {

  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.global
  lazy implicit val hc: HeaderCarrier = HeaderCarrier(
    authorization = Some(Authorization(AUTHORIZE_HEADER_VALUE))
  )

  val mockHost: String = WiremockHelper.wiremockHost
  val mockPort: Int = WiremockHelper.wiremockPort
  val mockUrl = s"http://$mockHost:$mockPort"

  def config: Map[String, String] = Map(
    "play.filters.csrf.header.bypassHeaders.Csrf-Token" -> "nocheck",
    "auditing.consumer.baseUri.host" -> s"$mockHost",
    "auditing.consumer.baseUri.port" -> s"$mockPort",
    "microservice.services.auth.host" -> s"$mockHost",
    "microservice.services.auth.port" -> s"$mockPort",
    "microservice.services.identity-verification.host" -> s"$mockHost",
    "microservice.services.identity-verification.port" -> s"$mockPort",
    "microservice.services.enrolment-store-proxy.host" -> s"$mockHost",
    "microservice.services.enrolment-store-proxy.port" -> s"$mockPort",
    "microservice.services.tax-enrolments.host" -> s"$mockHost",
    "microservice.services.tax-enrolments.port" -> s"$mockPort",
    "microservice.services.bas-gateway-frontend.host" -> s"$wiremockURL",
    "microservice.services.add-taxes-frontend.host" -> s"$mockHost",
    "microservice.services.add-taxes-frontend.port" -> s"$mockPort",
    "microservice.services.add-taxes-frontend.isTest" -> "false",
    "microservice.services.users-groups-search.host" -> s"$mockHost",
    "microservice.services.users-groups-search.port" -> s"$mockPort",
    "microservice.services.users-groups-search.isTest" -> "false",
    "play.http.router" -> "testOnlyDoNotUseInAppConf.Routes",
    "microservice.services.features.taxEnrolmentsServiceListLocal" -> "false",
    "throttle.percentage" -> "2"
  )

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(
    timeout = scaled(Span(15, Seconds)),
    interval = scaled(Span(200, Millis))
  )

  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .in(Environment.simple(mode = Mode.Dev))
    .configure(config)
    .build

  val sessionBaker: SessionCookieBaker = app.injector.instanceOf[SessionCookieBaker]
  val cookieHeaderEncoding: CookieHeaderEncoding = app.injector.instanceOf[CookieHeaderEncoding]
  val sessionCookieCrypto: SessionCookieCrypto = app.injector.instanceOf[SessionCookieCrypto]

  def createSessionCookieAsString(sessionData: Map[String, String]): String = {
    val sessionCookie = sessionBaker.encodeAsCookie(Session(sessionData))
    val encryptedSessionCookieValue =
      sessionCookieCrypto.crypto.encrypt(PlainText(sessionCookie.value)).value
    val encryptedSessionCookie =
      sessionCookie.copy(value = encryptedSessionCookieValue)
    cookieHeaderEncoding.encodeCookieHeader(Seq(encryptedSessionCookie))
  }
  val sessionRepository: SessionRepository = app.injector.instanceOf[SessionRepository]
  val cascadeUpsert: CascadeUpsert = app.injector.instanceOf[CascadeUpsert]
  val authData = Map("authToken" -> AUTHORIZE_HEADER_VALUE)
  val sessionAndAuth  = Map("authToken" -> AUTHORIZE_HEADER_VALUE, "sessionId" -> sessionId)

  val authCookie: String = createSessionCookieAsString(authData).substring(5)
  val authAndSessionCookie: String = createSessionCookieAsString(sessionAndAuth).substring(5)

  override def beforeEach(): Unit = {
    resetWiremock()
    await(sessionRepository().collection.deleteMany(BsonDocument()).toFuture())
  }

  override def beforeAll(): Unit = {
    super.beforeAll()
    startWiremock()
  }

  override def afterAll(): Unit = {
    stopWiremock()
    super.afterAll()
  }
}
