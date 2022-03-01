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
import play.api.Logging
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.auth.AuthAction
import uk.gov.hmrc.taxenrolmentassignmentfrontend.forms.EnrolCurrentUserIdForm
import uk.gov.hmrc.taxenrolmentassignmentfrontend.views.html.EnrolCurrentUser

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class EnrolCurrentUserController @Inject()(
  authAction: AuthAction,
  mcc: MessagesControllerComponents,
  enrolCurrentUserView: EnrolCurrentUser
)(implicit ec: ExecutionContext)
    extends FrontendController(mcc)
    with I18nSupport {

  def view(): Action[AnyContent] = authAction.async { implicit request =>
    //ToDo use session cache to get current userId and the userId that contains SA if present on different account
    val fixedCurrentUserId = "*********9871"
    val fixedSAUserId = "*********9872"
    Future.successful(
      Ok(
        enrolCurrentUserView(
          EnrolCurrentUserIdForm.enrolCurrentUserIdForm,
          fixedCurrentUserId,
          Some(fixedSAUserId)
        )
      )
    )
  }
}
