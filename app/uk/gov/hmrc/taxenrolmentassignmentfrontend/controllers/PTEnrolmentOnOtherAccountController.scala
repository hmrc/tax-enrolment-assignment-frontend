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
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.actions.{AccountMongoDetailsAction, AuthAction, ThrottleAction}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.helpers.{ErrorHandler, TEAFrontendController}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.logging.EventLoggerService
import uk.gov.hmrc.taxenrolmentassignmentfrontend.orchestrators.MultipleAccountsOrchestrator
import uk.gov.hmrc.taxenrolmentassignmentfrontend.views.html.PTEnrolmentOnAnotherAccount

import scala.concurrent.ExecutionContext

@Singleton
class PTEnrolmentOnOtherAccountController @Inject()(
  authAction: AuthAction,
  accountMongoDetailsAction: AccountMongoDetailsAction,
  throttleAction: ThrottleAction,
  mcc: MessagesControllerComponents,
  multipleAccountsOrchestrator: MultipleAccountsOrchestrator,
  ptEnrolmentOnAnotherAccountView: PTEnrolmentOnAnotherAccount,
  val logger: EventLoggerService,
  errorHandler: ErrorHandler
)(implicit ec: ExecutionContext) extends TEAFrontendController(mcc) {

  def view(): Action[AnyContent] = authAction.andThen(accountMongoDetailsAction).andThen(throttleAction).async { implicit request =>

    val res = multipleAccountsOrchestrator.getCurrentAndPTAAndSAIfExistsForUser

    res.value.map {
      case Right(accountDetails) =>
        Ok(
          ptEnrolmentOnAnotherAccountView(
            accountDetails
          )
        )
      case Left(error) =>
        errorHandler.handleErrors(error, "[PTEnrolmentOnOtherAccountController][view]")(request, implicitly)
    }
  }
}
