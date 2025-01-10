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

import helpers.TestITData._
import org.scalatest.concurrent.{IntegrationPatience, PatienceConfiguration, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.i18n.{Lang, Messages, MessagesApi}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsString, JsValue, Json}
import play.api.mvc.{AnyContent, Request}
import play.api.test.{FakeRequest, Injecting}
import play.api.Application
import uk.gov.hmrc.http.{Authorization, HeaderCarrier}
import uk.gov.hmrc.play.bootstrap.binders.RedirectUrl
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.actions.{AccountDetailsFromMongo, RequestWithUserDetailsFromSessionAndMongo}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.routes
import uk.gov.hmrc.taxenrolmentassignmentfrontend.services.TENCrypto
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.domain.{Generator => NinoGenerator}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes.SA_ASSIGNED_TO_OTHER_USER
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.SessionKeys.{ACCOUNT_TYPE, REDIRECT_URL}

import scala.concurrent.ExecutionContext

trait IntegrationSpecBase
    extends AnyWordSpec with GuiceOneAppPerSuite with Matchers with PatienceConfiguration with BeforeAndAfterEach
    with ScalaFutures with Injecting with IntegrationPatience with SessionCacheOperations with WireMockHelper {

  def generateNino: Nino = new NinoGenerator().nextNino
  def secondGenerateNino: Nino = new NinoGenerator().nextNino

  val nino: Nino = generateNino

  def ninoWithLast2digits(digits: String): Nino = {
    if (digits.length != 2) {
      throw new IllegalArgumentException("digits must be 2 characters exactly")
    }
    val digit1 = digits.head
    val digit2 = digits.reverse.head
    Nino(nino.nino.toList.updated(6, digit1).updated(7, digit2).mkString(""))
  }

  implicit lazy val ec: ExecutionContext = app.injector.instanceOf[ExecutionContext]
  lazy implicit val hc: HeaderCarrier = HeaderCarrier(
    authorization = Some(Authorization(AUTHORIZE_HEADER_VALUE))
  )
  lazy val crypto: TENCrypto = app.injector.instanceOf[TENCrypto]

  lazy val config: Map[String, Any] = Map(
    "play.filters.csrf.header.bypassHeaders.Csrf-Token"            -> "nocheck",
    "auditing.enabled"                                             -> true,
    "auditing.consumer.baseUri.port"                               -> server.port(),
    "microservice.services.auth.port"                              -> server.port(),
    "microservice.services.identity-verification.port"             -> server.port(),
    "microservice.services.enrolment-store-proxy.port"             -> server.port(),
    "microservice.services.enrolment-store-stub.port"              -> server.port(),
    "microservice.services.tax-enrolments.port"                    -> server.port(),
    "microservice.services.users-groups-search.port"               -> server.port(),
    "microservice.services.bas-stubs.port"                         -> server.port(),
    "microservice.services.identity-provider-account-context.port" -> server.port(),
    "microservice.services.one-login-stub.port"                    -> server.port(),
    "play.http.router"                                             -> "testOnlyDoNotUseInAppConf.Routes",
    "mongodb.uri"                                                  -> mongoUri,
    "play.ws.timeout.request"                                      -> "5000ms",
    "play.ws.timeout.connection"                                   -> "100ms"
  )

  protected def localGuiceApplicationBuilder: GuiceApplicationBuilder =
    GuiceApplicationBuilder()
      .configure(config)

  override implicit lazy val app: Application =
    localGuiceApplicationBuilder
      .build()

  lazy val returnUrl: String = "http://localhost:1234/redirect/url"

  lazy val accountCheckPath: String =
    routes.AccountCheckController.accountCheck(RedirectUrl.apply(returnUrl)).url

  lazy val unauthorizedPath: String = routes.AuthorisationController.notAuthorised.url

  val exampleMongoSessionData: Map[String, JsValue] =
    Map(ACCOUNT_TYPE -> Json.toJson(SA_ASSIGNED_TO_OTHER_USER), REDIRECT_URL -> JsString("redirectURL"))

  def requestWithAccountType(
    accountType: AccountTypes.Value,
    redirectUrl: String = returnUrl,
    mongoCacheData: Map[String, JsValue] = exampleMongoSessionData
  ): RequestWithUserDetailsFromSessionAndMongo[_] =
    RequestWithUserDetailsFromSessionAndMongo(
      FakeRequest().asInstanceOf[Request[AnyContent]],
      userDetailsNoEnrolments,
      sessionId,
      AccountDetailsFromMongo(accountType, redirectUrl, mongoCacheData)(crypto.crypto)
    )

  def messagesApi: MessagesApi =
    app.injector.instanceOf[MessagesApi]

  implicit lazy val messages: Messages = messagesApi.preferred(List(Lang("en")))
}
