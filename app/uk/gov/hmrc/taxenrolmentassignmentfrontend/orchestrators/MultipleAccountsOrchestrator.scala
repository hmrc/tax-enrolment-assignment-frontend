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
import play.api.data.Form
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.service.TEAFResult
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes.{MULTIPLE_ACCOUNTS, PT_ASSIGNED_TO_OTHER_USER, SA_ASSIGNED_TO_CURRENT_USER, SA_ASSIGNED_TO_OTHER_USER}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.actions.RequestWithUserDetailsFromSessionAndMongo
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.{IncorrectUserType, NoPTEnrolmentWhenOneExpected, NoSAEnrolmentWhenOneExpected, TaxEnrolmentAssignmentErrors}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.forms.KeepAccessToSAThroughPTAForm
import uk.gov.hmrc.taxenrolmentassignmentfrontend.logging.EventLoggerService
import uk.gov.hmrc.taxenrolmentassignmentfrontend.logging.LoggingEvent.logNoUserFoundWithPTEnrolment
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.forms.KeepAccessToSAThroughPTA
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.{AccountDetails, CADetailsPTADetailsSADetailsIfExists, PTEnrolmentOnOtherAccount, UsersAssignedEnrolment}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.SessionKeys._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.TEASessionCache
import uk.gov.hmrc.taxenrolmentassignmentfrontend.services.{EACDService, SilentAssignmentService, UsersGroupsSearchService}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class MultipleAccountsOrchestrator @Inject()(
  sessionCache: TEASessionCache,
  usersGroupSearchService: UsersGroupsSearchService,
  silentAssignmentService: SilentAssignmentService,
  eacdService: EACDService,
  logger: EventLoggerService
) {

  implicit val baseLogger: Logger = Logger(this.getClass.getName)

  def getDetailsForEnrolledPT(
                               implicit requestWithUserDetails: RequestWithUserDetailsFromSessionAndMongo[_],
                               hc: HeaderCarrier,
                               ec: ExecutionContext
  ): TEAFResult[AccountDetails] = {
    checkValidAccountType(List(MULTIPLE_ACCOUNTS, SA_ASSIGNED_TO_CURRENT_USER)) match {
      case Left(error) => EitherT.left(Future.successful(error))
      case Right(_)    => usersGroupSearchService.getAccountDetails(
        requestWithUserDetails.userDetails.credId
      )(implicitly, implicitly, requestWithUserDetails).map(accountDetails =>
      accountDetails.copy(
        hasSA = Some(requestWithUserDetails.accountDetailsFromMongo.accountType == SA_ASSIGNED_TO_CURRENT_USER)
      ))
    }
  }

  def getDetailsForEnrolledPTWithSAOnOtherAccount(
                                                   implicit requestWithUserDetails: RequestWithUserDetailsFromSessionAndMongo[_],
                                                   hc: HeaderCarrier,
                                                   ec: ExecutionContext
  ): TEAFResult[AccountDetails] = {
    checkValidAccountType(List(SA_ASSIGNED_TO_OTHER_USER)) match {
      case Left(error) => EitherT.left(Future.successful(error))
      case Right(_) => usersGroupSearchService.getAccountDetails(
        requestWithUserDetails.userDetails.credId
      )(implicitly, implicitly, requestWithUserDetails)
    }
  }

  def getDetailsForKeepAccessToSA(
                                   implicit requestWithUserDetails: RequestWithUserDetailsFromSessionAndMongo[_],
                                   hc: HeaderCarrier,
                                   ec: ExecutionContext
  ): TEAFResult[Form[KeepAccessToSAThroughPTA]] = {
    checkValidAccountType(List(SA_ASSIGNED_TO_OTHER_USER)) match {
      case Left(error) => EitherT.left(Future.successful(error))
      case Right(_) => EitherT.right[TaxEnrolmentAssignmentErrors](
        sessionCache.getEntry[KeepAccessToSAThroughPTA](KEEP_ACCESS_TO_SA_THROUGH_PTA_FORM)(requestWithUserDetails, implicitly)
          .map {
            case Some(data) =>
              KeepAccessToSAThroughPTAForm.keepAccessToSAThroughPTAForm
                .fill(data)
            case None => KeepAccessToSAThroughPTAForm.keepAccessToSAThroughPTAForm
          }
      )
    }
  }

  def handleKeepAccessToSAChoice(keepAccessToSA: KeepAccessToSAThroughPTA)(
    implicit requestWithUserDetails: RequestWithUserDetailsFromSessionAndMongo[_],
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): TEAFResult[Boolean] = {
    checkValidAccountType(List(SA_ASSIGNED_TO_OTHER_USER)) match {
      case Left(error) => EitherT.left(Future.successful(error))
      case Right(_) =>
        for {
          _ <- if (keepAccessToSA.keepAccessToSAThroughPTA) {
            EitherT.right[TaxEnrolmentAssignmentErrors](Future.successful((): Unit))
          } else {
            silentAssignmentService.enrolUser()(requestWithUserDetails, implicitly, implicitly)
          }
          _ <- EitherT.right[TaxEnrolmentAssignmentErrors](
            sessionCache
              .save[KeepAccessToSAThroughPTA](
                KEEP_ACCESS_TO_SA_THROUGH_PTA_FORM,
                keepAccessToSA
              )(requestWithUserDetails, implicitly)
          )
        } yield keepAccessToSA.keepAccessToSAThroughPTA
    }
  }


  def getSACredentialIfNotFraud(
                                 implicit requestWithUserDetails: RequestWithUserDetailsFromSessionAndMongo[_],
                                 hc: HeaderCarrier,
                                 ec: ExecutionContext
  ): TEAFResult[Option[AccountDetails]] = EitherT {
    sessionCache.getEntry[Boolean](REPORTED_FRAUD)(requestWithUserDetails, implicitly).flatMap {
      case Some(true) => Future.successful(Right(None))
      case _          => getSACredentialDetails.map(Some(_)).value
    }
  }

  def getSACredentialDetails(
                              implicit requestWithUserDetails: RequestWithUserDetailsFromSessionAndMongo[_],
                              hc: HeaderCarrier,
                              ec: ExecutionContext
  ): TEAFResult[AccountDetails] = EitherT {
    sessionCache
      .getEntry[UsersAssignedEnrolment](USER_ASSIGNED_SA_ENROLMENT)(requestWithUserDetails, implicitly)
      .flatMap { optCredential =>
        optCredential.fold[Option[String]](None)(_.enrolledCredential) match {
          case Some(saCred) =>
            usersGroupSearchService.getAccountDetails(saCred)(implicitly, implicitly, requestWithUserDetails).value
          case _ => Future.successful(Left(NoSAEnrolmentWhenOneExpected))
        }
      }
  }

  def getPTCredentialDetails(
                              implicit requestWithUserDetails: RequestWithUserDetailsFromSessionAndMongo[_],
                              hc: HeaderCarrier,
                              ec: ExecutionContext
  ): TEAFResult[AccountDetails] = EitherT {
    sessionCache
      .getEntry[UsersAssignedEnrolment](USER_ASSIGNED_PT_ENROLMENT)(requestWithUserDetails, implicitly)
      .flatMap { optCredential =>
        optCredential.fold[Option[String]](None)(_.enrolledCredential) match {
          case Some(saCred)
              if saCred != requestWithUserDetails.userDetails.credId =>
            usersGroupSearchService.getAccountDetails(saCred)(implicitly, implicitly, requestWithUserDetails).value
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
  )(implicit requestWithUserDetails: RequestWithUserDetailsFromSessionAndMongo[_],
    hc: HeaderCarrier,
    ec: ExecutionContext): TEAFResult[Unit] = {
    checkValidAccountType(List(expectedAccountType)) match {
      case Right(_) => silentAssignmentService.enrolUser()(requestWithUserDetails, implicitly, implicitly)
      case Left(error) => EitherT.left(Future.successful(error))
    }
  }

  def checkValidAccountType(validAccountTypes: List[AccountTypes.Value])(
    implicit requestWithUserDetailsAndMongo: RequestWithUserDetailsFromSessionAndMongo[_]): Either[TaxEnrolmentAssignmentErrors, AccountTypes.Value] = {
          if (validAccountTypes.contains(requestWithUserDetailsAndMongo.accountDetailsFromMongo.accountType)) {
            Right(requestWithUserDetailsAndMongo.accountDetailsFromMongo.accountType)
          } else {
            Left(
              IncorrectUserType(
                requestWithUserDetailsAndMongo.accountDetailsFromMongo.redirectUrl,
                requestWithUserDetailsAndMongo.accountDetailsFromMongo.accountType
              )
            )
          }
    }

  def getCurrentAndPTAAndSAIfExistsForUser(implicit requestWithUserDetails: RequestWithUserDetailsFromSessionAndMongo[_],
                                                hc: HeaderCarrier,
                                                ec: ExecutionContext
                                               ): TEAFResult[PTEnrolmentOnOtherAccount]  = {
    getSAForPTAlreadyEnrolledDetails.map { model =>
      PTEnrolmentOnOtherAccount(
        model.currentAccountDetails,
        model.ptAccountDetails,
        if (requestWithUserDetails.userDetails.hasSAEnrolment) {
          Some(model.currentAccountDetails.userId)
        } else {
          model.saAccountDetails.map(_.userId)
        }
      )
    }
  }

  private def getSAForPTAlreadyEnrolledDetails(
                                        implicit requestWithUserDetails: RequestWithUserDetailsFromSessionAndMongo[_],
                                        hc: HeaderCarrier,
                                        ec: ExecutionContext
                                      ): TEAFResult[CADetailsPTADetailsSADetailsIfExists] = {

    def getSAAccountDetails: TEAFResult[Option[AccountDetails]] = {
      if (!requestWithUserDetails.userDetails.hasSAEnrolment) {
        eacdService.getUsersAssignedSAEnrolment(requestWithUserDetails, implicitly, implicitly).map(
          user => user.enrolledCredential
        ).flatMap {
          case Some(userId) => usersGroupSearchService.getAccountDetails(userId)(implicitly, implicitly, requestWithUserDetails).map(Some(_))
          case None => EitherT.right[TaxEnrolmentAssignmentErrors](Future.successful(Option.empty[AccountDetails]))
        }
      } else {
        EitherT.right[TaxEnrolmentAssignmentErrors](Future.successful(None))
      }

    }

    checkValidAccountType(List(PT_ASSIGNED_TO_OTHER_USER)) match {
      case Right(_) => for {
        currentAccountDetails <- usersGroupSearchService.getAccountDetails(
          requestWithUserDetails.userDetails.credId
        )(implicitly, implicitly, requestWithUserDetails)
        ptAccountDetails <- getPTCredentialDetails
        saOnOtherAccountDetails <- getSAAccountDetails
      }
      yield
        CADetailsPTADetailsSADetailsIfExists(
          currentAccountDetails,
          ptAccountDetails,
          saOnOtherAccountDetails)

      case Left(error) => EitherT.left(Future.successful(error))
    }
  }

  }
