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

package uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers

import cats.data.EitherT
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Result}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.binders.*
import uk.gov.hmrc.play.bootstrap.binders.RedirectUrl.idFunctor
import uk.gov.hmrc.service.TEAFResult
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes.*
import uk.gov.hmrc.taxenrolmentassignmentfrontend.config.AppConfig
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.actions.{AuthJourney, RequestWithUserDetailsFromSession}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.helpers.{ErrorHandler, TEAFrontendController}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.{InvalidRedirectUrl, TaxEnrolmentAssignmentErrors}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.logging.EventLoggerService
import uk.gov.hmrc.taxenrolmentassignmentfrontend.logging.LoggingEvent.*
import uk.gov.hmrc.taxenrolmentassignmentfrontend.orchestrators.AccountCheckOrchestrator
import uk.gov.hmrc.taxenrolmentassignmentfrontend.reporting.{AuditEvent, AuditHandler}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.SessionKeys.REDIRECT_URL
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.TEASessionCache
import uk.gov.hmrc.taxenrolmentassignmentfrontend.services.SilentAssignmentService

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

@Singleton
class AccountCheckController @Inject() (
  silentAssignmentService: SilentAssignmentService,
  authJourney: AuthJourney,
  accountCheckOrchestrator: AccountCheckOrchestrator,
  auditHandler: AuditHandler,
  mcc: MessagesControllerComponents,
  sessionCache: TEASessionCache,
  appConfig: AppConfig,
  val logger: EventLoggerService,
  errorHandler: ErrorHandler
)(implicit ec: ExecutionContext)
    extends TEAFrontendController(mcc) {

  def accountCheck(redirectUrl: RedirectUrl): Action[AnyContent] = authJourney.authJourney.async { implicit request =>
    Try {
      redirectUrl.get(OnlyRelative | AbsoluteWithHostnameFromAllowlist(appConfig.validRedirectHostNames)).url
    } match {
      case Success(redirectUrlString) =>
        handleRequest(redirectUrlString).value.flatMap {
          case Right(accountType) => handleUsers(accountType, redirectUrlString)
          case Left(error)        =>
            Future.successful(
              errorHandler.handleErrors(error, "[AccountCheckController][accountCheck]")
            )
        }
      case Failure(error)             =>
        logger.logEvent(logInvalidRedirectUrl(error.getMessage), error)
        Future.successful(
          errorHandler.handleErrors(InvalidRedirectUrl, "[AccountCheckController][accountCheck]")
        )
    }

  }

  private def handleRequest(redirectUrl: String)(implicit
    request: RequestWithUserDetailsFromSession[AnyContent],
    hc: HeaderCarrier
  ): TEAFResult[AccountTypes.Value] =
    for {
      _           <- EitherT.right[TaxEnrolmentAssignmentErrors](
                       sessionCache.save[String](REDIRECT_URL, redirectUrl)(request, implicitly)
                     )
      accountType <- accountCheckOrchestrator.getAccountType
      _           <- enrolForPTIfRequired(accountType)
    } yield accountType

  private def handleUsers(accountType: AccountTypes.Value, redirectUrl: String)(implicit
    request: RequestWithUserDetailsFromSession[AnyContent]
  ): Future[Result] =
    accountType match {
      case PT_ASSIGNED_TO_OTHER_USER                                       => Future.successful(Redirect(routes.PTEnrolmentOnOtherAccountController.view))
      case SA_ASSIGNED_TO_OTHER_USER if request.userDetails.hasPTEnrolment =>
        Future.successful(Redirect(routes.EnrolledPTWithSAOnOtherAccountController.view))
      case SA_ASSIGNED_TO_OTHER_USER                                       => Future.successful(Redirect(routes.SABlueInterruptController.view))
      case MULTIPLE_ACCOUNTS                                               => Future.successful(Redirect(routes.EnrolledForPTController.view))
      case SA_ASSIGNED_TO_CURRENT_USER                                     => Future.successful(Redirect(routes.EnrolledForPTWithSAController.view))
      case _                                                               =>
        logger.logEvent(
          logRedirectingToReturnUrl(
            request.userDetails.credId,
            "[AccountCheckController][accountCheck]"
          )
        )
        sessionCache.removeRecord.map(_ => Redirect(redirectUrl))
    }

  private def enrolForPTIfRequired(accountType: AccountTypes.Value)(implicit
    request: RequestWithUserDetailsFromSession[AnyContent],
    hc: HeaderCarrier
  ): TEAFResult[Unit] = {
    val accountTypesToEnrolForPT = List(SINGLE_ACCOUNT, MULTIPLE_ACCOUNTS, SA_ASSIGNED_TO_CURRENT_USER)
    val hasPTEnrolmentAlready    = request.userDetails.hasPTEnrolment
    if (!hasPTEnrolmentAlready && accountTypesToEnrolForPT.contains(accountType)) {
      silentAssignmentService.enrolUser().flatMap { _ =>
      // TODO: Tidy this up:-
        val hh = authJourney.accountDetailsFromMongo(request).map { x =>
          val r = x.map { req =>
            auditHandler.audit(AuditEvent.auditSuccessfullyEnrolledPTWhenSANotOnOtherAccount(accountType)(req))
          }
          r
        }

        EitherT.right(if (accountType == SINGLE_ACCOUNT) {
          Future.successful(
            logger.logEvent(
              logSingleAccountHolderAssignedEnrolment(request.userDetails.credId, request.userDetails.nino)
            )
          )
        } else {
          Future.successful(
            logger.logEvent(
              logMultipleAccountHolderAssignedEnrolment(request.userDetails.credId, request.userDetails.nino)
            )
          )
        })
      }
    } else if (hasPTEnrolmentAlready) {
      EitherT.right(sessionCache.removeRecord.map(_ => (): Unit))
    } else {
      EitherT.right(Future.successful((): Unit))
    }
  }
}
