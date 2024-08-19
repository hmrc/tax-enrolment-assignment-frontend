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

package uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers

import cats.data.EitherT
import org.apache.pekko.stream.Materializer
import org.scalatest.concurrent.{IntegrationPatience, PatienceConfiguration, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.{BeforeAndAfterEach, OneInstancePerTest, Suite}
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.i18n.{Lang, Messages, MessagesApi, MessagesImpl}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.inject.{Binding, bind}
import play.api.libs.json.{JsString, JsValue, Json}
import play.api.mvc.{AnyContent, AnyContentAsEmpty, Request}
import play.api.test.CSRFTokenHelper._
import play.api.test.{FakeRequest, Injecting}
import play.api.{Application, Configuration}
import uk.gov.hmrc.domain.{Nino, Generator => NinoGenerator}
import uk.gov.hmrc.http.{HeaderCarrier, SessionKeys}
import uk.gov.hmrc.service.TEAFResult
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes.SINGLE_OR_MULTIPLE_ACCOUNTS
import uk.gov.hmrc.taxenrolmentassignmentfrontend.config.SessionModule
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.actions.{AccountDetailsFromMongo, DataRequest, RequestWithUserDetailsFromSession, RequestWithUserDetailsFromSessionAndMongo, UserDetailsFromSession}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.TaxEnrolmentAssignmentErrors
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.TestData.{userDetails, userDetailsNoEnrolments}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.UserAnswers
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.JourneyCacheRepository
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.SessionKeys.{ACCOUNT_TYPE, REDIRECT_URL}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.services.TENCrypto

import scala.concurrent.{ExecutionContext, Future}

trait BaseSpec
    extends AnyWordSpec with GuiceOneAppPerSuite with Matchers with PatienceConfiguration with BeforeAndAfterEach
    with OneInstancePerTest with ScalaFutures with Injecting with IntegrationPatience {
  this: Suite =>

  def generateNino: Nino = new NinoGenerator().nextNino

  implicit val hc: HeaderCarrier = HeaderCarrier()

  private val mockJourneyCacheRepository = mock[JourneyCacheRepository]

  lazy val configValues: Map[String, AnyVal] =
    Map(
      "metrics.enabled"  -> false,
      "auditing.enabled" -> false
    )

  lazy val overrides: Seq[Binding[JourneyCacheRepository]] = Seq(
    bind[JourneyCacheRepository].toInstance(mockJourneyCacheRepository)
  )

  protected def localGuiceApplicationBuilder(): GuiceApplicationBuilder =
    GuiceApplicationBuilder()
      .disable[SessionModule]
      .overrides(overrides)
      .configure(configValues)

  override implicit lazy val app: Application = localGuiceApplicationBuilder().build()

  implicit lazy val ec: ExecutionContext = inject[ExecutionContext]
  implicit lazy val crypto: TENCrypto = inject[TENCrypto]
  lazy val config: Configuration = inject[Configuration]
  lazy val messagesApi: MessagesApi = inject[MessagesApi]
  implicit lazy val messages: Messages = MessagesImpl(Lang("en"), messagesApi)
  implicit lazy val mat: Materializer = inject[Materializer]

  lazy val requestPath = "Not Used"
  implicit lazy val fakeRequest: FakeRequest[AnyContentAsEmpty.type] =
    FakeRequest("", requestPath)
      .withSession(
        SessionKeys.sessionId -> "foo",
        SessionKeys.authToken -> "token"
      )
      .withCSRFToken
      .asInstanceOf[FakeRequest[AnyContentAsEmpty.type]]

  def requestWithUserDetails(
    userDetails: UserDetailsFromSession = userDetailsNoEnrolments
  ): DataRequest[AnyContent] =
    new DataRequest[AnyContent](
      FakeRequest().asInstanceOf[Request[AnyContent]],
      userDetails,
      UserAnswers("sessionId", generateNino.nino),
      None
    )

  def requestWithGivenMongoData(
    requestWithMongo: RequestWithUserDetailsFromSessionAndMongo[AnyContent]
  ): DataRequest[AnyContent] =
    new DataRequest[AnyContent](
      FakeRequest().asInstanceOf[Request[AnyContent]],
      userDetailsNoEnrolments,
      UserAnswers("sessionId", generateNino.nino),
      Some(requestWithMongo)
    )

  def createInboundResult[T](result: T): TEAFResult[T] =
    EitherT.right[TaxEnrolmentAssignmentErrors](Future.successful(result))

  def createInboundResultError[T](
    error: TaxEnrolmentAssignmentErrors
  ): TEAFResult[T] = EitherT.left(Future.successful(error))

  implicit val request: RequestWithUserDetailsFromSession[AnyContent] =
    new RequestWithUserDetailsFromSession[AnyContent](
      FakeRequest().asInstanceOf[Request[AnyContent]],
      userDetailsNoEnrolments,
      "sessionId"
    )

  def requestWithEnrolments(hmrcPt: Boolean, irSa: Boolean): RequestWithUserDetailsFromSession[AnyContent] =
    new RequestWithUserDetailsFromSession[AnyContent](
      FakeRequest().asInstanceOf[Request[AnyContent]],
      userDetails(hmrcPt, irSa),
      "sessionId"
    )

  def requestWithGivenSessionData(
    requestWithSession: RequestWithUserDetailsFromSession[AnyContent]
  ): DataRequest[AnyContent] =
    new DataRequest[AnyContent](
      requestWithSession,
      userDetailsNoEnrolments,
      UserAnswers("sessionId", generateNino.nino),
      None
    )

  def generateBasicCacheData(accountType: AccountTypes.Value, redirectUrl: String = "foo"): Map[String, JsValue] =
    Map(ACCOUNT_TYPE -> Json.toJson(accountType), REDIRECT_URL -> JsString(redirectUrl))

  def requestWithAccountType(
    accountType: AccountTypes.Value = SINGLE_OR_MULTIPLE_ACCOUNTS,
    redirectUrl: String = UrlPaths.returnUrl,
    langCode: String = "en"
  ): RequestWithUserDetailsFromSessionAndMongo[AnyContent] =
    RequestWithUserDetailsFromSessionAndMongo(
      request.request.withTransientLang(langCode),
      request.userDetails,
      request.sessionID,
      AccountDetailsFromMongo(
        accountType,
        redirectUrl,
        None,
        None,
        None,
        None
      )
    )
}
