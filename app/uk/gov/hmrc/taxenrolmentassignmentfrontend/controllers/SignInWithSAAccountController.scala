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
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes.SA_ASSIGNED_TO_OTHER_USER
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.actions.{AccountMongoDetailsAction, AuthAction}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.helpers.{ErrorHandler, TEAFrontendController}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.logging.EventLoggerService
import uk.gov.hmrc.taxenrolmentassignmentfrontend.logging.LoggingEvent._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.AccountDetails
import uk.gov.hmrc.taxenrolmentassignmentfrontend.orchestrators.MultipleAccountsOrchestrator
import uk.gov.hmrc.taxenrolmentassignmentfrontend.reporting.{AuditEvent, AuditHandler}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.views.html.SignInWithSAAccount
import uk.gov.hmrc.taxenrolmentassignmentfrontend.services.AuditEventCreationService
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SignInWithSAAccountController @Inject() (
  authAction: AuthAction,
  accountMongoDetailsAction: AccountMongoDetailsAction,
  mcc: MessagesControllerComponents,
  multipleAccountsOrchestrator: MultipleAccountsOrchestrator,
  signInWithSAAccount: SignInWithSAAccount,
  val logger: EventLoggerService,
  auditHandler: AuditHandler,
  errorHandler: ErrorHandler,
  auditEventCreationService: AuditEventCreationService
)(implicit ec: ExecutionContext)
    extends TEAFrontendController(mcc) {

  def view: Action[AnyContent] =
    authAction.andThen(accountMongoDetailsAction).async { implicit request =>
      val res = for {
        _ <- EitherT {
               Future.successful(
                 multipleAccountsOrchestrator.checkAccessAllowedForPage(
                   List(SA_ASSIGNED_TO_OTHER_USER)
                 )
               )
             }
        saAccount <- multipleAccountsOrchestrator.getSACredentialDetails
      } yield AccountDetails.userFriendlyAccountDetails(saAccount)

      res.value.map {
        case Right(saAccount) =>
          Ok(signInWithSAAccount(saAccount))
        case Left(error) =>
          errorHandler.handleErrors(error, "[SignInWithSAAccountController][view]")(request, implicitly)
      }
    }

  def continue: Action[AnyContent] =
    authAction.andThen(accountMongoDetailsAction) { implicit request =>
      logger.logEvent(
        logUserSignsInAgainWithSAAccount(request.userDetails.credId)
      )
      auditHandler.audit(auditEventCreationService.auditSigninAgainWithSACredential())
      Redirect(routes.SignOutController.signOut)
    }
}
