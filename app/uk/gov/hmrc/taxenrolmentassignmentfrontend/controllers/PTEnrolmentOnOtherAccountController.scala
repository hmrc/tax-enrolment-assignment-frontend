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

import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.actions.{AccountMongoDetailsAction, AuthAction}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.helpers.{ErrorHandler, TEAFrontendController}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.logging.EventLoggerService
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.{AccountDetails, PTEnrolmentOnOtherAccount}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.orchestrators.MultipleAccountsOrchestrator
import uk.gov.hmrc.taxenrolmentassignmentfrontend.reporting.{AuditEvent, AuditHandler}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.views.html.PTEnrolmentOnAnotherAccount

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class PTEnrolmentOnOtherAccountController @Inject() (
  authAction: AuthAction,
  accountMongoDetailsAction: AccountMongoDetailsAction,
  mcc: MessagesControllerComponents,
  multipleAccountsOrchestrator: MultipleAccountsOrchestrator,
  ptEnrolmentOnAnotherAccountView: PTEnrolmentOnAnotherAccount,
  val logger: EventLoggerService,
  errorHandler: ErrorHandler,
  auditHandler: AuditHandler
)(implicit ec: ExecutionContext)
    extends TEAFrontendController(mcc) {

  def view: Action[AnyContent] =
    authAction.andThen(accountMongoDetailsAction).async { implicit request =>
      val res = multipleAccountsOrchestrator.getCurrentAndPTAAndSAIfExistsForUser

      res.value.map {
        case Right(accountDetails) =>
          val accountFriendlyDetails = AccountDetails.userFriendlyAccountDetails(accountDetails.ptAccountDetails)
          auditHandler
            .audit(AuditEvent.auditPTEnrolmentOnOtherAccount(accountFriendlyDetails))
          Ok(
            ptEnrolmentOnAnotherAccountView(
              PTEnrolmentOnOtherAccount(
                AccountDetails.userFriendlyAccountDetails(accountDetails.currentAccountDetails),
                accountFriendlyDetails,
                accountDetails.saUserCred.map(AccountDetails.trimmedUserId)
              )
            )
          )
        case Left(error) =>
          errorHandler.handleErrors(error, "[PTEnrolmentOnOtherAccountController][view]")(request, implicitly)
      }
    }
}
