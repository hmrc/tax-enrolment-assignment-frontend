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

package uk.gov.hmrc.taxenrolmentassignmentfrontend.orchestrators

import cats.data.{EitherT, OptionT}
import cats.implicits._
import play.api.Logger
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes.{PT_ASSIGNED_TO_CURRENT_USER, PT_ASSIGNED_TO_OTHER_USER, SA_ASSIGNED_TO_CURRENT_USER, SA_ASSIGNED_TO_OTHER_USER, SINGLE_OR_MULTIPLE_ACCOUNTS}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.actions.{AccountDetailsFromMongo, RequestWithUserDetailsFromSession}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.TaxEnrolmentAssignmentErrors
import uk.gov.hmrc.taxenrolmentassignmentfrontend.logging.EventLoggerService
import uk.gov.hmrc.taxenrolmentassignmentfrontend.logging.LoggingEvent.{logAnotherAccountAlreadyHasPTEnrolment, logAnotherAccountHasSAEnrolment, logCurrentAndAnotherAccountHasSAEnrolment, logCurrentUserAlreadyHasPTEnrolment, logCurrentUserHasSAEnrolment, logCurrentUserhasOneOrMultipleAccounts}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.{CacheMap, UsersAssignedEnrolment}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.SessionKeys._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.TEASessionCache
import uk.gov.hmrc.taxenrolmentassignmentfrontend.services.EACDService
import uk.gov.hmrc.taxenrolmentassignmentfrontend.utils.HmrcPTEnrolment

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AccountCheckOrchestrator @Inject() (
  eacdService: EACDService,
  logger: EventLoggerService,
  sessionCache: TEASessionCache,
  hmrcPTEnrolment: HmrcPTEnrolment
) {

  implicit val baseLogger: Logger = Logger(this.getClass.getName)

  private def findAndFixHmrcPtEnrolmentOnOtherAccount(implicit
    ec: ExecutionContext,
    hc: HeaderCarrier,
    requestWithUserDetails: RequestWithUserDetailsFromSession[_]
  ): EitherT[Future, TaxEnrolmentAssignmentErrors, Option[String]] =
    eacdService.getUsersAssignedPTEnrolment.flatMap { userAssignedEnrolment: UsersAssignedEnrolment =>
      userAssignedEnrolment.enrolledCredential match {
        // HMRC-PT enrolment found on the logged in credential
        case Some(requestWithUserDetails.userDetails.credId) =>
          EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](None: Option[String])

        // HMRC-PT enrolment found on a different credential than the logged in one
        case Some(credId) => EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](Some(credId): Option[String])

        // No HMRC-PT enrolment found on any credential
        case None =>
          // The HMRC-PT enrolment can be hold by an empty group. if it happens it needs to be deleted DDCNL-9718
          hmrcPTEnrolment.findAndDeleteGroupsWithPTEnrolment.transform {
            case Left(error) =>
              Left(error: TaxEnrolmentAssignmentErrors)
            case Right(_) =>
              Right(None: Option[String])
          }
      }
    }

  private def findIrSaOnOtherAccount(implicit
    ec: ExecutionContext,
    hc: HeaderCarrier,
    requestWithUserDetails: RequestWithUserDetailsFromSession[_]
  ): EitherT[Future, TaxEnrolmentAssignmentErrors, Option[String]] =
    eacdService.getUsersAssignedSAEnrolment.map { userAssignedEnrolment: UsersAssignedEnrolment =>
      userAssignedEnrolment.enrolledCredential match {
        case Some(requestWithUserDetails.userDetails.credId) => None
        case Some(credId)                                    => Some(credId)
        case None                                            => None
      }
    }

  private def returnAccountType(
    hmrcPtOnOtherAccount: Option[String],
    irSaOnOtherAccount: Option[String],
    hmrcPtOnCurrentAccount: Boolean,
    irSaOnCurrentAccount: Boolean
  )(implicit
    requestWithUserDetails: RequestWithUserDetailsFromSession[_]
  ): AccountTypes.Value =
    (hmrcPtOnOtherAccount, irSaOnOtherAccount, hmrcPtOnCurrentAccount, irSaOnCurrentAccount) match {
      case (None, _, true, _) =>
        logger.logEvent(
          logCurrentUserAlreadyHasPTEnrolment(
            requestWithUserDetails.userDetails.credId
          )
        )
        PT_ASSIGNED_TO_CURRENT_USER
      case (Some(credId), _, false, _) =>
        logger.logEvent(
          logAnotherAccountAlreadyHasPTEnrolment(
            requestWithUserDetails.userDetails.credId,
            credId
          )
        )
        PT_ASSIGNED_TO_OTHER_USER
      case (Some(_), _, true, _) =>
        throw new RuntimeException("HMRC-PT enrolment cannot be on both the current and an other account")
      case (_, None, _, true) =>
        logger.logEvent(
          logCurrentUserHasSAEnrolment(requestWithUserDetails.userDetails.credId)
        )
        SA_ASSIGNED_TO_CURRENT_USER
      case (_, Some(credId), _, false) =>
        logger.logEvent(
          logAnotherAccountHasSAEnrolment(
            requestWithUserDetails.userDetails.credId,
            credId
          )
        )
        SA_ASSIGNED_TO_OTHER_USER
      case (_, Some(credId), _, true) =>
        logger.logEvent(
          logCurrentAndAnotherAccountHasSAEnrolment(
            requestWithUserDetails.userDetails.credId,
            credId
          )
        )
        SA_ASSIGNED_TO_CURRENT_USER
      case _ =>
        logger.logEvent(logCurrentUserhasOneOrMultipleAccounts(requestWithUserDetails.userDetails.credId))
        SINGLE_OR_MULTIPLE_ACCOUNTS
    }

  def getAccountType(implicit
    ec: ExecutionContext,
    hc: HeaderCarrier,
    requestWithUserDetails: RequestWithUserDetailsFromSession[_]
  ): EitherT[Future, TaxEnrolmentAssignmentErrors, AccountTypes.Value] = EitherT {
    getOptAccountTypeFromCache.foldF {
      val hmrcPt = requestWithUserDetails.userDetails.hasPTEnrolment
      val irSa = requestWithUserDetails.userDetails.hasSAEnrolment

      (for {
        hmrcPtOnOtherAccount <- findAndFixHmrcPtEnrolmentOnOtherAccount
        irSaOnOtherAccount   <- findIrSaOnOtherAccount
        accountType = returnAccountType(hmrcPtOnOtherAccount, irSaOnOtherAccount, hmrcPt, irSa)
        _ <-
          EitherT[Future, TaxEnrolmentAssignmentErrors, CacheMap](
            sessionCache.save[AccountTypes.Value](ACCOUNT_TYPE, accountType).map(Right(_))
          )
      } yield accountType).value

    }(account => Future.successful(Right(account)))
  }

  private def getOptAccountTypeFromCache(implicit
    request: RequestWithUserDetailsFromSession[_],
    ec: ExecutionContext
  ): OptionT[Future, AccountTypes.Value] =
    OptionT(sessionCache.fetch().map { optCachedMap =>
      optCachedMap
        .fold[Option[AccountTypes.Value]](
          None
        ) { cachedMap =>
          AccountDetailsFromMongo.optAccountType(cachedMap.data)
        }
    })
}
