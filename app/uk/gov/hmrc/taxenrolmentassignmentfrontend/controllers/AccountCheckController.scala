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

package uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers

import javax.inject.{Inject, Singleton}
import play.api.{Logger, Logging}
import play.api.http.ContentTypeOf.contentTypeOf_Html
import play.api.i18n.I18nSupport
import play.api.mvc.{
  Action,
  AnyContent,
  Call,
  MessagesControllerComponents,
  Result
}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import uk.gov.hmrc.play.http.HeaderCarrierConverter
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.config.AppConfig
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.auth.{
  AuthAction,
  RequestWithUserDetails
}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.logging.EventLoggerService
import uk.gov.hmrc.taxenrolmentassignmentfrontend.orchestrators.AccountCheckOrchestrator
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.TEASessionCache
import uk.gov.hmrc.taxenrolmentassignmentfrontend.services.SilentAssignmentService
import uk.gov.hmrc.taxenrolmentassignmentfrontend.views.html.templates.ErrorTemplate
import uk.gov.hmrc.taxenrolmentassignmentfrontend.logging.LoggingEvent._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.SessionKeys.REDIRECT_URL

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AccountCheckController @Inject()(
  accountCheckOrchestrator: AccountCheckOrchestrator,
  silentAssignmentService: SilentAssignmentService,
  authAction: AuthAction,
  appConfig: AppConfig,
  mcc: MessagesControllerComponents,
  sessionCache: TEASessionCache,
  val logger: EventLoggerService,
  val errorView: ErrorTemplate
)(implicit ec: ExecutionContext)
    extends FrontendController(mcc)
    with I18nSupport
    with ControllerHelper {

  implicit val baseLogger: Logger = Logger(this.getClass.getName)

  def accountCheck(redirectUrl: String): Action[AnyContent] = authAction.async {
    implicit request =>
      sessionCache.save[String](REDIRECT_URL, redirectUrl)
      accountCheckOrchestrator.getAccountType.value.flatMap {
        case Right(PT_ASSIGNED_TO_CURRENT_USER) =>
          logger.logEvent(
            logRedirectingToReturnUrl(
              request.userDetails.credId,
              "[AccountCheckController][accountCheck]"
            )
          )
          Future.successful(Redirect(redirectUrl))
        case Right(PT_ASSIGNED_TO_OTHER_USER) =>
          Future.successful(
            Redirect(routes.PTEnrolmentOnOtherAccountController.view)
          )
        case Right(SA_ASSIGNED_TO_OTHER_USER) =>
          Future.successful(Redirect(routes.SABlueInterruptController.view))
        case Right(accountType) => silentEnrolmentAndRedirect(accountType)
        case Left(error) =>
          Future.successful(
            handleErrors(error, "[AccountCheckController][accountCheck]")
          )
      }
  }

  private def silentEnrolmentAndRedirect(accountType: AccountTypes.Value)(
    implicit request: RequestWithUserDetails[AnyContent],
    hc: HeaderCarrier
  ): Future[Result] = {
    silentAssignmentService.enrolUser().isRight map {
      case true if accountType == SINGLE_ACCOUNT =>
        logger.logEvent(
          logSingleAccountHolderAssignedEnrolment(request.userDetails.credId)
        )
        logger.logEvent(
          logRedirectingToReturnUrl(
            request.userDetails.credId,
            "[AccountCheckController][accountCheck]"
          )
        )
        Redirect(appConfig.redirectPTAUrl)
      case true =>
        logger.logEvent(
          logMultipleAccountHolderAssignedEnrolment(request.userDetails.credId)
        )
        Redirect(routes.EnrolledForPTController.view)
      case false =>
        Ok(
          errorView(
            "enrolmentError.title",
            "enrolmentError.heading",
            "enrolmentError.body"
          )
        )
    }
  }

}
