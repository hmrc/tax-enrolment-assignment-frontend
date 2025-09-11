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
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.i18n.{Lang, Messages, MessagesApi, MessagesImpl}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.inject.{Binding, bind}
import play.api.libs.json.{Format, JsString, JsValue, Json}
import play.api.mvc.{AnyContent, AnyContentAsEmpty, Request}
import play.api.test.CSRFTokenHelper.*
import play.api.test.{FakeRequest, Injecting}
import play.api.{Application, Configuration}
import uk.gov.hmrc.crypto.Sensitive.SensitiveString
import uk.gov.hmrc.domain.{Nino, NinoGenerator}
import uk.gov.hmrc.http.{HeaderCarrier, SessionKeys}
import uk.gov.hmrc.service.TEAFResult
import uk.gov.hmrc.taxenrolmentassignmentfrontend.config.HmrcModule
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.actions.{AccountDetailsFromMongo, RequestWithUserDetailsFromSession, RequestWithUserDetailsFromSessionAndMongo, UserDetailsFromSession}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.TaxEnrolmentAssignmentErrors
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.TestData.*
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.*
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.AccountTypes.SINGLE_ACCOUNT
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.SessionKeys.{ACCOUNT_TYPE, REDIRECT_URL}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.TEASessionCache
import uk.gov.hmrc.taxenrolmentassignmentfrontend.services.TENCrypto

import scala.concurrent.{ExecutionContext, Future}

trait BaseSpec
    extends AnyWordSpec
    with GuiceOneAppPerSuite
    with Matchers
    with PatienceConfiguration
    with BeforeAndAfterEach
    with OneInstancePerTest
    with ScalaFutures
    with Injecting
    with IntegrationPatience
    with MockitoSugar {
  this: Suite =>

  def generateNino: Nino = new NinoGenerator().nextNino

  implicit val hc: HeaderCarrier = HeaderCarrier()

  lazy val configValues: Map[String, AnyVal] =
    Map(
      "metrics.enabled"  -> false,
      "auditing.enabled" -> false
    )

  lazy val overrides: Seq[Binding[TEASessionCache]] = Seq(
    bind[TEASessionCache].toInstance(new TestTeaSessionCache)
  )

  protected def localGuiceApplicationBuilder(): GuiceApplicationBuilder =
    GuiceApplicationBuilder()
      .disable[HmrcModule]
      .overrides(overrides)
      .configure(configValues)

  override implicit lazy val app: Application = localGuiceApplicationBuilder().build()

  implicit lazy val ec: ExecutionContext = inject[ExecutionContext]
  implicit lazy val crypto: TENCrypto    = inject[TENCrypto]
  lazy val config: Configuration         = inject[Configuration]
  lazy val messagesApi: MessagesApi      = inject[MessagesApi]
  implicit lazy val messages: Messages   = MessagesImpl(Lang("en"), messagesApi)
  implicit lazy val mat: Materializer    = inject[Materializer]

  lazy val requestPath                                               = "Not Used"
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
  ): RequestWithUserDetailsFromSession[AnyContent] =
    new RequestWithUserDetailsFromSession[AnyContent](
      FakeRequest().asInstanceOf[Request[AnyContent]],
      userDetails,
      "sessionId"
    )

  def requestWithUserDetailsFromSessionAndMongo(
    request: RequestWithUserDetailsFromSession[AnyContent],
    accountDetailsFromMongo: AccountDetailsFromMongo
  ): RequestWithUserDetailsFromSessionAndMongo[AnyContent] =
    RequestWithUserDetailsFromSessionAndMongo(
      request = request.request,
      userDetails = request.userDetails,
      sessionID = request.sessionID,
      accountDetailsFromMongo = accountDetailsFromMongo
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

  def generateBasicCacheData(accountType: AccountTypes.Value, redirectUrl: String = "foo"): Map[String, JsValue] =
    Map(ACCOUNT_TYPE -> Json.toJson(accountType), REDIRECT_URL -> JsString(redirectUrl))

  def accountDetailsFromMongo(
    accountType: AccountTypes.Value = SINGLE_ACCOUNT,
    redirectUrl: String = UrlPaths.returnUrl,
    additionalCacheData: Map[String, JsValue] = Map()
  ): AccountDetailsFromMongo = AccountDetailsFromMongo(
    accountType = accountType,
    redirectUrl = redirectUrl,
    sessionData = generateBasicCacheData(accountType, redirectUrl) ++ additionalCacheData
  )(crypto.crypto)

  def requestWithAccountType(
    accountType: AccountTypes.Value = SINGLE_ACCOUNT,
    redirectUrl: String = UrlPaths.returnUrl,
    additionalCacheData: Map[String, JsValue] = Map(),
    langCode: String = "en"
  ): RequestWithUserDetailsFromSessionAndMongo[_] =
    RequestWithUserDetailsFromSessionAndMongo(
      request.request.withTransientLang(langCode),
      request.userDetails,
      request.sessionID,
      AccountDetailsFromMongo(
        accountType,
        redirectUrl,
        generateBasicCacheData(accountType, redirectUrl) ++ additionalCacheData
      )(crypto.crypto)
    )

  class TestTeaSessionCache extends TEASessionCache {
    override def save[A](key: String, value: A)(implicit
      request: RequestWithUserDetailsFromSession[_],
      fmt: Format[A]
    ): Future[CacheMap] = Future(CacheMap(request.sessionID, Map()))

    override def removeRecord(implicit
      request: RequestWithUserDetailsFromSession[_]
    ): Future[Boolean] = ???

    override def fetch()(implicit
      request: RequestWithUserDetailsFromSession[_]
    ): Future[Option[CacheMap]] =
      Future(Some(CacheMap(request.sessionID, Map())))

    override def extendSession()(implicit
      request: RequestWithUserDetailsFromSession[_]
    ): Future[Boolean] = Future.successful(true)

    def collectionDeleteOne(id: String): Future[Boolean]                = ???
    def get(id: String): scala.concurrent.Future[Option[CacheMap]]      = ???
    def updateLastUpdated(id: String): scala.concurrent.Future[Boolean] = ???
    def upsert(cm: CacheMap): scala.concurrent.Future[Boolean]          = ???

  }

  val mfaDetails = Seq(
    MFADetails("mfaDetails.text", "28923"),
    MFADetails("mfaDetails.voice", "53839"),
    MFADetails("mfaDetails.totp", "HMRC APP")
  )

  val testAccountDetails         = AccountDetails(
    identityProviderType = SCP,
    CREDENTIAL_ID,
    userId = USER_ID,
    email = Some(SensitiveString("email.otherUser@test.com")),
    lastLoginDate = Some("27 February 2022 at 12:00PM"),
    mfaDetails
  )
  val testAccountDetailsOL       = AccountDetails(
    identityProviderType = ONE_LOGIN,
    CREDENTIAL_ID,
    userId = USER_ID,
    email = Some(SensitiveString("email.otherUser@test.com")),
    lastLoginDate = Some("27 February 2022 at 12:00PM"),
    mfaDetails
  )
  val testAccountDetailsWithSA   = AccountDetails(
    identityProviderType = SCP,
    CREDENTIAL_ID_1,
    userId = PT_USER_ID,
    email = Some(SensitiveString("email.otherUser@test.com")),
    lastLoginDate = Some("27 February 2022 at 12:00PM"),
    mfaDetails,
    hasSA = Some(true)
  )
  val testAccountDetailsWithSAOL = AccountDetails(
    identityProviderType = ONE_LOGIN,
    CREDENTIAL_ID_1,
    userId = PT_USER_ID,
    email = Some(SensitiveString("email.otherUser@test.com")),
    lastLoginDate = Some("27 February 2022 at 12:00PM"),
    mfaDetails,
    hasSA = Some(true)
  )

  val accountDetailsWithNoEmail: AccountDetails = AccountDetails(
    identityProviderType = SCP,
    CREDENTIAL_ID,
    userId = "9871",
    email = None,
    lastLoginDate = Some("27 February 2022 at 12:00PM"),
    mfaDetails = List(MFADetails("mfaDetails.text", "26543"))
  )

}
