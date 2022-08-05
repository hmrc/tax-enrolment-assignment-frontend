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
import uk.gov.hmrc.taxenrolmentassignmentfrontend.logging.LoggingEvent.logRedirectingToReturnUrl
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.AccountDetails
import uk.gov.hmrc.taxenrolmentassignmentfrontend.orchestrators.MultipleAccountsOrchestrator
import uk.gov.hmrc.taxenrolmentassignmentfrontend.views.html.EnrolledForPTWithSAOnOtherAccount

import scala.concurrent.ExecutionContext

@Singleton
class EnrolledPTWithSAOnOtherAccountController @Inject()(
  authAction: AuthAction,
  accountMongoDetailsAction: AccountMongoDetailsAction,
  throttleAction: ThrottleAction,
  multipleAccountsOrchestrator: MultipleAccountsOrchestrator,
  mcc: MessagesControllerComponents,
  enrolledForPTPage: EnrolledForPTWithSAOnOtherAccount,
  val logger: EventLoggerService,
  errorHandler: ErrorHandler
)(implicit ec: ExecutionContext)
    extends TEAFrontendController(mcc) {

  def view(): Action[AnyContent] = authAction.andThen(accountMongoDetailsAction).andThen(throttleAction).async { implicit request =>
    val res = for {
      currentAccount <- multipleAccountsOrchestrator.getDetailsForEnrolledPTWithSAOnOtherAccount
      optSAAccount <- multipleAccountsOrchestrator.getSACredentialIfNotFraud

    } yield (AccountDetails.userFriendlyAccountDetails(currentAccount).userId, optSAAccount.map(AccountDetails.userFriendlyAccountDetails).map(_.userId))

    res.value.map {
      case Right((currentUserId, optSAUserId)) =>
        Ok(enrolledForPTPage(currentUserId, optSAUserId))
      case Left(error) =>
        errorHandler.handleErrors(error, "[EnrolledPTWithSAOnOtherAccountController][view]")(request, implicitly)
    }
  }

  def continue: Action[AnyContent] = authAction.andThen(accountMongoDetailsAction).andThen(throttleAction) { implicit request =>
        logger.logEvent(
          logRedirectingToReturnUrl(
            request.userDetails.credId,
            "[EnrolledWithSAOnOtherAccountController][continue]"
          )
        )
        Redirect(request.accountDetailsFromMongo.redirectUrl)
  }

}
