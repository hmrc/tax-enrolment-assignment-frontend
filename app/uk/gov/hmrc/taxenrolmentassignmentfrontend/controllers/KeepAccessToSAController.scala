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

import cats.data.EitherT
import com.google.inject.{Inject, Singleton}
import play.api.Logger
import play.api.i18n.I18nSupport
import play.api.mvc._
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes.SA_ASSIGNED_TO_OTHER_USER
import uk.gov.hmrc.taxenrolmentassignmentfrontend.config.AppConfig
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.auth.AuthAction
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.TaxEnrolmentAssignmentErrors
import uk.gov.hmrc.taxenrolmentassignmentfrontend.forms.KeepAccessToSAThroughPTAForm
import uk.gov.hmrc.taxenrolmentassignmentfrontend.logging.EventLoggerService
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.forms.KeepAccessToSAThroughPTA
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.forms.KeepAccessToSAThroughPTA._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.orchestrators.MultipleAccountsOrchestrator
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.SessionKeys.KEEP_ACCESS_TO_SA_THROUGH_PTA_FORM
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.TEASessionCache
import uk.gov.hmrc.taxenrolmentassignmentfrontend.views.html.KeepAccessToSA
import uk.gov.hmrc.taxenrolmentassignmentfrontend.views.html.templates.ErrorTemplate

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class KeepAccessToSAController @Inject()(
  authAction: AuthAction,
  multipleAccountsOrchestrator: MultipleAccountsOrchestrator,
  mcc: MessagesControllerComponents,
  val logger: EventLoggerService,
  sessionCache: TEASessionCache,
  keepAccessToSA: KeepAccessToSA,
  val errorView: ErrorTemplate
)(implicit config: AppConfig, ec: ExecutionContext)
    extends FrontendController(mcc)
    with I18nSupport
    with ControllerHelper {

  implicit val baseLogger: Logger = Logger(this.getClass.getName)

  def view(): Action[AnyContent] =
    authAction.async { implicit request =>
      val res = for {
        _ <- multipleAccountsOrchestrator
          .checkValidAccountTypeRedirectUrlInCache(
            List(SA_ASSIGNED_TO_OTHER_USER)
          )
        optFormData <- EitherT.right[TaxEnrolmentAssignmentErrors](
          sessionCache.getEntry[KeepAccessToSAThroughPTA](
            KEEP_ACCESS_TO_SA_THROUGH_PTA_FORM
          )
        )
      } yield
        optFormData match {
          case Some(data) =>
            KeepAccessToSAThroughPTAForm.keepAccessToSAThroughPTAForm
              .fill(data)
          case None => KeepAccessToSAThroughPTAForm.keepAccessToSAThroughPTAForm
        }

      res.value.map {
        case Right(form) => Ok(keepAccessToSA(form))
        case Left(error) =>
          handleErrors(error, "[KeepAccessToSAController][view]")
      }
    }

  def continue: Action[AnyContent] = authAction.async {
    implicit requestWithUserDetails =>
      KeepAccessToSAThroughPTAForm.keepAccessToSAThroughPTAForm.bindFromRequest
        .fold(
          formWithErrors => {
            Future.successful(BadRequest(keepAccessToSA(formWithErrors)))
          },
          keepAccessToSA => {
            val res = if (keepAccessToSA.keepAccessToSAThroughPTA) {
              multipleAccountsOrchestrator
                .checkValidAccountTypeRedirectUrlInCache(
                  List(SA_ASSIGNED_TO_OTHER_USER)
                )
                .map(_ => routes.SignInAgainController.view)
            } else {
              multipleAccountsOrchestrator
                .checkValidAccountTypeAndEnrolForPT(SA_ASSIGNED_TO_OTHER_USER)
                .map(_ => routes.EnrolledPTWithSAOnOtherAccountController.view)
            }

            res.value.flatMap {
              case Right(call) =>
                sessionCache
                  .save[KeepAccessToSAThroughPTA](
                    KEEP_ACCESS_TO_SA_THROUGH_PTA_FORM,
                    keepAccessToSA
                  )
                  .map(_ => Redirect(call))
              case Left(error) =>
                Future.successful(
                  handleErrors(error, "[KeepAccessToSAController][continue]")
                )
            }
          }
        )
  }
}
