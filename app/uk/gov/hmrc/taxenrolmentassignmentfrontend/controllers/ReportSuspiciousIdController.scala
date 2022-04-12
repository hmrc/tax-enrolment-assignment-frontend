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
import play.api.Logger
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes.SA_ASSIGNED_TO_OTHER_USER
import uk.gov.hmrc.taxenrolmentassignmentfrontend.config.AppConfig
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.auth.AuthAction
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.{
  InvalidUserType,
  UnexpectedResponseFromTaxEnrolments
}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.logging.EventLoggerService
import uk.gov.hmrc.taxenrolmentassignmentfrontend.logging.LoggingEvent.{
  logAssignedEnrolmentAfterReportingFraud,
  logUnexpectedErrorOccurred
}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.{
  AccountDetails,
  MFADetails
}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.orchestrators.MultipleAccountsOrchestrator
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.SessionKeys.REPORTED_FRAUD
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.TEASessionCache
import uk.gov.hmrc.taxenrolmentassignmentfrontend.views.html.ReportSuspiciousID
import uk.gov.hmrc.taxenrolmentassignmentfrontend.views.html.templates.ErrorTemplate

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ReportSuspiciousIdController @Inject()(
  authAction: AuthAction,
  sessionCache: TEASessionCache,
  multipleAccountsOrchestrator: MultipleAccountsOrchestrator,
  mcc: MessagesControllerComponents,
  reportSuspiciousId: ReportSuspiciousID,
  logger: EventLoggerService,
  errorView: ErrorTemplate
)(implicit ec: ExecutionContext, appConfig: AppConfig)
    extends FrontendController(mcc)
    with I18nSupport {

  implicit val baseLogger: Logger = Logger(this.getClass.getName)

  def view(): Action[AnyContent] = authAction.async { implicit request =>
    val mfaDetails = Seq(MFADetails("Text message", "07390328923"))
    val fixedAccountDetails = AccountDetails(
      "********3214",
      Some("email1@test.com"),
      "Yesterday",
      mfaDetails
    )

    Future.successful(Ok(reportSuspiciousId(fixedAccountDetails, true)))
  }

  def continue: Action[AnyContent] = authAction.async { implicit request =>
    sessionCache.save[Boolean](REPORTED_FRAUD, true)
    multipleAccountsOrchestrator
      .checkValidAccountTypeAndEnrolForPT(SA_ASSIGNED_TO_OTHER_USER)
      .value
      .map {
        case Right(_) =>
          logger.logEvent(
            logAssignedEnrolmentAfterReportingFraud(request.userDetails.credId)
          )
          Redirect(routes.EnrolledPTWithSAOnOtherAccountController.view)
        case Left(InvalidUserType(redirectUrl)) if redirectUrl.isDefined =>
          Redirect(routes.AccountCheckController.accountCheck(redirectUrl.get))
        case Left(UnexpectedResponseFromTaxEnrolments) =>
          Ok(
            errorView(
              "enrolmentError.title",
              "enrolmentError.heading",
              "enrolmentError.body"
            )
          )
        case Left(error) =>
          logger.logEvent(
            logUnexpectedErrorOccurred(
              request.userDetails.credId,
              "[ReportSuspiciousIdController][continue]",
              error
            )
          )
          InternalServerError
      }
  }
}
