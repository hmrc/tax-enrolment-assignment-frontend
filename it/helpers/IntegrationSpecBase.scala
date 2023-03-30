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
import play.api.libs.json.{JsValue, Json, JsString}
import play.api.mvc.{AnyContent, Request}
import play.api.test.{FakeRequest, Injecting}
import play.api.Application
import uk.gov.hmrc.http.{Authorization, HeaderCarrier}
import uk.gov.hmrc.play.bootstrap.binders.RedirectUrl
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.actions.{AccountDetailsFromMongo, RequestWithUserDetailsFromSessionAndMongo, UserDetailsFromSession}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.{routes, testOnly}
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

  implicit lazy val ec: ExecutionContext = app.injector.instanceOf[ExecutionContext]
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
