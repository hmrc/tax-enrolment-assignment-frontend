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
import play.api.mvc.AnyContent
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes.{PT_ASSIGNED_TO_CURRENT_USER, PT_ASSIGNED_TO_OTHER_USER, SA_ASSIGNED_TO_CURRENT_USER, SA_ASSIGNED_TO_OTHER_USER, SINGLE_OR_MULTIPLE_ACCOUNTS}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.actions.{AccountDetailsFromMongo, DataRequest}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.TaxEnrolmentAssignmentErrors
import uk.gov.hmrc.taxenrolmentassignmentfrontend.logging.EventLoggerService
import uk.gov.hmrc.taxenrolmentassignmentfrontend.logging.LoggingEvent.{logAnotherAccountAlreadyHasPTEnrolment, logAnotherAccountHasSAEnrolment, logCurrentUserAlreadyHasPTEnrolment, logCurrentUserHasSAEnrolment, logCurrentUserhasOneOrMultipleAccounts}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.UsersAssignedEnrolment
import uk.gov.hmrc.taxenrolmentassignmentfrontend.pages.{AccountTypePage, RedirectUrlPage, UserAssignedPtaEnrolmentPage, UserAssignedSaEnrolmentPage}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.JourneyCacheRepository
import uk.gov.hmrc.taxenrolmentassignmentfrontend.services.EACDService

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AccountCheckOrchestrator @Inject() (
  eacdService: EACDService,
  logger: EventLoggerService,
  journeyCacheRepository: JourneyCacheRepository
) {

  implicit val baseLogger: Logger = Logger(this.getClass.getName)

  def getAccountType(redirectUrl: Option[String])(implicit
    ec: ExecutionContext,
    hc: HeaderCarrier,
    request: DataRequest[AnyContent]
  ): EitherT[Future, TaxEnrolmentAssignmentErrors, AccountTypes.Value] = EitherT {

    getOptAccountTypeFromCache.foldF {
      val usersAssignedPtaEnrolmentResult = eacdService.getUsersAssignedPTEnrolment
      val hmrcPtOnOtherAccountFuture =
        usersAssignedPtaEnrolmentResult.map { userAssignedEnrolment: UsersAssignedEnrolment =>
          userAssignedEnrolment.enrolledCredential match {
            case Some(request.userDetails.credId) => None
            case Some(credId)                     => Some(credId)
            case None                             => None
          }

        }

      val usersAssignedSAEnrolmentResult = eacdService.getUsersAssignedSAEnrolment
      val irSaOnOtherAccountFuture = usersAssignedSAEnrolmentResult.map {
        userAssignedEnrolment: UsersAssignedEnrolment =>
          userAssignedEnrolment.enrolledCredential match {
            case Some(request.userDetails.credId) => None
            case Some(credId)                     => Some(credId)
            case None                             => None
          }
      }

      val hmrcPt = request.userDetails.hasPTEnrolment
      val irSa = request.userDetails.hasSAEnrolment

      (for {
        usersAssignedSAEnrolmentValue  <- usersAssignedSAEnrolmentResult
        usersAssignedPtaEnrolmentValue <- usersAssignedPtaEnrolmentResult
        hmrcPtOnOtherAccount           <- hmrcPtOnOtherAccountFuture
        irSaOnOtherAccount             <- irSaOnOtherAccountFuture
        accountType = {
          (hmrcPtOnOtherAccount, irSaOnOtherAccount, hmrcPt, irSa) match {
            case (None, _, true, _) =>
              logger.logEvent(
                logCurrentUserAlreadyHasPTEnrolment(
                  request.userDetails.credId
                )
              )
              PT_ASSIGNED_TO_CURRENT_USER
            case (Some(credId), _, false, _) =>
              logger.logEvent(
                logAnotherAccountAlreadyHasPTEnrolment(
                  request.userDetails.credId,
                  credId
                )
              )
              PT_ASSIGNED_TO_OTHER_USER
            case (Some(_), _, true, _) =>
              throw new RuntimeException("HMRC-PT enrolment cannot be on both the current and an other account")
            case (_, None, _, true) =>
              logger.logEvent(
                logCurrentUserHasSAEnrolment(request.userDetails.credId)
              )
              SA_ASSIGNED_TO_CURRENT_USER
            case (_, Some(credId), _, false) =>
              logger.logEvent(
                logAnotherAccountHasSAEnrolment(
                  request.userDetails.credId,
                  credId
                )
              )
              SA_ASSIGNED_TO_OTHER_USER
            case (_, Some(_), _, true) =>
              throw new RuntimeException("IR-SA enrolment cannot be on both the current and an other account")
            case _ =>
              logger.logEvent(logCurrentUserhasOneOrMultipleAccounts(request.userDetails.credId))
              SINGLE_OR_MULTIPLE_ACCOUNTS
          }
        }
        _ =
          journeyCacheRepository
            .set(
              request.userAnswers
                .setOrException(AccountTypePage, accountType.toString)
                .setOrException(RedirectUrlPage, redirectUrl.getOrElse(""))
                .setOrException(UserAssignedSaEnrolmentPage, usersAssignedSAEnrolmentValue)
                .setOrException(UserAssignedPtaEnrolmentPage, usersAssignedPtaEnrolmentValue)
            )
            .map(Right(_))
      } yield accountType).value

    }(account => Future.successful(Right(account)))
  }

  private def getOptAccountTypeFromCache(implicit
    request: DataRequest[AnyContent]
  ): OptionT[Future, AccountTypes.Value] =
    OptionT(
      Future.successful(request.userAnswers.get(AccountTypePage).map(AccountDetailsFromMongo.optAccountType(_).get))
    )
}
