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
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes.SA_ASSIGNED_TO_OTHER_USER
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.actions.{AccountMongoDetailsAction, AuthAction, ThrottleAction}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.helpers.{ErrorHandler, TEAFrontendController}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.logging.EventLoggerService
import uk.gov.hmrc.taxenrolmentassignmentfrontend.orchestrators.MultipleAccountsOrchestrator
import uk.gov.hmrc.taxenrolmentassignmentfrontend.views.html.SABlueInterrupt

@Singleton
class SABlueInterruptController @Inject()(
  authAction: AuthAction,
  accountMongoDetailsAction: AccountMongoDetailsAction,
  throttleAction: ThrottleAction,
  mcc: MessagesControllerComponents,
  multipleAccountsOrchestrator: MultipleAccountsOrchestrator,
  val logger: EventLoggerService,
  saBlueInterrupt: SABlueInterrupt,
  errorHandler: ErrorHandler
)
    extends TEAFrontendController(mcc)   {

  def view: Action[AnyContent] =
    authAction.andThen(accountMongoDetailsAction).andThen(throttleAction) { implicit request =>
      multipleAccountsOrchestrator
        .checkAccessAllowedForPage(List(SA_ASSIGNED_TO_OTHER_USER)) match {
          case Right(_) =>
            Ok(saBlueInterrupt())
          case Left(error) =>
            errorHandler.handleErrors(error, "[SABlueInterruptController][view]")(request, implicitly)
        }
    }

  def continue: Action[AnyContent] =
    authAction.andThen(accountMongoDetailsAction).andThen(throttleAction) { implicit request =>
      multipleAccountsOrchestrator
        .checkAccessAllowedForPage(List(SA_ASSIGNED_TO_OTHER_USER)) match {
          case Right(_) => Redirect(routes.KeepAccessToSAController.view)
          case Left(error) =>
            errorHandler.handleErrors(error, "[SABlueInterruptController][continue]")(request, implicitly)
        }
    }
}
