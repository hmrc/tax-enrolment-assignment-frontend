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

import play.api.Logger
import play.api.http.ContentTypeOf.contentTypeOf_Html
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Result}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.config.AppConfig
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.actions.{AuthAction, RequestWithUserDetailsFromSession, ThrottleAction}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.logging.EventLoggerService
import uk.gov.hmrc.taxenrolmentassignmentfrontend.logging.LoggingEvent._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.orchestrators.AccountCheckOrchestrator
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.SessionKeys.REDIRECT_URL
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.TEASessionCache
import uk.gov.hmrc.taxenrolmentassignmentfrontend.services.SilentAssignmentService
import uk.gov.hmrc.taxenrolmentassignmentfrontend.views.html.templates.ErrorTemplate
import uk.gov.hmrc.play.bootstrap.controller.WithDefaultFormBinding
import uk.gov.hmrc.taxenrolmentassignmentfrontend.reporting.{AuditEvent, AuditHandler}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AccountCheckController @Inject()(
                                        silentAssignmentService: SilentAssignmentService,
                                        throttleAction: ThrottleAction,
                                        authAction: AuthAction,
                                        accountCheckOrchestrator: AccountCheckOrchestrator,
                                        auditHandler: AuditHandler,
                                        appConfig: AppConfig,
                                        mcc: MessagesControllerComponents,
                                        sessionCache: TEASessionCache,
                                        val logger: EventLoggerService,
                                        errorHandler: ErrorHandler,
                                        errorView: ErrorTemplate
)(implicit ec: ExecutionContext)
    extends FrontendController(mcc)
    with I18nSupport
      with WithDefaultFormBinding {

  implicit val baseLogger: Logger = Logger(this.getClass.getName)

  def accountCheck(redirectUrl: String): Action[AnyContent] = authAction.async {
    implicit request =>
      sessionCache.save[String](REDIRECT_URL, redirectUrl)(request, implicitly).flatMap { _ =>
        accountCheckOrchestrator.getAccountType.value.flatMap {
          case Right(anyAccountType) => throttleAction.throttle(anyAccountType, redirectUrl).flatMap {
            case Some(redirectResult) => Future.successful(redirectResult)
            case _ => anyAccountType match {
              case PT_ASSIGNED_TO_CURRENT_USER =>
                logger.logEvent(
                  logRedirectingToReturnUrl(
                    request.userDetails.credId,
                    "[AccountCheckController][accountCheck]"
                  )
                )
                Future.successful(Redirect(redirectUrl))
              case PT_ASSIGNED_TO_OTHER_USER =>
                Future.successful(
                  Redirect(routes.PTEnrolmentOnOtherAccountController.view)
                )
              case SA_ASSIGNED_TO_OTHER_USER =>
                Future.successful(Redirect(routes.SABlueInterruptController.view))
              case accountType => silentEnrolmentAndRedirect(accountType, redirectUrl)
            }
          }
          case Left(error) =>
            Future.successful(
              errorHandler.handleErrors(error, "[AccountCheckController][accountCheck]")
            )
        }
      }
  }

      private def silentEnrolmentAndRedirect(accountType: AccountTypes.Value, usersRedirectUrl: String)(
        implicit request: RequestWithUserDetailsFromSession[_],
        hc: HeaderCarrier
      ): Future[Result] = {
        silentAssignmentService.enrolUser().isRight map {
          case true =>
            auditHandler.audit(AuditEvent.auditSuccessfullyEnrolledPTWhenSANotOnOtherAccount(accountType))
            if (accountType == SINGLE_ACCOUNT) {
              logger.logEvent(
                logSingleAccountHolderAssignedEnrolment(request.userDetails.credId)
              )
              logger.logEvent(
                logRedirectingToReturnUrl(request.userDetails.credId,"[AccountCheckController][accountCheck]"
              )
            )
              Redirect(usersRedirectUrl)

            }
            else if (accountType == SA_ASSIGNED_TO_CURRENT_USER) {
            logger.logEvent(
              logMultipleAccountHolderAssignedEnrolment(request.userDetails.credId)
            )
            Redirect(routes.EnrolledForPTWithSAController.view)
            }
            else {
              logger.logEvent(
                logMultipleAccountHolderAssignedEnrolment(request.userDetails.credId)
              )
              Redirect(routes.EnrolledForPTController.view)
        }
          case false =>
            InternalServerError(errorView())
        }
      }

  }

