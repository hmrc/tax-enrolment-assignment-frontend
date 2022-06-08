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
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.actions.AuthAction
import uk.gov.hmrc.taxenrolmentassignmentfrontend.logging.EventLoggerService
import uk.gov.hmrc.taxenrolmentassignmentfrontend.logging.LoggingEvent._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.formats.EnrolmentsFormats

import javax.inject.{Inject, Singleton}
import scala.concurrent.Future

@Singleton
class TestOnlyController @Inject()(mcc: MessagesControllerComponents,
                                   authAction: AuthAction,
                                   logger: EventLoggerService)
    extends FrontendController(mcc) {

  implicit val baseLogger: Logger = Logger(this.getClass.getName)

  def successfulCall: Action[AnyContent] = Action.async { implicit request =>
    logger.logEvent(logSuccessfulRedirectToReturnUrl)
    Future.successful(Ok("Successful"))
  }

  def usersGroupSearchCall(credId: String): Action[AnyContent] = Action.async {
    implicit request =>
      UsersGroupsFixedData.usersGroupSearchCreds.get(credId) match {
        case Some(userDetails) =>
          Future.successful(
            NonAuthoritativeInformation(
              UsersGroupsFixedData.toJson(userDetails)
            )
          )
        case None => Future.successful(NotFound)
      }
  }

  def enrolmentsFromAuth(): Action[AnyContent] = authAction {
    implicit request =>
      Ok(Json.toJson(request.userDetails.enrolments.enrolments)(EnrolmentsFormats.writes).toString())
  }

  def getSARedirectUrl(): Action[AnyContent] = Action.async{
    implicit request =>

  }
}
