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
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.helpers.{ErrorHandler, TEAFrontendController}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.NoSAEnrolmentWhenOneExpected
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.JourneyCacheRepository

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class EnrolForSAController @Inject() (
  mcc: MessagesControllerComponents,
  appConfig: AppConfig,
  errorHandler: ErrorHandler,
  journeyCacheRepository: JourneyCacheRepository,
  authJourney: AuthJourney
)(implicit ec: ExecutionContext)
    extends TEAFrontendController(mcc) {

  def enrolForSA: Action[AnyContent] = authJourney.authWithDataRetrieval.async { implicit request =>
    if (request.userDetails.hasSAEnrolment) {
      journeyCacheRepository.clear(request.userAnswers.sessionId, request.userAnswers.nino).map { _ =>
        Redirect(appConfig.btaUrl)
      }
    } else {
      Future.successful(
        errorHandler
          .handleErrors(NoSAEnrolmentWhenOneExpected, "[EnrolForSAController][enrolForSA]")(request, implicitly)
      )
    }
  }

}
