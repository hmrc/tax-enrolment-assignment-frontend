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
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Result}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.binders.RedirectUrl._
import uk.gov.hmrc.play.bootstrap.binders._
import uk.gov.hmrc.service.TEAFResult
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.config.AppConfig
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.actions.{AuthAction, RequestWithUserDetailsFromSession, ThrottleAction}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.helpers.{ErrorHandler, TEAFrontendController}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.{InvalidRedirectUrl, TaxEnrolmentAssignmentErrors}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.logging.EventLoggerService
import uk.gov.hmrc.taxenrolmentassignmentfrontend.logging.LoggingEvent._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.orchestrators.AccountCheckOrchestrator
import uk.gov.hmrc.taxenrolmentassignmentfrontend.reporting.{AuditEvent, AuditHandler}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.SessionKeys.REDIRECT_URL
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.TEASessionCache
import uk.gov.hmrc.taxenrolmentassignmentfrontend.services.SilentAssignmentService

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

@Singleton
class AccountCheckController @Inject()(
                                        silentAssignmentService: SilentAssignmentService,
                                        throttleAction: ThrottleAction,
                                        authAction: AuthAction,
                                        accountCheckOrchestrator: AccountCheckOrchestrator,
                                        auditHandler: AuditHandler,
                                        mcc: MessagesControllerComponents,
                                        sessionCache: TEASessionCache,
                                        appConfig: AppConfig,
                                        val logger: EventLoggerService,
                                        errorHandler: ErrorHandler
)(implicit ec: ExecutionContext)
extends TEAFrontendController(mcc) {

  def accountCheck(redirectUrl: RedirectUrl): Action[AnyContent] = authAction.async {
    implicit request =>
      Try {
        redirectUrl.get(OnlyRelative | AbsoluteWithHostnameFromAllowlist(appConfig.validRedirectHostNames)).url
      } match {
        case Success(redirectUrlString) => handleRequest(redirectUrlString).value.map {
          case Right((_, Some(redirectResult))) => redirectResult
          case Right((accountType, _)) => handleNoneThrottledUsers(accountType, redirectUrlString)
          case Left(error) =>
            errorHandler.handleErrors(error, "[AccountCheckController][accountCheck]")
        }
        case Failure(error) =>
          logger.logEvent(logInvalidRedirectUrl(error.getMessage), error)
          Future.successful(
          errorHandler.handleErrors(InvalidRedirectUrl, "[AccountCheckController][accountCheck]")
        )
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

   def handleNoneThrottledUsers(accountType: AccountTypes.Value, redirectUrl: String)
                                      (implicit request: RequestWithUserDetailsFromSession[_]): Result = {
    accountType match {
      case PT_ASSIGNED_TO_OTHER_USER => Redirect(routes.PTEnrolmentOnOtherAccountController.view)
      case SA_ASSIGNED_TO_OTHER_USER if request.userDetails.hasPTEnrolment => Redirect(routes.EnrolledPTWithSAOnOtherAccountController.view)
      case SA_ASSIGNED_TO_OTHER_USER => Redirect(routes.SABlueInterruptController.view)
      case MULTIPLE_ACCOUNTS => Redirect(routes.EnrolledForPTController.view)
      case SA_ASSIGNED_TO_CURRENT_USER => Redirect(routes.EnrolledForPTWithSAController.view)
      case _ => logger.logEvent(
        logRedirectingToReturnUrl(
          request.userDetails.credId,
          "[AccountCheckController][accountCheck]"
        )
      )
        Redirect(redirectUrl)
    }
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
            logSingleAccountHolderAssignedEnrolment(request.userDetails.credId, request.userDetails.nino)
          )
        } else {
          logger.logEvent(
            logMultipleAccountHolderAssignedEnrolment(request.userDetails.credId, request.userDetails.nino)
          )
        }
      }
    } else {
      EitherT.right(Future.successful((): Unit))
    }
  }
}

