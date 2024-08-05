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
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.actions.AuthJourney
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.helpers.TEAFrontendController
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.JourneyCacheRepository
import uk.gov.hmrc.taxenrolmentassignmentfrontend.views.html.TimedOutView

import javax.inject.{Inject, Singleton}
import scala.concurrent.Future

@Singleton
class TimeOutController @Inject() (
  mcc: MessagesControllerComponents,
  timedOutView: TimedOutView,
  journeyCacheRepository: JourneyCacheRepository,
  authJourney: AuthJourney
) extends TEAFrontendController(mcc) {

  def keepAlive: Action[AnyContent] = authJourney.authWithDataRetrieval.async { implicit request =>
    journeyCacheRepository.keepAlive(request.userAnswers.sessionId, request.userAnswers.nino)
    Future.successful(NoContent)
  }

  def timeout: Action[AnyContent] = Action { implicit request =>
    Ok(timedOutView())
  }
}
