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

package uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers

import cats.data.EitherT
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.scalamock.scalatest.MockFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.i18n._
import play.api.inject.Injector
import play.api.libs.json.Format
import play.api.mvc._
import play.api.test.CSRFTokenHelper._
import play.api.test.Helpers._
import play.api.test._
import play.twirl.api.Html
import uk.gov.hmrc.auth.core.{AuthConnector, Enrolment}
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.http.{HeaderCarrier, SessionKeys}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.service.TEAFResult
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes.SINGLE_ACCOUNT
import uk.gov.hmrc.taxenrolmentassignmentfrontend.config.AppConfig
import uk.gov.hmrc.taxenrolmentassignmentfrontend.connectors.{EACDConnector, IVConnector, LegacyAuthConnector, TaxEnrolmentsConnector}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.actions._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.testOnly.TestOnlyController
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.{ErrorHandler, SignOutController}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.{TaxEnrolmentAssignmentErrors, UnexpectedError}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.TestData.{randomAccountType, userDetailsNoEnrolments}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.logging.EventLoggerService
import uk.gov.hmrc.taxenrolmentassignmentfrontend.orchestrators.{AccountCheckOrchestrator, MultipleAccountsOrchestrator}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.reporting.AuditHandler
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.TEASessionCache
import uk.gov.hmrc.taxenrolmentassignmentfrontend.services._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.views.html.UnderConstructionView
import uk.gov.hmrc.taxenrolmentassignmentfrontend.views.html.templates.ErrorTemplate

import scala.concurrent.{ExecutionContext, Future}

trait TestFixture
    extends AnyWordSpec
    with MockFactory
    with GuiceOneAppPerSuite
    with Matchers
    with Injecting {

  val TIME_OUT = 5
  val INTERVAL = 5

  lazy val injector: Injector = app.injector
  implicit val request: RequestWithUserDetailsFromSession[AnyContent] =
    new RequestWithUserDetailsFromSession[AnyContent](
      FakeRequest().asInstanceOf[Request[AnyContent]],
      userDetailsNoEnrolments,
      "sessionId"
    )

  def requestWithAccountType(
    accountType: AccountTypes.Value = SINGLE_ACCOUNT,
    redirectUrl: String = UrlPaths.returnUrl
  ): RequestWithUserDetailsFromSessionAndMongo[_] =
    RequestWithUserDetailsFromSessionAndMongo(
      request.request,
      request.userDetails,
      request.sessionID,
      AccountDetailsFromMongo(accountType, redirectUrl)
    )

  implicit val ec: ExecutionContext = injector.instanceOf[ExecutionContext]
  lazy val servicesConfig = injector.instanceOf[ServicesConfig]
  implicit val hc: HeaderCarrier = HeaderCarrier()
  lazy val mcc: MessagesControllerComponents =
    stubMessagesControllerComponents()
  lazy val errorHandler: ErrorHandler = new ErrorHandler(errorView, logger, mcc)
  lazy val logger: EventLoggerService = new EventLoggerService()
  implicit val appConfig: AppConfig = injector.instanceOf[AppConfig]
  lazy val messagesApi: MessagesApi = inject[MessagesApi]
  implicit lazy val messages: Messages = messagesApi.preferred(fakeRequest)
  lazy val UCView: UnderConstructionView = inject[UnderConstructionView]
  val mockAuthConnector: AuthConnector = mock[AuthConnector]
  val mockIVConnector: IVConnector = mock[IVConnector]
  val mockTaxEnrolmentsConnector: TaxEnrolmentsConnector =
    mock[TaxEnrolmentsConnector]
  val mockEacdConnector: EACDConnector = mock[EACDConnector]
  val mockLegacyAuthConnector = mock[LegacyAuthConnector]
  val mockEacdService: EACDService = mock[EACDService]
  val mockUsersGroupService: UsersGroupsSearchService =
    mock[UsersGroupsSearchService]
  val testBodyParser: BodyParsers.Default = mock[BodyParsers.Default]
  lazy val requestPath = "Not Used"
  val mockTeaSessionCache = mock[TEASessionCache]
  val mockAccountCheckOrchestrator = mock[AccountCheckOrchestrator]
  val mockMultipleAccountsOrchestrator = mock[MultipleAccountsOrchestrator]
  val mockSilentAssignmentService: SilentAssignmentService =
    mock[SilentAssignmentService]
  val mockAuditHandler: AuditHandler = mock[AuditHandler]

  val mockThrottlingService = mock[ThrottlingService]
  implicit lazy val fakeRequest: FakeRequest[AnyContentAsEmpty.type] =
    FakeRequest("", requestPath)
      .withSession(
        SessionKeys.sessionId -> "foo",
        SessionKeys.authToken -> "token"
      )
      .withCSRFToken
      .asInstanceOf[FakeRequest[AnyContentAsEmpty.type]]

  val errorView: ErrorTemplate = app.injector.instanceOf[ErrorTemplate]
  lazy val mockAuthAction =
    new AuthAction(mockAuthConnector, testBodyParser, logger, appConfig)
  lazy val mockAccountMongoDetailsAction =
    new AccountMongoDetailsAction(
      mockAccountCheckOrchestrator,
      testBodyParser,
      errorHandler
    )

  lazy val mockThrottleAction =
    new ThrottleAction(mockThrottlingService, testBodyParser, errorHandler)
  implicit lazy val testMessages: Messages =
    messagesApi.preferred(FakeRequest())

  val messagesActionBuilder: MessagesActionBuilder =
    new DefaultMessagesActionBuilderImpl(
      stubBodyParser[AnyContent](),
      stubMessagesApi()
    )

  lazy val mockSignOutController = mock[SignOutController]

  def doc(result: Html): Document = Jsoup.parse(contentAsString(result))

  def createInboundResult[T](result: T): TEAFResult[T] =
    EitherT.right[TaxEnrolmentAssignmentErrors](Future.successful(result))

  def createInboundResultError[T](
    error: TaxEnrolmentAssignmentErrors
  ): TEAFResult[T] = EitherT.left(Future.successful(error))

  lazy val testOnlyController = new TestOnlyController(mcc, mockAuthAction,  logger)

  def mockAccountShouldNotBeThrottled(accountTypes: AccountTypes.Value, nino: String, enrolments: Set[Enrolment]) = {
    (mockThrottlingService.throttle(_: AccountTypes.Value, _: String, _: Set[Enrolment])(_: ExecutionContext, _: HeaderCarrier))
      .expects(
        accountTypes,
        nino,
        enrolments, *, *
      )
      .returning(createInboundResult(ThrottleDoesNotApply))
      .once()
  }

  def mockAccountShouldBeThrottled(accountTypes: AccountTypes.Value, nino: String, enrolments: Set[Enrolment]) = {
    (mockThrottlingService.throttle(_: AccountTypes.Value, _: String, _: Set[Enrolment])(_: ExecutionContext, _: HeaderCarrier))
      .expects(
        accountTypes,
        nino,
        enrolments, *, *
      )
      .returning(createInboundResult(ThrottleApplied))
      .once()
  }
  def mockErrorFromThrottlingService(accountTypes: AccountTypes.Value, nino: String, enrolments: Set[Enrolment]) = {
    (mockThrottlingService.throttle(_: AccountTypes.Value, _: String, _: Set[Enrolment])(_: ExecutionContext, _: HeaderCarrier))
      .expects(
        accountTypes,
        nino,
        enrolments, *, *
      )
      .returning(createInboundResultError(UnexpectedError))
      .once()
  }
  def mockGetAccountTypeAndRedirectUrlSuccess(accountType: AccountTypes.Value, redirectUrl: String = "foo") = {
    (mockAccountCheckOrchestrator
      .getAccountTypeFromCache(
        _: RequestWithUserDetailsFromSession[_],
        _: Format[AccountTypes.Value]
      ))
      .expects(*, *)
      .returning(Future.successful(Some(accountType)))
    (mockAccountCheckOrchestrator
      .getRedirectUrlFromCache(
        _: RequestWithUserDetailsFromSession[_]
      ))
      .expects(*)
      .returning(Future.successful(Some(redirectUrl)))
  }
  def mockGetAccountTypeSucessRedirectFail = {
    (mockAccountCheckOrchestrator
      .getAccountTypeFromCache(
        _: RequestWithUserDetailsFromSession[_],
        _: Format[AccountTypes.Value]
      ))
      .expects(*, *)
      .returning(Future.successful(Some(randomAccountType)))
    (mockAccountCheckOrchestrator
      .getRedirectUrlFromCache(
        _: RequestWithUserDetailsFromSession[_]
      ))
      .expects(*)
      .returning(Future.successful(None))
  }

  class TestTeaSessionCache extends TEASessionCache {
    override def save[A](key: String, value: A)(
      implicit request: RequestWithUserDetailsFromSession[_],
      fmt: Format[A]
    ): Future[CacheMap] = Future(CacheMap(request.sessionID, Map()))

    override def remove(key: String)(
      implicit request: RequestWithUserDetailsFromSession[_]
    ): Future[Boolean] = ???

    override def removeAll()(
      implicit request: RequestWithUserDetailsFromSession[_]
    ): Future[Boolean] = Future.successful(true)

    override def fetch()(
      implicit request: RequestWithUserDetailsFromSession[_]
    ): Future[Option[CacheMap]] =
      Future(Some(CacheMap(request.sessionID, Map())))

    override def getEntry[A](key: String)(
      implicit request: RequestWithUserDetailsFromSession[_],
      fmt: Format[A]
    ): Future[Option[A]] = Future.successful(None)

    override def extendSession()(
      implicit request: RequestWithUserDetailsFromSession[_]
    ): Future[Boolean] = Future.successful(true)
  }
}
