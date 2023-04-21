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
import uk.gov.hmrc.taxenrolmentassignmentfrontend.config.AppConfig
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.actions.AuthAction
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.helpers.TEAFrontendController
import uk.gov.hmrc.taxenrolmentassignmentfrontend.logging.EventLoggerService
import uk.gov.hmrc.taxenrolmentassignmentfrontend.logging.LoggingEvent.logUserSigninAgain
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.SessionKeys.REDIRECT_URL
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.TEASessionCache

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class SignOutController @Inject()(
  authAction: AuthAction,
  mcc: MessagesControllerComponents,
  appConfig: AppConfig,
  sessionCache: TEASessionCache,
  val logger: EventLoggerService
)(implicit ec: ExecutionContext)
    extends TEAFrontendController(mcc) {

  def signOut(): Action[AnyContent] = authAction.async { implicit request =>
    sessionCache.fetch().map{cacheData =>
      val optRedirectUrl = cacheData.fold[Option[String]](None)(_.data.get(
        REDIRECT_URL
      ).map(_.as[String]))
      sessionCache.removeRecord
      logger.logEvent(logUserSigninAgain(request.userDetails.credId))
      optRedirectUrl match {
        case Some(redirectUrl) => Redirect(appConfig.signOutUrl, Map("continueUrl"-> Seq(redirectUrl)))
          .removingFromSession("X-Request-ID", "Session-Id")
        case None => Redirect(appConfig.signOutUrl)
          .removingFromSession("X-Request-ID", "Session-Id")
      }
    }
  }
}
