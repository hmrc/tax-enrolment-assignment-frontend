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
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.actions.AuthAction
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.helpers.TEAFrontendController
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.TEASessionCache
import uk.gov.hmrc.taxenrolmentassignmentfrontend.views.html.TimedOutView

import javax.inject.{Inject, Singleton}
import scala.concurrent.Future

@Singleton
class TimeOutController @Inject() (
  authAction: AuthAction,
  mcc: MessagesControllerComponents,
  sessionCache: TEASessionCache,
  timedoutView: TimedOutView
) extends TEAFrontendController(mcc) {

  def keepAlive: Action[AnyContent] = authAction.async { implicit request =>
    sessionCache.extendSession()
    Future.successful(NoContent)
  }

  def timeout: Action[AnyContent] = Action { implicit request =>
    Ok(timedoutView())
  }

}
