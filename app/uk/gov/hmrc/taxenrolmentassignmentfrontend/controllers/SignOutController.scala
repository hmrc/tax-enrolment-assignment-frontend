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
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.actions.AuthJourney
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.helpers.TEAFrontendController
import uk.gov.hmrc.taxenrolmentassignmentfrontend.logging.EventLoggerService
import uk.gov.hmrc.taxenrolmentassignmentfrontend.logging.LoggingEvent.logUserSigninAgain
import uk.gov.hmrc.taxenrolmentassignmentfrontend.pages.RedirectUrlPage
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.JourneyCacheRepository

import javax.inject.{Inject, Singleton}
import scala.concurrent.Future

@Singleton
class SignOutController @Inject() (
  mcc: MessagesControllerComponents,
  appConfig: AppConfig,
  val logger: EventLoggerService,
  authJourney: AuthJourney,
  journeyCacheRepository: JourneyCacheRepository
) extends TEAFrontendController(mcc) {

  def signOut: Action[AnyContent] = authJourney.authWithDataRetrieval.async { implicit request =>
    val optRedirectUrl = request.userAnswers.get(RedirectUrlPage)
    journeyCacheRepository.clear(request.userAnswers.sessionId, request.userAnswers.nino)
    logger.logEvent(logUserSigninAgain(request.userDetails.credId))
    optRedirectUrl match {
      case Some(redirectUrl) =>
        Future.successful(
          Redirect(appConfig.signOutUrl, Map("continue" -> Seq(redirectUrl)))
            .removingFromSession("X-Request-ID", "Session-Id")
        )
      case None =>
        Future.successful(
          Redirect(appConfig.signOutUrl)
            .removingFromSession("X-Request-ID", "Session-Id")
        )
    }
  }
}
