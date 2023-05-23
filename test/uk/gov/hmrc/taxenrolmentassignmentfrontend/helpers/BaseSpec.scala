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
import play.api.test.CSRFTokenHelper._
import org.scalamock.scalatest.MockFactory
import org.scalatest.concurrent.{IntegrationPatience, PatienceConfiguration, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.{BeforeAndAfterEach, OneInstancePerTest, Suite}
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.i18n.{Lang, Messages, MessagesApi, MessagesImpl}
import play.api.{Application, Configuration}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.{AnyContent, AnyContentAsEmpty, Request}
import play.api.test.{FakeRequest, Injecting}
import uk.gov.hmrc.domain.{Nino, Generator => NinoGenerator}
import uk.gov.hmrc.http.{HeaderCarrier, SessionKeys}
import uk.gov.hmrc.service.TEAFResult
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.actions.{AccountDetailsFromMongo, RequestWithUserDetailsFromSession, RequestWithUserDetailsFromSessionAndMongo, UserDetailsFromSession}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.TaxEnrolmentAssignmentErrors
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.TestData.userDetailsNoEnrolments
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.TEASessionCache
import play.api.inject.bind
import play.api.libs.json.{Format, JsString, JsValue, Json}
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes.SINGLE_ACCOUNT
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.SessionKeys.{ACCOUNT_TYPE, REDIRECT_URL}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.services.TENCrypto

import scala.concurrent.{ExecutionContext, Future}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.config.HmrcModule

trait BaseSpec
    extends AnyWordSpec with GuiceOneAppPerSuite with Matchers with PatienceConfiguration with BeforeAndAfterEach
    with MockFactory with OneInstancePerTest with ScalaFutures with Injecting with IntegrationPatience {
  this: Suite =>

  def generateNino: Nino = new NinoGenerator().nextNino

  implicit val hc = HeaderCarrier()

  lazy val configValues: Map[String, AnyVal] =
    Map(
      "metrics.enabled"  -> false,
      "auditing.enabled" -> false
    )

  lazy val overrides = Seq(
    bind[TEASessionCache].toInstance(new TestTeaSessionCache)
  )

  protected def localGuiceApplicationBuilder(): GuiceApplicationBuilder =
    GuiceApplicationBuilder()
      .disable[HmrcModule]
      .overrides(overrides)
      .configure(configValues)

  override implicit lazy val app: Application = localGuiceApplicationBuilder().build()

  implicit lazy val ec = inject[ExecutionContext]
  implicit lazy val crypto = inject[TENCrypto]
  lazy val config = inject[Configuration]
  lazy val messagesApi: MessagesApi = inject[MessagesApi]
  implicit lazy val messages: Messages = MessagesImpl(Lang("en"), messagesApi)

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
  ): RequestWithUserDetailsFromSession[AnyContent] =
    new RequestWithUserDetailsFromSession[AnyContent](
      FakeRequest().asInstanceOf[Request[AnyContent]],
      userDetails,
      "sessionId"
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

  def generateBasicCacheData(accountType: AccountTypes.Value, redirectUrl: String = "foo") =
    Map(ACCOUNT_TYPE -> Json.toJson(accountType), REDIRECT_URL -> JsString(redirectUrl))

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

    override def remove(key: String)(implicit
      request: RequestWithUserDetailsFromSession[_]
    ): Future[Boolean] = ???

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

    def collectionDeleteOne(id: String) = ???
    def get(id: String): scala.concurrent.Future[Option[uk.gov.hmrc.http.cache.client.CacheMap]] = ???
    def updateLastUpdated(id: String): scala.concurrent.Future[Boolean] = ???
    def upsert(cm: uk.gov.hmrc.http.cache.client.CacheMap): scala.concurrent.Future[Boolean] = ???

  }

}
