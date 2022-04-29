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

import com.google.inject.{Inject, Singleton}
import play.api.Logger
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes.{PT_ASSIGNED_TO_OTHER_USER, SA_ASSIGNED_TO_OTHER_USER}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.config.AppConfig
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.actions.{AccountMongoDetailsAction, AuthAction}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.logging.EventLoggerService
import uk.gov.hmrc.taxenrolmentassignmentfrontend.logging.LoggingEvent.logAssignedEnrolmentAfterReportingFraud
import uk.gov.hmrc.taxenrolmentassignmentfrontend.orchestrators.MultipleAccountsOrchestrator
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.SessionKeys.REPORTED_FRAUD
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.TEASessionCache
import uk.gov.hmrc.taxenrolmentassignmentfrontend.views.html.ReportSuspiciousID
import uk.gov.hmrc.taxenrolmentassignmentfrontend.views.html.templates.ErrorTemplate

import scala.concurrent.ExecutionContext

@Singleton
class ReportSuspiciousIDController @Inject()(
  authAction: AuthAction,
  accountMongoDetailsAction: AccountMongoDetailsAction,
  sessionCache: TEASessionCache,
  multipleAccountsOrchestrator: MultipleAccountsOrchestrator,
  mcc: MessagesControllerComponents,
  reportSuspiciousID: ReportSuspiciousID,
  val logger: EventLoggerService,
  errorHandler: ErrorHandler
)(implicit ec: ExecutionContext, appConfig: AppConfig)
    extends FrontendController(mcc)
    with I18nSupport {

  implicit val baseLogger: Logger = Logger(this.getClass.getName)

  def viewNoSA(): Action[AnyContent] = authAction.andThen(accountMongoDetailsAction).async { implicit request =>
    val res = for {
      _ <- multipleAccountsOrchestrator.checkValidAccountTypeRedirectUrlInCache(
        List(PT_ASSIGNED_TO_OTHER_USER)
      )
      ptAccount <- multipleAccountsOrchestrator.getPTCredentialDetails
    } yield ptAccount

    res.value.map {
      case Right(ptAccount) =>
        Ok(reportSuspiciousID(ptAccount))
      case Left(error) =>
        errorHandler.handleErrors(error, "[ReportSuspiciousIDController][viewNoSA]")(request, implicitly)
    }
  }

  def viewSA(): Action[AnyContent] = authAction.andThen(accountMongoDetailsAction).async { implicit request =>
    val res = for {
      _ <- multipleAccountsOrchestrator.checkValidAccountTypeRedirectUrlInCache(
        List(SA_ASSIGNED_TO_OTHER_USER)
      )
      saAccount <- multipleAccountsOrchestrator.getSACredentialDetails
    } yield saAccount

    res.value.map {
      case Right(saAccount) =>
        Ok(reportSuspiciousID(saAccount, true))
      case Left(error) =>
        errorHandler.handleErrors(error, "[ReportSuspiciousIDController][viewSA]")(request, implicitly)
    }
  }

  def continue: Action[AnyContent] = authAction.andThen(accountMongoDetailsAction).async { implicit request =>
    sessionCache.save[Boolean](REPORTED_FRAUD, true)(request, implicitly)
    multipleAccountsOrchestrator
      .checkValidAccountTypeAndEnrolForPT(SA_ASSIGNED_TO_OTHER_USER)
      .value
      .map {
        case Right(_) =>
          logger.logEvent(
            logAssignedEnrolmentAfterReportingFraud(request.userDetails.credId)
          )
          Redirect(routes.EnrolledPTWithSAOnOtherAccountController.view)
        case Left(error) =>
          errorHandler.handleErrors(error, "[ReportSuspiciousIdController][continue]")(request, implicitly)
      }
  }
}
