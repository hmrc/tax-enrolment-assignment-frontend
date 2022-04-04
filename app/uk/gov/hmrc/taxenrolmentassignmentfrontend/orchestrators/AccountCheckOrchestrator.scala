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

package uk.gov.hmrc.taxenrolmentassignmentfrontend.orchestrators

import cats.data.EitherT
import cats.implicits._
import com.google.inject.{Inject, Singleton}
import play.api.Logger
import play.api.mvc.AnyContent
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.service.TEAFResult
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes.{
  MULTIPLE_ACCOUNTS,
  PT_ASSIGNED_TO_CURRENT_USER,
  PT_ASSIGNED_TO_OTHER_USER,
  SINGLE_ACCOUNT
}
import uk.gov.hmrc.taxenrolmentassignmentfrontend._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.auth.RequestWithUserDetails
import uk.gov.hmrc.taxenrolmentassignmentfrontend.logging.EventLoggerService
import uk.gov.hmrc.taxenrolmentassignmentfrontend.logging.LoggingEvent.{
  logAnotherAccountAlreadyHasPTEnrolment,
  logCurrentUserAlreadyHasPTEnrolment,
  logCurrentUserhasMultipleAccounts,
  logCurrentUserhasOneAccount
}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.SessionKeys.ACCOUNT_TYPE
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.TEASessionCache
import uk.gov.hmrc.taxenrolmentassignmentfrontend.services.{
  EACDService,
  SilentAssignmentService
}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AccountCheckOrchestrator @Inject()(
  eacdService: EACDService,
  silentAssignmentService: SilentAssignmentService,
  logger: EventLoggerService,
  sessionCache: TEASessionCache
) {

  implicit val baseLogger: Logger = Logger(this.getClass.getName)

  def getAccountType(
    implicit ec: ExecutionContext,
    hc: HeaderCarrier,
    requestWithUserDetails: RequestWithUserDetails[AnyContent]
  ): TEAFResult[AccountTypes.Value] = EitherT {
    sessionCache.getEntry[AccountTypes.Value](ACCOUNT_TYPE).flatMap {
      case Some(accountType) => Future.successful(Right(accountType))
      case None =>
        generateAccountType.map { accountType =>
          sessionCache.save[AccountTypes.Value](ACCOUNT_TYPE, accountType)
          accountType
        }.value
    }
  }

  private def generateAccountType(
    implicit ec: ExecutionContext,
    hc: HeaderCarrier,
    requestWithUserDetails: RequestWithUserDetails[AnyContent]
  ): TEAFResult[AccountTypes.Value] = EitherT {
    checkUsersWithPTEnrolmentAlreadyAssigned.value
      .flatMap {
        case Right(Some(accountTypes)) => Future.successful(Right(accountTypes))
        case Right(None)               => checkIfSingleOrMultipleAccounts.value
        case Left(error)               => Future.successful(Left(error))
      }
  }

  private def checkUsersWithPTEnrolmentAlreadyAssigned(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext,
    requestWithUserDetails: RequestWithUserDetails[AnyContent]
  ): TEAFResult[Option[AccountTypes.Value]] = {
    if (requestWithUserDetails.userDetails.hasPTEnrolment) {
      logger.logEvent(
        logCurrentUserAlreadyHasPTEnrolment(
          requestWithUserDetails.userDetails.credId
        )
      )
      EitherT.right(Future.successful(Some(PT_ASSIGNED_TO_CURRENT_USER)))
    } else {
      eacdService.getUsersAssignedPTEnrolment
        .map(
          usersAssignedEnrolment =>
            usersAssignedEnrolment.enrolledCredential.map { credId =>
              if (credId == requestWithUserDetails.userDetails.credId) {
                logger.logEvent(
                  logCurrentUserAlreadyHasPTEnrolment(
                    requestWithUserDetails.userDetails.credId
                  )
                )
                PT_ASSIGNED_TO_CURRENT_USER
              } else {
                logger.logEvent(
                  logAnotherAccountAlreadyHasPTEnrolment(
                    requestWithUserDetails.userDetails.credId,
                    credId
                  )
                )
                PT_ASSIGNED_TO_OTHER_USER
              }
          }
        )
    }
  }

  private def checkIfSingleOrMultipleAccounts(
    implicit requestWithUserDetails: RequestWithUserDetails[AnyContent],
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): TEAFResult[AccountTypes.Value] = {
    silentAssignmentService.getOtherAccountsWithPTAAccess.map(
      otherCreds =>
        if (otherCreds.isEmpty) {
          logger.logEvent(
            logCurrentUserhasOneAccount(
              requestWithUserDetails.userDetails.credId
            )
          )
          SINGLE_ACCOUNT
        } else {
          logger.logEvent(
            logCurrentUserhasMultipleAccounts(
              requestWithUserDetails.userDetails.credId
            )
          )
          MULTIPLE_ACCOUNTS
      }
    )
  }

}