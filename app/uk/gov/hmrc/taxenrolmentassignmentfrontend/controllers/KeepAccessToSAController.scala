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

import play.api.mvc._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.actions.{AccountMongoDetailsAction, AuthJourney}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.helpers.{ErrorHandler, TEAFrontendController}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.forms.KeepAccessToSAThroughPTAForm
import uk.gov.hmrc.taxenrolmentassignmentfrontend.logging.EventLoggerService
import uk.gov.hmrc.taxenrolmentassignmentfrontend.orchestrators.MultipleAccountsOrchestrator
import uk.gov.hmrc.taxenrolmentassignmentfrontend.reporting.{AuditEvent, AuditHandler}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.services.TENCrypto
import uk.gov.hmrc.taxenrolmentassignmentfrontend.views.html.KeepAccessToSA

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class KeepAccessToSAController @Inject() (
  accountMongoDetailsAction: AccountMongoDetailsAction,
  multipleAccountsOrchestrator: MultipleAccountsOrchestrator,
  mcc: MessagesControllerComponents,
  val logger: EventLoggerService,
  keepAccessToSA: KeepAccessToSA,
  auditHandler: AuditHandler,
  errorHandler: ErrorHandler,
  authJourney: AuthJourney
)(implicit ec: ExecutionContext, crypto: TENCrypto)
    extends TEAFrontendController(mcc) {

  def view: Action[AnyContent] =
    authJourney.authWithDataRetrieval.andThen(accountMongoDetailsAction).async { implicit request =>
      multipleAccountsOrchestrator.getDetailsForKeepAccessToSA.value.map {
        case Right(form) => Ok(keepAccessToSA(form))
        case Left(error) =>
          errorHandler.handleErrors(error, "[KeepAccessToSAController][view]")(request, implicitly)
      }
    }

  def continue: Action[AnyContent] =
    authJourney.authWithDataRetrieval.andThen(accountMongoDetailsAction).async { implicit request =>
      KeepAccessToSAThroughPTAForm.keepAccessToSAThroughPTAForm
        .bindFromRequest()
        .fold(
          formWithErrors => Future.successful(BadRequest(keepAccessToSA(formWithErrors))),
          keepAccessToSA =>
            multipleAccountsOrchestrator
              .handleKeepAccessToSAChoice(keepAccessToSA)
              .value
              .map {
                case Right(true) =>
                  Redirect(routes.SignInWithSAAccountController.view)
                case Right(false) =>
                  auditHandler.audit(AuditEvent.auditSuccessfullyEnrolledPTWhenSAOnOtherAccount())
                  Redirect(routes.EnrolledPTWithSAOnOtherAccountController.view)
                case Left(error) =>
                  errorHandler.handleErrors(error, "[KeepAccessToSAController][continue]")(request, implicitly)
              }
        )
    }
}
