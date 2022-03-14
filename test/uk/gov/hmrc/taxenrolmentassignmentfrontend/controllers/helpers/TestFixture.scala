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

package uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.helpers

import cats.data.EitherT
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.scalamock.scalatest.MockFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.i18n.{Messages, MessagesApi}
import play.api.inject.Injector
import play.api.libs.json.Format
import play.api.mvc._
import play.api.test.Helpers._
import play.api.test._
import play.twirl.api.Html
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.SessionKeys
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.service.TEAFResult
import uk.gov.hmrc.taxenrolmentassignmentfrontend.config.AppConfig
import uk.gov.hmrc.taxenrolmentassignmentfrontend.connectors.IVConnector
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.auth.{
  AuthAction,
  RequestWithUserDetails
}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.testOnly.TestOnlyController
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.TaxEnrolmentAssignmentErrors
import uk.gov.hmrc.taxenrolmentassignmentfrontend.logging.EventLoggerService
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.TEASessionCache

import scala.concurrent.{ExecutionContext, Future}

trait TestFixture
    extends AnyWordSpec
    with MockFactory
    with GuiceOneAppPerSuite
    with Matchers
    with Injecting {

  lazy val injector: Injector = app.injector
  implicit val ec: ExecutionContext = injector.instanceOf[ExecutionContext]
  lazy val logger: EventLoggerService = new EventLoggerService()
  lazy val servicesConfig = injector.instanceOf[ServicesConfig]
  implicit val appConfig: AppConfig = injector.instanceOf[AppConfig]

  lazy val messagesApi: MessagesApi = inject[MessagesApi]

  val mockAuthConnector: AuthConnector = mock[AuthConnector]
  val mockIVConnector: IVConnector = mock[IVConnector]
  val testBodyParser: BodyParsers.Default = mock[BodyParsers.Default]
  val testAppConfig: AppConfig = app.injector.instanceOf[AppConfig]
  lazy val mockAuthAction =
    new AuthAction(mockAuthConnector, testBodyParser, logger, testAppConfig)
  lazy val mcc: MessagesControllerComponents =
    stubMessagesControllerComponents()
  implicit lazy val testMessages: Messages =
    messagesApi.preferred(FakeRequest())

  val messagesActionBuilder: MessagesActionBuilder =
    new DefaultMessagesActionBuilderImpl(
      stubBodyParser[AnyContent](),
      stubMessagesApi()
    )

  def doc(result: Html): Document = Jsoup.parse(contentAsString(result))

  def createInboundResult[T](result: T): TEAFResult[T] =
    EitherT.right[TaxEnrolmentAssignmentErrors](Future.successful(result))

  def createInboundResultError[T](
    error: TaxEnrolmentAssignmentErrors
  ): TEAFResult[T] = EitherT.left(Future.successful(error))

  lazy val testOnlyController = new TestOnlyController(mcc, logger)

  class TestTeaSessionCache extends TEASessionCache {
    override def save[A](key: String, value: A)(
      implicit request: RequestWithUserDetails[AnyContent],
      fmt: Format[A]
    ): Future[CacheMap] = Future(CacheMap(request.sessionID, Map()))

    override def remove(key: String)(
      implicit request: RequestWithUserDetails[AnyContent]
    ): Future[Boolean] = ???

    override def removeAll()(
      implicit request: RequestWithUserDetails[AnyContent]
    ): Future[Boolean] = Future.successful(true)

    override def fetch()(
      implicit request: RequestWithUserDetails[AnyContent]
    ): Future[Option[CacheMap]] =
      Future(Some(CacheMap(request.sessionID, Map())))

    override def getEntry[A](key: String)(
      implicit request: RequestWithUserDetails[AnyContent],
      fmt: Format[A]
    ): Future[Option[A]] = ???
  }
}
