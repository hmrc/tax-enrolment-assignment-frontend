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

import com.google.inject.Inject
import play.api.Logger
import play.api.i18n.{I18nSupport, Messages}
import play.api.mvc.{MessagesControllerComponents, Result}
import play.api.mvc.Results.{InternalServerError, Redirect}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.actions.RequestWithUserDetailsFromSession
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.{InvalidUserType, TaxEnrolmentAssignmentErrors}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.logging.EventLoggerService
import uk.gov.hmrc.taxenrolmentassignmentfrontend.logging.LoggingEvent.logUnexpectedErrorOccurred
import uk.gov.hmrc.taxenrolmentassignmentfrontend.views.html.templates.ErrorTemplate

class ErrorHandler @Inject()(errorView: ErrorTemplate, logger: EventLoggerService, mcc: MessagesControllerComponents) extends FrontendController(mcc) with I18nSupport {

  def handleErrors(error: TaxEnrolmentAssignmentErrors, classAndMethod: String)(
    implicit request: RequestWithUserDetailsFromSession[_],
    baseLogger: Logger
  ): Result = {
    error match {
      case InvalidUserType(redirectUrl) if redirectUrl.isDefined =>
        Redirect(routes.AccountCheckController.accountCheck(redirectUrl.get))
      case _ =>
        logger.logEvent(
          logUnexpectedErrorOccurred(
            request.userDetails.credId,
            classAndMethod,
            error
          )
        )
        InternalServerError(errorView())
    }
  }
}