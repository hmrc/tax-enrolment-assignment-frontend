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
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes.{PT_ASSIGNED_TO_OTHER_USER, SA_ASSIGNED_TO_OTHER_USER}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.actions.{AccountMongoDetailsAction, AuthJourney}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.helpers.{ErrorHandler, TEAFrontendController}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.logging.EventLoggerService
import uk.gov.hmrc.taxenrolmentassignmentfrontend.logging.LoggingEvent.logAssignedEnrolmentAfterReportingFraud
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.AccountDetails
import uk.gov.hmrc.taxenrolmentassignmentfrontend.orchestrators.MultipleAccountsOrchestrator
import uk.gov.hmrc.taxenrolmentassignmentfrontend.pages.ReportedFraudPage
import uk.gov.hmrc.taxenrolmentassignmentfrontend.reporting.{AuditEvent, AuditHandler}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.JourneyCacheRepository
import uk.gov.hmrc.taxenrolmentassignmentfrontend.services.TENCrypto
import uk.gov.hmrc.taxenrolmentassignmentfrontend.views.html.ReportSuspiciousID

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ReportSuspiciousIDController @Inject() (
  accountMongoDetailsAction: AccountMongoDetailsAction,
  multipleAccountsOrchestrator: MultipleAccountsOrchestrator,
  mcc: MessagesControllerComponents,
  reportSuspiciousID: ReportSuspiciousID,
  val logger: EventLoggerService,
  auditHandler: AuditHandler,
  errorHandler: ErrorHandler,
  authJourney: AuthJourney,
  journeyCacheRepository: JourneyCacheRepository
)(implicit ec: ExecutionContext, crypto: TENCrypto)
    extends TEAFrontendController(mcc) {

  def viewNoSA: Action[AnyContent] =
    authJourney.authWithDataRetrieval.andThen(accountMongoDetailsAction).async { implicit request =>
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
            .audit(AuditEvent.auditReportSuspiciousPTAccount(ptAccount))
          Ok(reportSuspiciousID(ptAccount))
        case Left(error) =>
          errorHandler.handleErrors(
            error,
            "[ReportSuspiciousIDController][viewNoSA]"
          )(request, implicitly)
      }
    }

  def viewSA: Action[AnyContent] =
    authJourney.authWithDataRetrieval.andThen(accountMongoDetailsAction).async { implicit request =>
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
              .audit(AuditEvent.auditReportSuspiciousSAAccount(saAccount))
          }
          Ok(reportSuspiciousID(saAccount, saOnOtherAccountJourney = true))
        case Left(error) =>
          errorHandler.handleErrors(
            error,
            "[ReportSuspiciousIDController][viewSA]"
          )(request, implicitly)
      }
    }

  def continue: Action[AnyContent] =
    authJourney.authWithDataRetrieval.andThen(accountMongoDetailsAction).async { implicit request =>
      journeyCacheRepository.set(request.userAnswers.setOrException(ReportedFraudPage, true))
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
            auditHandler
              .audit(AuditEvent.auditSuccessfullyEnrolledPTWhenSAOnOtherAccount(enrolledAfterReportingFraud = true))
            Redirect(routes.EnrolledForPTController.view)
          case Left(error) =>
            errorHandler.handleErrors(
              error,
              "[ReportSuspiciousIdController][continue]"
            )(request, implicitly)
        }
    }
}
