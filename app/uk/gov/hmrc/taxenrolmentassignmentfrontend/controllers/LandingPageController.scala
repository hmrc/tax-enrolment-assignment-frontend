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

import javax.inject.{Inject, Singleton}
import play.api.Logger
import play.api.http.ContentTypeOf.contentTypeOf_Html
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.auth.AuthAction
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.InvalidUserType
import uk.gov.hmrc.taxenrolmentassignmentfrontend.logging.EventLoggerService
import uk.gov.hmrc.taxenrolmentassignmentfrontend.orchestrators.MultipleAccountsOrchestrator
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.SessionKeys.REDIRECT_URL
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.TEASessionCache
import uk.gov.hmrc.taxenrolmentassignmentfrontend.views.html.LandingPage

import scala.concurrent.ExecutionContext

@Singleton
class LandingPageController @Inject()(
  authAction: AuthAction,
  mcc: MessagesControllerComponents,
  multipleAccountsOrchestrator: MultipleAccountsOrchestrator,
  sessionCache: TEASessionCache,
  logger: EventLoggerService,
  landingPageView: LandingPage
)(implicit ec: ExecutionContext)
    extends FrontendController(mcc)
    with I18nSupport {

  implicit val baseLogger: Logger = Logger(this.getClass.getName)

  def view: Action[AnyContent] = authAction.async { implicit request =>
    multipleAccountsOrchestrator.getDetailsForLandingPage.value.map {
      case Right(accountDetails) =>
        Ok(
          landingPageView(
            accountDetails.userId,
            accountDetails.hasSA.getOrElse(false)
          )
        )
      case Left(InvalidUserType(redirectUrl)) if redirectUrl.isDefined =>
        Redirect(routes.AccountCheckController.accountCheck(redirectUrl.get))
      case Left(_) => InternalServerError
    }
  }

  def continue: Action[AnyContent] = authAction.async { implicit request =>
    sessionCache.getEntry[String](REDIRECT_URL).map {
      case Some(redirectUrl) =>
        Redirect(redirectUrl)
      case None => InternalServerError
    }
  }
}
