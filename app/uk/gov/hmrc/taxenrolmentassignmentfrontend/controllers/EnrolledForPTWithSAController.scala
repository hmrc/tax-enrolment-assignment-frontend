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

import play.api.Logger
import play.api.http.ContentTypeOf.contentTypeOf_Html
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.play.bootstrap.controller.WithDefaultFormBinding
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.actions.{AccountMongoDetailsAction, AuthAction, ThrottleAction}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.logging.EventLoggerService
import uk.gov.hmrc.taxenrolmentassignmentfrontend.logging.LoggingEvent.logRedirectingToReturnUrl
import uk.gov.hmrc.taxenrolmentassignmentfrontend.orchestrators.MultipleAccountsOrchestrator
import uk.gov.hmrc.taxenrolmentassignmentfrontend.views.html.EnrolledForPTPage

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class EnrolledForPTWithSAController @Inject()(
                                         authAction: AuthAction,
                                         accountMongoDetailsAction: AccountMongoDetailsAction,
                                         throttleAction: ThrottleAction,
                                         mcc: MessagesControllerComponents,
                                         multipleAccountsOrchestrator: MultipleAccountsOrchestrator,
                                         val logger: EventLoggerService,
                                         enrolledForPTPage: EnrolledForPTPage,
                                         errorHandler: ErrorHandler
)(implicit ec: ExecutionContext)
    extends FrontendController(mcc)
    with I18nSupport
      with WithDefaultFormBinding {

  implicit val baseLogger: Logger = Logger(this.getClass.getName)

  def view: Action[AnyContent] = authAction.andThen(accountMongoDetailsAction).andThen(throttleAction).async { implicit request =>
    multipleAccountsOrchestrator.getDetailsForEnrolledPT(request, implicitly, implicitly).value.map {
      case Right(accountDetails) =>
        Ok(
          enrolledForPTPage(
            accountDetails.userId,
            true,
            routes.EnrolledForPTWithSAController.continue
          )
        )
      case Left(error) =>
        errorHandler.handleErrors(error, "[EnrolledForPTWithSAController][view]")(request, implicitly)
    }
  }

  def continue: Action[AnyContent] = authAction.andThen(accountMongoDetailsAction).andThen(throttleAction) { implicit request =>
    logger.logEvent(
          logRedirectingToReturnUrl(
            request.userDetails.credId,
            "[EnrolledForPTWithSAController][continue]"
          )
        )
        Redirect(request.accountDetailsFromMongo.redirectUrl)

  }
}