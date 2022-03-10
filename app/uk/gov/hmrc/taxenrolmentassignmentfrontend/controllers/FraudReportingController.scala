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
import play.api.mvc._
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import uk.gov.hmrc.taxenrolmentassignmentfrontend.config.AppConfig
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.auth.AuthAction
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.MFADetails
import uk.gov.hmrc.taxenrolmentassignmentfrontend.views.html.FraudReporting

import scala.concurrent.Future

@Singleton
class FraudReportingController @Inject()(
  authAction: AuthAction,
  mcc: MessagesControllerComponents,
  fraudReportingView: FraudReporting
)(implicit config: AppConfig)
    extends FrontendController(mcc)
    with I18nSupport {

  def selectIdsToReport(): Action[AnyContent] =
    authAction.async { implicit request =>
      Future.successful(Ok(fraudReportingView()))
    }
}
