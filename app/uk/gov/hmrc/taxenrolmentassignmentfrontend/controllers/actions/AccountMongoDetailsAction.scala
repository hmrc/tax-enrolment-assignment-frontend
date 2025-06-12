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

package uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.actions

import play.api.Logger
import play.api.mvc.*
import uk.gov.hmrc.taxenrolmentassignmentfrontend.config.AppConfig
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.helpers.ErrorHandler
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.*
import uk.gov.hmrc.taxenrolmentassignmentfrontend.logging.{EventLoggerService, LoggingEvent}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.services.AccountMongoDetailsRetrievalService

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

case class RequestWithUserDetailsFromSessionAndMongo[A](
  request: Request[A],
  userDetails: UserDetailsFromSession,
  sessionID: String,
  accountDetailsFromMongo: AccountDetailsFromMongo
) extends WrappedRequest[A](request)

object RequestWithUserDetailsFromSessionAndMongo {
  import scala.language.implicitConversions
  implicit def requestConversion(
    requestWithUserDetailsFromSessionAndMongo: RequestWithUserDetailsFromSessionAndMongo[_]
  ): RequestWithUserDetailsFromSession[_] =
    RequestWithUserDetailsFromSession(
      requestWithUserDetailsFromSessionAndMongo.request,
      requestWithUserDetailsFromSessionAndMongo.userDetails,
      requestWithUserDetailsFromSessionAndMongo.sessionID
    )
}

trait AccountMongoDetailsActionTrait
    extends ActionRefiner[RequestWithUserDetailsFromSession, RequestWithUserDetailsFromSessionAndMongo]

// TODO: Spec file - mock AccountMongoDetailsRetrievalService
class AccountMongoDetailsAction @Inject() (
  errorHandler: ErrorHandler,
  val appConfig: AppConfig,
  logger: EventLoggerService,
  accountMongoDetailsRetrievalService: AccountMongoDetailsRetrievalService
)(implicit val executionContext: ExecutionContext)
    extends AccountMongoDetailsActionTrait with RedirectHelper {
  implicit val baseLogger: Logger = Logger(this.getClass.getName)
  override protected def refine[A](
    request: RequestWithUserDetailsFromSession[A]
  ): Future[Either[Result, RequestWithUserDetailsFromSessionAndMongo[A]]] =
    accountMongoDetailsRetrievalService
      .getAccountDetailsFromMongoFromCache(request)
      .map {
        case Right(accountDetailsFromMongo) =>
          Right(
            RequestWithUserDetailsFromSessionAndMongo(
              request.request,
              request.userDetails,
              request.sessionID,
              accountDetailsFromMongo
            )
          )
        case Left(CacheNotCompleteOrNotCorrect(None, None)) =>
          logger.logEvent(LoggingEvent.logUserHasNoCacheInMongo(request.userDetails.credId, request.sessionID))
          Left(toGGLogin)
        case Left(error) =>
          Left(
            errorHandler
              .handleErrors(error, "[AccountTypeAction][invokeBlock]")(request, baseLogger)
          )
      }
      .recover { case _ =>
        Left(
          errorHandler
            .handleErrors(UnexpectedError, "[AccountTypeAction][invokeBlock]")(
              request,
              baseLogger
            )
        )
      }

}
