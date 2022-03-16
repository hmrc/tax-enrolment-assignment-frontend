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
import play.api.http.ContentTypeOf.contentTypeOf_Html
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes.MULTIPLE_ACCOUNTS
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.auth.AuthAction
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.TEASessionCache
import uk.gov.hmrc.taxenrolmentassignmentfrontend.views.html.LandingPage

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class LandingPageController @Inject()(
  authAction: AuthAction,
  mcc: MessagesControllerComponents,
  sessionCache: TEASessionCache,
  landingPageView: LandingPage
)(implicit ec: ExecutionContext)
    extends FrontendController(mcc)
    with Logging
    with I18nSupport {

  def view: Action[AnyContent] = authAction.async { implicit request =>
    sessionCache.getEntry[AccountTypes.Value]("ACCOUNT_TYPE").flatMap {
      case Some(MULTIPLE_ACCOUNTS) => Future.successful(Ok(landingPageView()))
      case _ =>
        sessionCache.getEntry[String]("redirectURL").map {
          case Some(redirectUrl) =>
            Redirect(routes.AccountCheckController.accountCheck(redirectUrl))
          case None => InternalServerError
        }
    }
//      val testing =
//      for {
//        usersWithPT <- checkUsersWithPTEnrolmentAlreadyAssigned(request.userDetails)
//        creds <- checkIfUserHasOtherAccounts(request.userDetails)
//        validPtaAccounts<-  EitherT.right[TaxEnrolmentAssignmentErrors](silentAssignmentService.getValidPtaAccounts(creds))
//      } yield (creds, usersWithPT, validPtaAccounts)
//
//      testing.value.flatMap {
//        case Right((creds, None, validPtaAccounts)) if creds.length <= 1 && validPtaAccounts.flatten.size == 1 =>
//              silentEnrol()
//        case Right((_, Some(usersWithPT),_)) if usersWithPT == request.userDetails.credId =>
//          Future.successful(Redirect(redirectUrl))
//        case Right((_, Some(_),_)) =>   Future.successful(Ok(underConstructionView()))
//        case Right((_, None,_)) =>  Future.successful(Ok(landingPageView()))
//        case Left(error) => Future.successful(InternalServerError)
//      }
  }

//  private def checkUsersWithPTEnrolmentAlreadyAssigned(sessionUserDetails:UserDetailsFromSession)(implicit hc : HeaderCarrier): TEAFResult[Option[String]] = {
//    if(sessionUserDetails.hasPTEnrolment) {
//      EitherT.right(Future.successful(Some(sessionUserDetails.credId)))
//    } else {
//      eacdConnector
//        .getUsersWithPTEnrolment(sessionUserDetails.nino)
//        .map(optUsers => optUsers.fold[Option[String]](None)(users => users.principalUserIds.headOption))
//    }
//  }
//
//  private def checkIfUserHasOtherAccounts(sessionUserDetails:UserDetailsFromSession)(implicit hc : HeaderCarrier)
//                                         : TEAFResult[List[IVNinoStoreEntry]]  = {
//      ivConnector.getCredentialsWithNino(sessionUserDetails.nino)
//
//  }
//
//
//  private def silentEnrol()(implicit request: RequestWithUserDetails[AnyContent],
//                hc: HeaderCarrier): Future[Result] = {
//    silentAssignmentService.enrolUser().isRight map {
//      case true => Redirect(appConfig.redirectPTAUrl)
//      case false => Ok(errorView("enrolmentError.title", "enrolmentError.heading", "enrolmentError.body"))
//    }
//  }

}
