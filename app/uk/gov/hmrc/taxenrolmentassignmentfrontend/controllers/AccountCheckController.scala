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
import play.api.Logging
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import uk.gov.hmrc.taxenrolmentassignmentfrontend.connectors.IVConnector
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.auth.AuthAction
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.UnexpectedResponseFromIV

import scala.concurrent.{ExecutionContext, Future}
@Singleton
class AccountCheckController @Inject()(
  authAction: AuthAction,
  ivConnector: IVConnector,
  mcc: MessagesControllerComponents
)(implicit ec: ExecutionContext)
    extends FrontendController(mcc)
    with Logging {

  def accountCheck(redirectUrl: String): Action[AnyContent] = authAction.async {
    implicit request =>
      if (request.userDetails.hasPTEnrolment) {
        Future.successful(Redirect(redirectUrl))
      } else {
        ivConnector.getCredentialsWithNino(request.userDetails.nino).value.map {
          case Right(credsWithNino) if credsWithNino.length == 1 =>
            Redirect(redirectUrl)
          case Right(_)                       => Ok("Multiple Accounts with Nino")
          case Left(UnexpectedResponseFromIV) => InternalServerError
        }
      }
  }

}
