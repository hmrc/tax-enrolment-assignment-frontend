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
import uk.gov.hmrc.taxenrolmentassignmentfrontend.config.AppConfig
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.auth.AuthAction
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.InvalidUserType
import uk.gov.hmrc.taxenrolmentassignmentfrontend.logging.EventLoggerService
import uk.gov.hmrc.taxenrolmentassignmentfrontend.orchestrators.MultipleAccountsOrchestrator
import uk.gov.hmrc.taxenrolmentassignmentfrontend.views.html.PTEnrolmentOnAnotherAccount
import uk.gov.hmrc.taxenrolmentassignmentfrontend.views.html.templates.ErrorTemplate

import scala.concurrent.ExecutionContext

@Singleton
class PTEnrolmentOnOtherAccountController @Inject()(
  authAction: AuthAction,
  mcc: MessagesControllerComponents,
  multipleAccountsOrchestrator: MultipleAccountsOrchestrator,
  ptEnrolmentOnAnotherAccountView: PTEnrolmentOnAnotherAccount,
  val logger: EventLoggerService,
  val errorView: ErrorTemplate
)(implicit ec: ExecutionContext, appConfig: AppConfig)
    extends FrontendController(mcc)
    with I18nSupport
    with ControllerHelper {

  implicit val baseLogger: Logger = Logger(this.getClass.getName)

  def view(): Action[AnyContent] = authAction.async { implicit request =>

    val res = multipleAccountsOrchestrator.getSAForPTAlreadyEnrolledDetails

    res.value.map {
      case Right(accountDetails) =>
        Ok(
          ptEnrolmentOnAnotherAccountView(
            request.userDetails.credId,
            accountDetails._1,
            accountDetails._2,
            request.userDetails.hasSAEnrolment,
            accountDetails._2.get.hasSA.get,
            accountDetails._1.hasSA.get
          )
        )
      case Left(InvalidUserType(redirectUrl)) if redirectUrl.isDefined =>
        Redirect(routes.AccountCheckController.accountCheck(redirectUrl.get))
      case Left(error) =>
        handleErrors(error, "[PTEnrolmentOnOtherAccountController][view]")
    }
  }
}
