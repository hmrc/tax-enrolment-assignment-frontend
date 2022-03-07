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
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.auth.AuthAction
import uk.gov.hmrc.taxenrolmentassignmentfrontend.forms.{
  AccountsBelongToUserForm,
  EnrolCurrentUserIdForm
}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.AccountDetails
import uk.gov.hmrc.taxenrolmentassignmentfrontend.views.html.AssociatedAccounts

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AssociatedAccountsController @Inject()(
  authAction: AuthAction,
  mcc: MessagesControllerComponents,
  associatedAccountsView: AssociatedAccounts
)(implicit ec: ExecutionContext)
    extends FrontendController(mcc)
    with I18nSupport {

  def view(): Action[AnyContent] = authAction.async { implicit request =>
    //ToDo use session cache to get users accounts
    val fixedCurrentUserAccount = AccountDetails(
      "*********9871",
      "olivia.cunningham32@gmail.com",
      "12 December 2021"
    )
    val fixedOtherAccount1 = AccountDetails(
      "*********1234",
      "olivia.cunningham32@gmail.com",
      "21 October 2021"
    )

    val fixedOtherAccount2 = AccountDetails(
      "*********9872",
      "olivia.cunningham@zetec.com",
      "19 October 2021"
    )

    Future.successful(
      Ok(
        associatedAccountsView(
          AccountsBelongToUserForm.accountsBelongToUserForm,
          fixedCurrentUserAccount,
          List(fixedOtherAccount1, fixedOtherAccount2)
        )
      )
    )
  }
}
