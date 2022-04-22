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
import com.google.inject.{Inject, Singleton}
import play.api.mvc.AnyContent
import uk.gov.hmrc.http.HeaderCarrier
import cats.implicits._
import play.api.Logger
import uk.gov.hmrc.service.TEAFResult
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes.{
  MULTIPLE_ACCOUNTS,
  PT_ASSIGNED_TO_OTHER_USER,
  SA_ASSIGNED_TO_CURRENT_USER,
  SA_ASSIGNED_TO_OTHER_USER
}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.auth.RequestWithUserDetails
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.{
  InvalidUserType,
  NoPTEnrolmentWhenOneExpected,
  NoSAEnrolmentWhenOneExpected,
  TaxEnrolmentAssignmentErrors
}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.logging.EventLoggerService
import uk.gov.hmrc.taxenrolmentassignmentfrontend.logging.LoggingEvent.{
  logIncorrectUserType,
  logNoUserFoundWithPTEnrolment
}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.{
  AccountDetails,
  UsersAssignedEnrolment
}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.SessionKeys.{
  ACCOUNT_TYPE,
  REDIRECT_URL,
  REPORTED_FRAUD,
  USER_ASSIGNED_PT_ENROLMENT,
  USER_ASSIGNED_SA_ENROLMENT
}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.TEASessionCache
import uk.gov.hmrc.taxenrolmentassignmentfrontend.services.{
  EACDService,
  SilentAssignmentService,
  UsersGroupsSearchService
}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class MultipleAccountsOrchestrator @Inject()(
  sessionCache: TEASessionCache,
  usersGroupSearchService: UsersGroupsSearchService,
  silentAssignmentService: SilentAssignmentService,
  logger: EventLoggerService
) {

  implicit val baseLogger: Logger = Logger(this.getClass.getName)

  def getDetailsForEnrolledPT(
    implicit requestWithUserDetails: RequestWithUserDetails[AnyContent],
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): TEAFResult[AccountDetails] = {
    for {
      accountType <- checkValidAccountTypeRedirectUrlInCache(
        List(MULTIPLE_ACCOUNTS, SA_ASSIGNED_TO_CURRENT_USER)
      )
      accountDetails <- usersGroupSearchService.getAccountDetails(
        requestWithUserDetails.userDetails.credId
      )
    } yield
      accountDetails.copy(
        hasSA = Some(accountType == SA_ASSIGNED_TO_CURRENT_USER)
      )
  }

  def getDetailsForEnrolledPTWithSAOnOtherAccount(
    implicit requestWithUserDetails: RequestWithUserDetails[AnyContent],
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): TEAFResult[AccountDetails] = {
    for {
      _ <- checkValidAccountTypeRedirectUrlInCache(
        List(SA_ASSIGNED_TO_OTHER_USER)
      )
      accountDetails <- usersGroupSearchService.getAccountDetails(
        requestWithUserDetails.userDetails.credId
      )
    } yield accountDetails

  }

  def getSACredentialIfNotFraud(
    implicit requestWithUserDetails: RequestWithUserDetails[AnyContent],
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): TEAFResult[Option[AccountDetails]] = EitherT {
    sessionCache.getEntry[Boolean](REPORTED_FRAUD).flatMap {
      case Some(true) => Future.successful(Right(None))
      case _          => getSACredentialDetails.map(Some(_)).value
    }
  }

  def getSACredentialDetails(
    implicit requestWithUserDetails: RequestWithUserDetails[AnyContent],
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): TEAFResult[AccountDetails] = EitherT {
    sessionCache
      .getEntry[UsersAssignedEnrolment](USER_ASSIGNED_SA_ENROLMENT)
      .flatMap { optCredential =>
        optCredential.fold[Option[String]](None)(_.enrolledCredential) match {
          case Some(saCred) =>
            usersGroupSearchService.getAccountDetails(saCred).value
          case _ => Future.successful(Left(NoSAEnrolmentWhenOneExpected))
        }
      }
  }

  def getPTCredentialDetails(
    implicit requestWithUserDetails: RequestWithUserDetails[AnyContent],
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): TEAFResult[AccountDetails] = EitherT {
    sessionCache
      .getEntry[UsersAssignedEnrolment](USER_ASSIGNED_PT_ENROLMENT)
      .flatMap { optCredential =>
        optCredential.fold[Option[String]](None)(_.enrolledCredential) match {
          case Some(saCred)
              if saCred != requestWithUserDetails.userDetails.credId =>
            usersGroupSearchService.getAccountDetails(saCred).value
          case _ =>
            logger.logEvent(
              logNoUserFoundWithPTEnrolment(
                requestWithUserDetails.userDetails.credId
              )
            )
            Future.successful(Left(NoPTEnrolmentWhenOneExpected))
        }
      }
  }

  def checkValidAccountTypeAndEnrolForPT(
    expectedAccountType: AccountTypes.Value
  )(implicit requestWithUserDetails: RequestWithUserDetails[AnyContent],
    hc: HeaderCarrier,
    ec: ExecutionContext): TEAFResult[Unit] = {
    for {
      _ <- checkValidAccountTypeRedirectUrlInCache(List(expectedAccountType))
      enrolled <- silentAssignmentService.enrolUser()
    } yield enrolled
  }

  def checkValidAccountTypeRedirectUrlInCache(
    validAccountTypes: List[AccountTypes.Value]
  )(implicit requestWithUserDetails: RequestWithUserDetails[AnyContent],
    hc: HeaderCarrier,
    ec: ExecutionContext): TEAFResult[AccountTypes.Value] = EitherT {
    val res = for {
      accountType <- sessionCache.getEntry[AccountTypes.Value](ACCOUNT_TYPE)
      redirectUrl <- sessionCache.getEntry[String](REDIRECT_URL)
    } yield (accountType, redirectUrl)

    res.map {
      case (Some(accountType), Some(_))
          if validAccountTypes.contains(accountType) =>
        Right(accountType)
      case (optAccountType, optRedirectUrl) =>
        logger.logEvent(
          logIncorrectUserType(
            requestWithUserDetails.userDetails.credId,
            validAccountTypes,
            optAccountType
          )
        )
        Left(InvalidUserType(optRedirectUrl))
    }
  }
}
