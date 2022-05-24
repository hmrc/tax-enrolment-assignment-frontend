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
import play.api.mvc._
import uk.gov.hmrc.play.bootstrap.controller.WithDefaultFormBinding
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import uk.gov.hmrc.taxenrolmentassignmentfrontend.config.AppConfig
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.actions.{AccountMongoDetailsAction, AuthAction, ThrottleAction}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.forms.KeepAccessToSAThroughPTAForm
import uk.gov.hmrc.taxenrolmentassignmentfrontend.logging.EventLoggerService
import uk.gov.hmrc.taxenrolmentassignmentfrontend.orchestrators.MultipleAccountsOrchestrator
import uk.gov.hmrc.taxenrolmentassignmentfrontend.reporting.{AuditEvent, AuditHandler}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.views.html.KeepAccessToSA

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class KeepAccessToSAController @Inject()(
                                          authAction: AuthAction,
                                          accountMongoDetailsAction: AccountMongoDetailsAction,
                                          throttleAction: ThrottleAction,
                                          multipleAccountsOrchestrator: MultipleAccountsOrchestrator,
                                          mcc: MessagesControllerComponents,
                                          val logger: EventLoggerService,
                                          keepAccessToSA: KeepAccessToSA,
                                          auditHandler: AuditHandler,
                                          errorHandler: ErrorHandler
)(implicit config: AppConfig, ec: ExecutionContext)
    extends FrontendController(mcc)
    with I18nSupport with WithDefaultFormBinding {

  implicit val baseLogger: Logger = Logger(this.getClass.getName)

  def view(): Action[AnyContent] =
    authAction.andThen(accountMongoDetailsAction).andThen(throttleAction).async { implicit request =>
      multipleAccountsOrchestrator.getDetailsForKeepAccessToSA.value.map {
        case Right(form) => Ok(keepAccessToSA(form))
        case Left(error) =>
          errorHandler.handleErrors(error, "[KeepAccessToSAController][view]")(request, implicitly)
      }
    }

  def continue: Action[AnyContent] = authAction.andThen(accountMongoDetailsAction).andThen(throttleAction).async {
    implicit request =>
      KeepAccessToSAThroughPTAForm.keepAccessToSAThroughPTAForm.bindFromRequest
        .fold(
          formWithErrors => {
            Future.successful(BadRequest(keepAccessToSA(formWithErrors)))
          },
          keepAccessToSA => {

            multipleAccountsOrchestrator
              .handleKeepAccessToSAChoice(keepAccessToSA)
              .value
              .map {
                case Right(true) =>
                  Redirect(routes.SignInWithSAAccountController.view)
                case Right(false) =>
                  auditHandler.audit(AuditEvent.auditSuccessfullyEnrolledPTWhenSAOnOtherAccount(false))
                  Redirect(routes.EnrolledPTWithSAOnOtherAccountController.view)
                case Left(error) =>
                  errorHandler.handleErrors(error, "[KeepAccessToSAController][continue]")(request, implicitly)
              }
          }
        )
  }
}
