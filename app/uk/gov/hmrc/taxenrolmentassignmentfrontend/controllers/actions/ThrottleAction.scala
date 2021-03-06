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

package uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.actions

import play.api.Logger
import play.api.mvc.Results.Redirect
import play.api.mvc._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.http.HeaderCarrierConverter
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.helpers.ErrorHandler
import uk.gov.hmrc.taxenrolmentassignmentfrontend.logging.{EventLoggerService, LoggingEvent}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.services.{ThrottleApplied, ThrottleDoesNotApply, ThrottlingService}

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

trait ThrottleActionTrait extends ActionFilter[RequestWithUserDetailsFromSessionAndMongo]

class ThrottleAction @Inject()(throttlingService: ThrottlingService,
                               val parser: BodyParsers.Default,
                               errorHandler: ErrorHandler,
                               val logger: EventLoggerService)(implicit val executionContext: ExecutionContext) extends ThrottleActionTrait {
  implicit val baseLogger: Logger = Logger(this.getClass.getName)

  def throttle(accountType: AccountTypes.Value, redirectUrl : String)(
    implicit ec: ExecutionContext, hc: HeaderCarrier, requestWithUserDetailsFromSession: RequestWithUserDetailsFromSession[_]): Future[Option[Result]] = {

    throttlingService.throttle(
      accountType,
      requestWithUserDetailsFromSession.userDetails.nino,
      requestWithUserDetailsFromSession.userDetails.enrolments.enrolments
    )(ec, hc).value.map {
      case Right(ThrottleApplied) =>
        logger.logEvent(LoggingEvent.logUserThrottled(requestWithUserDetailsFromSession.userDetails.credId, accountType, requestWithUserDetailsFromSession.userDetails.nino))
        Some(Redirect(redirectUrl))
      case Right(ThrottleDoesNotApply) => None
      case Left(error) => Some(errorHandler
        .handleErrors(error, "[ThrottleAction][filter]")(requestWithUserDetailsFromSession, baseLogger))
    }(ec)
  }

  override protected def filter[A](request: RequestWithUserDetailsFromSessionAndMongo[A]): Future[Option[Result]] = {
    implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequestAndSession(request, request.session)
    throttle(request.accountDetailsFromMongo.accountType, request.accountDetailsFromMongo.redirectUrl)(implicitly, implicitly, request)

  }
}
