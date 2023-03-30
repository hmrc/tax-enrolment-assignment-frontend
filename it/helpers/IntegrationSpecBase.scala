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
    with ScalaFutures with Injecting with IntegrationPatience with DefaultPlayMongoRepositorySupport[DatedCacheMap] with WireMockHelper {

  def generateNino: Nino = new NinoGenerator().nextNino

  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.global
  lazy implicit val hc: HeaderCarrier = HeaderCarrier(
    authorization = Some(Authorization(AUTHORIZE_HEADER_VALUE))
  )

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

  implicit lazy val crypto = app.injector.instanceOf[TENCrypto]

  lazy val sessionBaker: SessionCookieBaker = app.injector.instanceOf[SessionCookieBaker]
  lazy val cookieHeaderEncoding: CookieHeaderEncoding = app.injector.instanceOf[CookieHeaderEncoding]
  lazy val sessionCookieCrypto: SessionCookieCrypto = app.injector.instanceOf[SessionCookieCrypto]

  def createSessionCookieAsString(sessionData: Map[String, String]): String = {
    val sessionCookie = sessionBaker.encodeAsCookie(Session(sessionData))
    val encryptedSessionCookieValue =
      sessionCookieCrypto.crypto.encrypt(PlainText(sessionCookie.value)).value
    val encryptedSessionCookie =
      sessionCookie.copy(value = encryptedSessionCookieValue)
    cookieHeaderEncoding.encodeCookieHeader(Seq(encryptedSessionCookie))
  }
  val authData = Map("authToken" -> AUTHORIZE_HEADER_VALUE)
  val sessionAndAuth  = Map("authToken" -> AUTHORIZE_HEADER_VALUE, "sessionId" -> sessionId)

  lazy val authCookie: String = createSessionCookieAsString(authData).substring(5)
  lazy val authAndSessionCookie: String = createSessionCookieAsString(sessionAndAuth).substring(5)










  lazy val teaHost = s"localhost:${server.port()}"

  lazy val returnUrl: String = testOnly.routes.TestOnlyController.successfulCall
    .absoluteURL(false, teaHost)

  lazy val accountCheckPath =
    routes.AccountCheckController.accountCheck(RedirectUrl.apply(returnUrl)).url

  lazy val appConfig = app.injector.instanceOf[AppConfig]

  lazy val sessionCookie
  : (String, String) = ("COOKIE" -> createSessionCookieAsString(sessionData))

  lazy val errorView = app.injector.instanceOf[ErrorTemplate]

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

  def requestWithUserDetails(userDetails: UserDetailsFromSession = userDetailsNoEnrolments): RequestWithUserDetailsFromSession[_] =
    RequestWithUserDetailsFromSession(
      FakeRequest().asInstanceOf[Request[AnyContent]],
      userDetails,
      sessionId
    )

  def messagesApi: MessagesApi = {
    app.injector.instanceOf[MessagesApi]
  }

  implicit lazy val messages: Messages = messagesApi.preferred(List(Lang("en")))

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











  lazy val sessionRepository = app.injector.instanceOf[DefaultTEASessionCache]
  lazy val cascadeUpsert: CascadeUpsert = app.injector.instanceOf[CascadeUpsert]
  lazy val repository = inject[DefaultTEASessionCache]


  def save[T](sessionID: String, key: String, value: T)(
    implicit fmt: Format[T]
  ): Future[CacheMap] = {
    sessionRepository.get(sessionID).flatMap { optionalCacheMap =>
      val updatedCacheMap = cascadeUpsert(
        key,
        value,
        optionalCacheMap.getOrElse(CacheMap(sessionID, Map()))
      )
      sessionRepository.upsert(updatedCacheMap).map { _ =>
        updatedCacheMap
      }
    }
  }

  def recordExistsInMongo = sessionRepository.collection.find(Filters.empty()).headOption().map(_.isDefined).futureValue

  def save(sessionId: String,
           dataMap: Map[String, JsValue]): Future[Boolean] = {
    sessionRepository.upsert(CacheMap(sessionId, dataMap))
  }

  def removeAll(sessionID: String): Future[Boolean] = {
    sessionRepository.upsert(CacheMap(sessionID, Map("" -> JsString(""))))
  }

  def fetch(sessionID: String): Future[Option[CacheMap]] =
    sessionRepository.get(sessionID)

  def getEntry[A](sessionID: String,
                  key: String)(implicit fmt: Format[A]): Future[Option[A]] = {
    fetch(sessionID).map { optionalCacheMap =>
      optionalCacheMap.flatMap { cacheMap =>
        cacheMap.getEntry(key)
      }
    }
  }

  def getLastLoginDateTime(sessionID: String): LocalDateTime = {
      sessionRepository.collection
        .find(Filters.equal("id", sessionID))
        .first()
        .toFuture()
        .map(_.lastUpdated)
        .futureValue
  }
}
