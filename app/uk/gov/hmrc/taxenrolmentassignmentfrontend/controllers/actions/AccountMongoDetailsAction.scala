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
import play.api.mvc._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.config.AppConfig
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.helpers.ErrorHandler
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.logging.{EventLoggerService, LoggingEvent}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.pages.{AccountTypePage, KeepAccessToSAThroughPTAPage, RedirectUrlPage, ReportedFraudPage, UserAssignedPtaEnrolmentPage, UserAssignedSaEnrolmentPage}

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

trait AccountMongoDetailsActionTrait extends ActionRefiner[DataRequest, DataRequest]

class AccountMongoDetailsAction @Inject() (
  errorHandler: ErrorHandler,
  val appConfig: AppConfig,
  logger: EventLoggerService
)(implicit val executionContext: ExecutionContext)
    extends AccountMongoDetailsActionTrait with RedirectHelper {
  implicit val baseLogger: Logger = Logger(this.getClass.getName)
  override protected def refine[A](
    request: DataRequest[A]
  ): Future[Either[Result, DataRequest[A]]] =
    getAccountDetailsFromMongoFromCache(request)
      .map {
        case Right(accountDetailsFromMongo) =>
          Right(
            DataRequest(
              request.request,
              request.userDetails,
              request.userAnswers,
              Some(
                RequestWithUserDetailsFromSessionAndMongo(
                  request.request,
                  request.userDetails,
                  request.userAnswers.sessionId,
                  accountDetailsFromMongo
                )
              )
            )
          )
        case Left(CacheNotCompleteOrNotCorrect(None, None)) =>
          logger.logEvent(
            LoggingEvent
              .logUserHasNoCacheInMongo(request.userDetails.credId, request.userAnswers.sessionId)
          )
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

  private def getAccountDetailsFromMongoFromCache(implicit
    request: DataRequest[_]
  ): Future[Either[TaxEnrolmentAssignmentErrors, AccountDetailsFromMongo]] = {

    val optKeepAccessToSAThroughPTA = request.userAnswers.get(KeepAccessToSAThroughPTAPage)
    val optReportedFraud = request.userAnswers.get(ReportedFraudPage)
    val optUserAssignedSA = request.userAnswers.get(UserAssignedSaEnrolmentPage)
    val optUserAssignedPT = request.userAnswers.get(UserAssignedPtaEnrolmentPage)

    val optAccountType = request.userAnswers.get(AccountTypePage)
    val optRedirectUrl = request.userAnswers.get(RedirectUrlPage)
    if (optAccountType.isEmpty && optRedirectUrl.isDefined) {
      Future.successful(
        Left(
          CacheNotCompleteOrNotCorrect(optRedirectUrl, None)
        )
      )
    } else if (optAccountType.isEmpty && optRedirectUrl.isEmpty) {
      Future.successful(Left(CacheNotCompleteOrNotCorrect(None, None)))
    } else {
      (AccountDetailsFromMongo.optAccountType(optAccountType.get), optRedirectUrl) match {
        case (Some(accountType), Some(redirectUrl)) =>
          Future.successful(
            Right(
              AccountDetailsFromMongo(
                accountType,
                redirectUrl,
                optKeepAccessToSAThroughPTA,
                optReportedFraud,
                optUserAssignedSA,
                optUserAssignedPT
              )
            )
          )
        case (optAccountType, optRedirectUrl) =>
          Future.successful(
            Left(
              CacheNotCompleteOrNotCorrect(optRedirectUrl, optAccountType)
            )
          )
      }
    }
  }

}
