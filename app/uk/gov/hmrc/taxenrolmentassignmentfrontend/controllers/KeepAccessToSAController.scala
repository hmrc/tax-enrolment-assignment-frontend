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

import javax.inject.{Inject, Singleton}
import play.api.mvc._

import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.actions.{AccountMongoDetailsAction, AuthAction}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.helpers.{ErrorHandler, TEAFrontendController}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.forms.KeepAccessToSAThroughPTAForm
import uk.gov.hmrc.taxenrolmentassignmentfrontend.logging.EventLoggerService
import uk.gov.hmrc.taxenrolmentassignmentfrontend.orchestrators.MultipleAccountsOrchestrator
import uk.gov.hmrc.taxenrolmentassignmentfrontend.reporting.{AuditEvent, AuditHandler}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.views.html.KeepAccessToSA

import scala.concurrent.ExecutionContext

@Singleton
class KeepAccessToSAController @Inject() (
  authAction: AuthAction,
  accountMongoDetailsAction: AccountMongoDetailsAction,
  multipleAccountsOrchestrator: MultipleAccountsOrchestrator,
  mcc: MessagesControllerComponents,
  val logger: EventLoggerService,
  keepAccessToSA: KeepAccessToSA,
  auditHandler: AuditHandler,
  errorHandler: ErrorHandler
)(implicit ec: ExecutionContext)
    extends TEAFrontendController(mcc) {

  def view: Action[AnyContent] =
    authAction.andThen(accountMongoDetailsAction).async { implicit request =>
      val form = request.accountDetailsFromMongo.optKeepAccessToSAFormData match {
        case Some(details) => KeepAccessToSAThroughPTAForm.keepAccessToSAThroughPTAForm.fill(details)
        case None          => KeepAccessToSAThroughPTAForm.keepAccessToSAThroughPTAForm
      }
      multipleAccountsOrchestrator.getSAAndCADetails.fold(
        error => errorHandler.handleErrors(error, "[KeepAccessToSAController][view]")(request, implicitly),
        accountDetails =>
          Ok(keepAccessToSA(form, accountDetails.currentAccountDetails, accountDetails.saAccountDetails))
      )
    }

  def continue: Action[AnyContent] =
    authAction.andThen(accountMongoDetailsAction).async { implicit request =>
      KeepAccessToSAThroughPTAForm.keepAccessToSAThroughPTAForm
        .bindFromRequest()
        .fold(
          formWithErrors =>
            multipleAccountsOrchestrator.getSAAndCADetails.fold(
              error => errorHandler.handleErrors(error, "[KeepAccessToSAController][view]")(request, implicitly),
              accountDetails =>
                BadRequest(
                  keepAccessToSA(
                    formWithErrors,
                    accountDetails.currentAccountDetails,
                    accountDetails.saAccountDetails
                  )
                )
            ),
          keepAccessToSA =>
            multipleAccountsOrchestrator
              .handleKeepAccessToSAChoice(keepAccessToSA)
              .value
              .map {
                case Right(true)  =>
                  Redirect(routes.SignInWithSAAccountController.view)
                case Right(false) =>
                  auditHandler.audit(AuditEvent.auditSuccessfullyEnrolledPTWhenSAOnOtherAccount(false))
                  Redirect(routes.EnrolledPTWithSAOnOtherAccountController.view)
                case Left(error)  =>
                  errorHandler.handleErrors(error, "[KeepAccessToSAController][continue]")(request, implicitly)
              }
        )
    }
}
