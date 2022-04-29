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

import com.google.inject.Inject
import play.api.Logger
import play.api.mvc._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.http.HeaderCarrierConverter
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.ErrorHandler
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.UnexpectedErrorWhenGettingUserType
import uk.gov.hmrc.taxenrolmentassignmentfrontend.orchestrators.AccountCheckOrchestrator

import scala.concurrent.{ExecutionContext, Future}

case class AccountDetailsFromMongo(accountType: AccountTypes.Value)
case class RequestWithUserDetailsFromSessionAndMongo[A](request: Request[A],
                                                        userDetails: UserDetailsFromSession,
                                                        sessionID: String,
                                                        accountDetailsFromMongo: AccountDetailsFromMongo)
  extends WrappedRequest[A](request)

object RequestWithUserDetailsFromSessionAndMongo{
  import scala.language.implicitConversions
  implicit def requestConversion(
                                  requestWithUserDetailsFromSessionAndMongo: RequestWithUserDetailsFromSessionAndMongo[_])
  : RequestWithUserDetailsFromSession[_] = {
    RequestWithUserDetailsFromSession(
      requestWithUserDetailsFromSessionAndMongo.request,
      requestWithUserDetailsFromSessionAndMongo.userDetails,
      requestWithUserDetailsFromSessionAndMongo.sessionID)
  }
}

trait AccountMongoDetailsActionTrait
    extends ActionRefiner[RequestWithUserDetailsFromSession, RequestWithUserDetailsFromSessionAndMongo]


  class AccountMongoDetailsAction @Inject()(accountCheckOrchestrator: AccountCheckOrchestrator,
                                          val parser: BodyParsers.Default,
                                          errorHandler: ErrorHandler)(implicit val executionContext: ExecutionContext) extends AccountMongoDetailsActionTrait {
  implicit val baseLogger: Logger = Logger(this.getClass.getName)

  override protected def refine[A](request: RequestWithUserDetailsFromSession[A]): Future[Either[Result, RequestWithUserDetailsFromSessionAndMongo[A]]] = {
    implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequestAndSession(request, request.session)
    accountCheckOrchestrator.getAccountType(executionContext, hc, request).value
      .map {
        case Right(accountType) => Right(
          RequestWithUserDetailsFromSessionAndMongo(
            request.request, request.userDetails, request.sessionID, AccountDetailsFromMongo(accountType)
          )
        )
        case Left(_) =>
          Left(errorHandler
            .handleErrors(UnexpectedErrorWhenGettingUserType, "[AccountTypeAction][invokeBlock]")(request, baseLogger))
      }
  }
}