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

import javax.inject.{Inject, Singleton}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Result}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes.{PT_ASSIGNED_TO_OTHER_USER, SA_ASSIGNED_TO_OTHER_USER}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.actions.{AccountMongoDetailsAction, AuthAction, RequestWithUserDetailsFromSessionAndMongo}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.helpers.{ErrorHandler, TEAFrontendController}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.logging.EventLoggerService
import uk.gov.hmrc.taxenrolmentassignmentfrontend.logging.LoggingEvent.logAssignedEnrolmentAfterReportingFraud
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.AccountDetails
import uk.gov.hmrc.taxenrolmentassignmentfrontend.orchestrators.MultipleAccountsOrchestrator
import uk.gov.hmrc.taxenrolmentassignmentfrontend.reporting.{AuditEvent, AuditHandler}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.SessionKeys.REPORTED_FRAUD
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.TEASessionCache
import uk.gov.hmrc.taxenrolmentassignmentfrontend.views.html.{ReportSuspiciousIDGateway, ReportSuspiciousIDOneLogin}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.services.AuditEventCreationService
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ReportSuspiciousIDController @Inject() (
  authAction: AuthAction,
  accountMongoDetailsAction: AccountMongoDetailsAction,
  sessionCache: TEASessionCache,
  multipleAccountsOrchestrator: MultipleAccountsOrchestrator,
  mcc: MessagesControllerComponents,
  reportSuspiciousIDGateway: ReportSuspiciousIDGateway,
  reportSuspiciousIDOneLogin: ReportSuspiciousIDOneLogin,
  val logger: EventLoggerService,
  auditHandler: AuditHandler,
  errorHandler: ErrorHandler,
  auditEventCreationService: AuditEventCreationService
)(implicit ec: ExecutionContext)
    extends TEAFrontendController(mcc) {

  private def identityProviderPage(
    accountDetails: AccountDetails
  )(implicit request: RequestWithUserDetailsFromSessionAndMongo[AnyContent]): Result =
    if (accountDetails.isIdentityProviderOneLogin) {
      Ok(reportSuspiciousIDOneLogin(accountDetails))
    } else {
      Ok(reportSuspiciousIDGateway(accountDetails))
    }

  def viewNoSA: Action[AnyContent] =
    authAction.andThen(accountMongoDetailsAction).async { implicit request =>
      val res = for {
        _ <- EitherT {
               Future.successful(
                 multipleAccountsOrchestrator
                   .checkValidAccountType(List(PT_ASSIGNED_TO_OTHER_USER))
               )
             }
        ptAccount <- multipleAccountsOrchestrator.getPTCredentialDetails
      } yield AccountDetails.userFriendlyAccountDetails(ptAccount)

      res.value.map {
        case Right(ptAccount) =>
          auditHandler
            .audit(auditEventCreationService.auditReportSuspiciousPTAccount(ptAccount))
          identityProviderPage(ptAccount)
        case Left(error) =>
          errorHandler.handleErrors(
            error,
            "[ReportSuspiciousIDController][viewNoSA]"
          )(request, implicitly)
      }
    }

  def viewSA: Action[AnyContent] =
    authAction.andThen(accountMongoDetailsAction).async { implicit request =>
      val res = for {
        _ <- EitherT {
               Future.successful(
                 multipleAccountsOrchestrator
                   .checkValidAccountType(List(SA_ASSIGNED_TO_OTHER_USER))
               )
             }
        saAccount <- multipleAccountsOrchestrator.getSACredentialDetails
      } yield AccountDetails.userFriendlyAccountDetails(saAccount)

      res.value.map {
        case Right(saAccount) =>
          if (!request.userDetails.hasPTEnrolment) {
            auditHandler
              .audit(auditEventCreationService.auditReportSuspiciousSAAccount(saAccount))
          }
          identityProviderPage(saAccount)
        case Left(error) =>
          errorHandler.handleErrors(
            error,
            "[ReportSuspiciousIDController][viewSA]"
          )(request, implicitly)
      }
    }

  def continue: Action[AnyContent] =
    authAction.andThen(accountMongoDetailsAction).async { implicit request =>
      sessionCache.save[Boolean](REPORTED_FRAUD, true)(request, implicitly)
      multipleAccountsOrchestrator
        .checkValidAccountTypeAndEnrolForPT(SA_ASSIGNED_TO_OTHER_USER)
        .value
        .map {
          case Right(_) =>
            logger.logEvent(
              logAssignedEnrolmentAfterReportingFraud(
                request.userDetails.credId
              )
            )
            auditHandler.audit(auditEventCreationService.auditSuccessfullyEnrolledPTWhenSAOnOtherAccount(true))
            Redirect(routes.EnrolledForPTController.view)
          case Left(error) =>
            errorHandler.handleErrors(
              error,
              "[ReportSuspiciousIdController][continue]"
            )(request, implicitly)
        }
    }
}
