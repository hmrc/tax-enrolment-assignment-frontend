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

package uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.testOnly

import play.api.Logger
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import uk.gov.hmrc.taxenrolmentassignmentfrontend.logging.EventLoggerService
import uk.gov.hmrc.taxenrolmentassignmentfrontend.logging.LoggingEvent._

import javax.inject.{Inject, Singleton}
import scala.concurrent.Future

@Singleton
class TestOnlyController @Inject()(mcc: MessagesControllerComponents,
                                   logger: EventLoggerService)
    extends FrontendController(mcc) {

  implicit val baseLogger: Logger = Logger(this.getClass.getName)

  def successfulCall: Action[AnyContent] = Action.async { implicit request =>
    logger.logEvent(logSuccessfulRedirectToReturnUrl)
    Future.successful(Ok("Successful"))
  }

  def usersGroupSearchCall(credId: String): Action[AnyContent] = Action.async {
    implicit request =>
      println("Here")
      UsersGroupsFixedData.usersGroupSearchCreds.get(credId) match {
        case Some(userDetails) =>
          println("found")
          Future.successful(Ok(UsersGroupsFixedData.toJson(userDetails)))
        case None =>
          println("not found")
          Future.successful(NotFound)
      }
  }
}
