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

import cats.data.EitherT
import javax.inject.{Inject, Singleton}
import play.api.Logger
import play.api.http.ContentTypeOf.contentTypeOf_Html
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Result}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.controller.WithDefaultFormBinding
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import uk.gov.hmrc.service.TEAFResult
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.config.AppConfig
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.actions.{AuthAction, RequestWithUserDetailsFromSession, ThrottleAction}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.TaxEnrolmentAssignmentErrors
import uk.gov.hmrc.taxenrolmentassignmentfrontend.logging.EventLoggerService
import uk.gov.hmrc.taxenrolmentassignmentfrontend.logging.LoggingEvent._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.orchestrators.AccountCheckOrchestrator
import uk.gov.hmrc.taxenrolmentassignmentfrontend.reporting.{AuditEvent, AuditHandler}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.SessionKeys.REDIRECT_URL
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.TEASessionCache
import uk.gov.hmrc.taxenrolmentassignmentfrontend.services.SilentAssignmentService
import uk.gov.hmrc.taxenrolmentassignmentfrontend.views.html.templates.ErrorTemplate

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AccountCheckController @Inject()(silentAssignmentService: SilentAssignmentService,
                                        throttleAction: ThrottleAction,
                                        authAction: AuthAction,
                                        accountCheckOrchestrator: AccountCheckOrchestrator,
                                        auditHandler: AuditHandler,
                                        mcc: MessagesControllerComponents,
                                        sessionCache: TEASessionCache,
                                        val logger: EventLoggerService,
                                        errorHandler: ErrorHandler
)(implicit ec: ExecutionContext)
    extends FrontendController(mcc)
    with I18nSupport
      with WithDefaultFormBinding {

  implicit val baseLogger: Logger = Logger(this.getClass.getName)

  def accountCheck(redirectUrl: String): Action[AnyContent] = authAction.async {
    implicit request =>
      handleRequest(redirectUrl).value.map {
        case Right((_, Some(redirectResult))) => redirectResult
        case Right((PT_ASSIGNED_TO_OTHER_USER, _)) => Redirect(routes.PTEnrolmentOnOtherAccountController.view)
        case Right((SA_ASSIGNED_TO_OTHER_USER, _)) if request.userDetails.hasPTEnrolment => Redirect(routes.EnrolledPTWithSAOnOtherAccountController.view)
        case Right((SA_ASSIGNED_TO_OTHER_USER, _)) => Redirect(routes.SABlueInterruptController.view)
        case Right((MULTIPLE_ACCOUNTS, _)) => Redirect(routes.EnrolledForPTController.view)
        case Right((SA_ASSIGNED_TO_CURRENT_USER, _)) => Redirect(routes.EnrolledForPTWithSAController.view)
        case Right(_) =>
          logger.logEvent(
            logRedirectingToReturnUrl(request.userDetails.credId, "[AccountCheckController][accountCheck]")
          )
          Redirect(redirectUrl)
        case Left(error) =>
            errorHandler.handleErrors(error, "[AccountCheckController][accountCheck]")
      }
  }

  private def handleRequest(redirectUrl: String)(implicit request: RequestWithUserDetailsFromSession[_],
  hc: HeaderCarrier): TEAFResult[(AccountTypes.Value, Option[Result])] = {
    for {
      _ <- EitherT.right[TaxEnrolmentAssignmentErrors](sessionCache.save[String](REDIRECT_URL, redirectUrl)(request, implicitly))
      accountType <- accountCheckOrchestrator.getAccountType
      throttle <- EitherT.right[TaxEnrolmentAssignmentErrors](throttleAction.throttle(accountType, redirectUrl))
      _ <- enrolForPTIfRequired(accountType, throttle.isEmpty)
    } yield (accountType, throttle)
  }

  private def enrolForPTIfRequired(accountType: AccountTypes.Value, isThrottled: Boolean)(
    implicit request: RequestWithUserDetailsFromSession[_],
    hc: HeaderCarrier
  ): TEAFResult[Unit] = {
    val accountTypesToEnrolForPT = List(SINGLE_ACCOUNT, MULTIPLE_ACCOUNTS, SA_ASSIGNED_TO_CURRENT_USER)
    val hasPTEnrolmentAlready = request.userDetails.hasPTEnrolment

    if (isThrottled && !hasPTEnrolmentAlready && accountTypesToEnrolForPT.contains(accountType)) {
      silentAssignmentService.enrolUser().map {_ =>
        auditHandler.audit(AuditEvent.auditSuccessfullyEnrolledPTWhenSANotOnOtherAccount(accountType))
        if (accountType == SINGLE_ACCOUNT) {
          logger.logEvent(
            logSingleAccountHolderAssignedEnrolment(request.userDetails.credId)
          )
        } else {
          logger.logEvent(
            logMultipleAccountHolderAssignedEnrolment(request.userDetails.credId)
          )
        }
      }
    } else {
      EitherT.right(Future.successful((): Unit))
    }
  }
}

