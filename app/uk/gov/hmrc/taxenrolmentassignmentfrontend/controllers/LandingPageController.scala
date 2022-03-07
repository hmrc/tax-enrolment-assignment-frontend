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

import play.api.Logging
import play.api.http.ContentTypeOf.contentTypeOf_Html
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Result}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import uk.gov.hmrc.taxenrolmentassignmentfrontend.config.AppConfig
import uk.gov.hmrc.taxenrolmentassignmentfrontend.connectors.{IVConnector, TaxEnrolmentsConnector}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.auth.{AuthAction, RequestWithUserDetails}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.UnexpectedResponseFromIV
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.TEASessionCache
import uk.gov.hmrc.taxenrolmentassignmentfrontend.views.html.{ErrorTemplate, LandingPage}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class LandingPageController @Inject()(
                                        authAction: AuthAction,
                                        appConfig: AppConfig,
                                        ivConnector: IVConnector,
                                        taxEnrolmentsConnector: TaxEnrolmentsConnector,
                                        mcc: MessagesControllerComponents,
                                        sessionCache: TEASessionCache,
                                        landingPageView: LandingPage,
                                        errorView: ErrorTemplate
                                      )(implicit ec: ExecutionContext)
  extends FrontendController(mcc) with Logging with I18nSupport {

  def showLandingPage(redirectUrl: String): Action[AnyContent] = authAction.async {
    implicit request =>
      sessionCache.save[String]("redirectURL", redirectUrl)
      if (request.userDetails.hasPTEnrolment) {
        Future.successful(Redirect(redirectUrl))
      } else {
        ivConnector.getCredentialsWithNino(request.userDetails.nino).value.flatMap {
          case Right(credsWithNino) if credsWithNino.length == 1 =>
            enrolUser()
          case Right(_)                       =>
            Future.successful(Ok(landingPageView()))
          case Left(UnexpectedResponseFromIV) => Future.successful(InternalServerError("error"))
        }
      }
  }

  def enrolUser()(implicit request: RequestWithUserDetails[AnyContent],
                hc: HeaderCarrier): Future[Result] = {
    val details =  request.userDetails
    taxEnrolmentsConnector.assignPTEnrolment(details.groupId, details.credId, details.nino).isRight map {
      case true => Redirect(appConfig.redirectPTAUrl)
      case false => Ok(errorView("enrolmentError.title", "enrolmentError.heading", "enrolmentError.body"))
    }
  }

}