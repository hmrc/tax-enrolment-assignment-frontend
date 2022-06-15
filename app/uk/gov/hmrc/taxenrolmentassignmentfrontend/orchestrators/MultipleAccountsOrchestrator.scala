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
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.actions.{AccountDetailsFromMongo, RequestWithUserDetailsFromSession, RequestWithUserDetailsFromSessionAndMongo}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.{IncorrectUserType, NoPTEnrolmentWhenOneExpected, NoSAEnrolmentWhenOneExpected, TaxEnrolmentAssignmentErrors, UnexpectedPTEnrolment, UserDoesNotHaveSAOnCurrentToEnrol}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.forms.KeepAccessToSAThroughPTAForm
import uk.gov.hmrc.taxenrolmentassignmentfrontend.logging.EventLoggerService
import uk.gov.hmrc.taxenrolmentassignmentfrontend.logging.LoggingEvent.logNoUserFoundWithPTEnrolment
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.forms.KeepAccessToSAThroughPTA
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.setupSAJourney.SASetupJourneyResponse
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.{AccountDetails, CADetailsPTADetailsSADetailsIfExists, PTEnrolmentOnOtherAccount, UsersAssignedEnrolment}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.SessionKeys._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.TEASessionCache
import uk.gov.hmrc.taxenrolmentassignmentfrontend.services.{AddTaxesFrontendService, EACDService, SilentAssignmentService, UsersGroupsSearchService}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class MultipleAccountsOrchestrator @Inject()(
  sessionCache: TEASessionCache,
  usersGroupSearchService: UsersGroupsSearchService,
  silentAssignmentService: SilentAssignmentService,
  eacdService: EACDService,
  addTaxesFrontendService: AddTaxesFrontendService,
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
  def enrolForSA(implicit requestWithUserDetailsFromSessionAndMongo: RequestWithUserDetailsFromSessionAndMongo[_],
                   hc: HeaderCarrier,
                   ec: ExecutionContext): TEAFResult[SASetupJourneyResponse] = {

    (checkValidAccountType(List(PT_ASSIGNED_TO_OTHER_USER)), requestWithUserDetailsFromSessionAndMongo.userDetails.hasSAEnrolment)  match {
        case (Left(error), _) => EitherT.left(Future.successful(error))
        case (Right(_), false) => EitherT.left(Future.successful(UserDoesNotHaveSAOnCurrentToEnrol))
        case (Right(_), _) => addTaxesFrontendService.saSetupJourney(requestWithUserDetailsFromSessionAndMongo.userDetails)
      }
    }

  def getDetailsForKeepAccessToSA(
                                   implicit requestWithUserDetails: RequestWithUserDetailsFromSessionAndMongo[_],
                                   hc: HeaderCarrier,
                                   ec: ExecutionContext
  ): TEAFResult[Form[KeepAccessToSAThroughPTA]] = {
    checkAccessAllowedForPage(List(SA_ASSIGNED_TO_OTHER_USER)) match {
      case Left(error) => EitherT.left(Future.successful(error))
      case Right(_) => EitherT.right[TaxEnrolmentAssignmentErrors](
        Future.successful(
          requestWithUserDetails.accountDetailsFromMongo.optKeepAccessToSAFormData match {
            case Some(data) => KeepAccessToSAThroughPTAForm.keepAccessToSAThroughPTAForm.fill(data)
            case None => KeepAccessToSAThroughPTAForm.keepAccessToSAThroughPTAForm
          }
        )
      )
    }
  }

  def handleKeepAccessToSAChoice(keepAccessToSA: KeepAccessToSAThroughPTA)(
    implicit requestWithUserDetails: RequestWithUserDetailsFromSessionAndMongo[_],
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): TEAFResult[Boolean] = {
    checkAccessAllowedForPage(List(SA_ASSIGNED_TO_OTHER_USER)) match {
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
    requestWithUserDetails.accountDetailsFromMongo.optReportedFraud match {
      case Some(true) => Future.successful(Right(None))
      case _          => getSACredentialDetails.map(Some(_)).value
    }
  }

  def getSACredentialDetails(
                              implicit requestWithUserDetails: RequestWithUserDetailsFromSessionAndMongo[_],
                              hc: HeaderCarrier,
                              ec: ExecutionContext
  ): TEAFResult[AccountDetails] = EitherT {
    val optCredential = requestWithUserDetails.accountDetailsFromMongo.optUserAssignedSA
    optCredential.fold[Option[String]](None)(_.enrolledCredential) match {
      case Some(saCred) =>
        usersGroupSearchService.getAccountDetails(saCred)(implicitly, implicitly, requestWithUserDetails).value
      case _ => Future.successful(Left(NoSAEnrolmentWhenOneExpected))
    }
  }

  def getPTCredentialDetails(
                              implicit requestWithUserDetails: RequestWithUserDetailsFromSessionAndMongo[_],
                              hc: HeaderCarrier,
                              ec: ExecutionContext
  ): TEAFResult[AccountDetails] = EitherT {
    val optCredential = requestWithUserDetails.accountDetailsFromMongo.optUserAssignedPT
    optCredential.fold[Option[String]](None)(_.enrolledCredential) match {
      case Some(ptCred)
          if ptCred != requestWithUserDetails.userDetails.credId =>
        usersGroupSearchService.getAccountDetails(ptCred)(implicitly, implicitly, requestWithUserDetails).value
      case _ =>
        logger.logEvent(
          logNoUserFoundWithPTEnrolment(
            requestWithUserDetails.userDetails.credId
          )
        )
        Future.successful(Left(NoPTEnrolmentWhenOneExpected))
    }
  }

  def checkValidAccountTypeAndEnrolForPT(
    expectedAccountType: AccountTypes.Value
  )(implicit requestWithUserDetails: RequestWithUserDetailsFromSessionAndMongo[_],
    hc: HeaderCarrier,
    ec: ExecutionContext): TEAFResult[Unit] = {
    checkAccessAllowedForPage(List(expectedAccountType)) match {
      case Right(_) => silentAssignmentService.enrolUser()(requestWithUserDetails, implicitly, implicitly)
      case Left(error) => EitherT.left(Future.successful(error))
    }
  }

  def checkAccessAllowedForPage(validAccountTypes: List[AccountTypes.Value])(
    implicit requestWithUserDetailsAndMongo: RequestWithUserDetailsFromSessionAndMongo[_]): Either[TaxEnrolmentAssignmentErrors, AccountTypes.Value] = {

    (checkValidAccountType(validAccountTypes), checkPTEnrolmentDoesNotExist) match {
      case (Right(accountType), Right(_)) => Right(accountType)
      case (Right(_), Left(error)) => Left(error)
      case _ =>
        Left(
          IncorrectUserType(
            requestWithUserDetailsAndMongo.accountDetailsFromMongo.redirectUrl,
            requestWithUserDetailsAndMongo.accountDetailsFromMongo.accountType
          )
        )
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

  def checkPTEnrolmentDoesNotExist(
    implicit requestWithUserDetailsAndMongo: RequestWithUserDetailsFromSessionAndMongo[_]): Either[TaxEnrolmentAssignmentErrors, Unit] = {
    val hasPTEnrolment = requestWithUserDetailsAndMongo.userDetails.hasPTEnrolment

    if(hasPTEnrolment) {
      Left(UnexpectedPTEnrolment(requestWithUserDetailsAndMongo.accountDetailsFromMongo.accountType))
    } else {
      Right((): Unit)
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
        model.saAccountDetails.map(_.userId)

      )
    }
  }

  private def getSAForPTAlreadyEnrolledDetails(
                                        implicit requestWithUserDetails: RequestWithUserDetailsFromSessionAndMongo[_],
                                        hc: HeaderCarrier,
                                        ec: ExecutionContext
                                      ): TEAFResult[CADetailsPTADetailsSADetailsIfExists] = {

    lazy val optSACredentialId: TEAFResult[Option[String]] =
      (requestWithUserDetails.userDetails.hasSAEnrolment, requestWithUserDetails.accountDetailsFromMongo.optUserAssignedSA) match {
      case (true, _) =>
        EitherT.right[TaxEnrolmentAssignmentErrors](Future.successful(Some(requestWithUserDetails.userDetails.credId)))
      case (false, Some(usersAssignedEnrolment)) =>
        EitherT.right[TaxEnrolmentAssignmentErrors](Future.successful(usersAssignedEnrolment.enrolledCredential))
      case (false, None) =>
        eacdService.getUsersAssignedSAEnrolment(requestWithUserDetails, implicitly, implicitly)
          .map(user => user.enrolledCredential)
    }

    def getSAAccountDetails(currentAccountDetails: AccountDetails, ptAccountDetails: AccountDetails): TEAFResult[Option[AccountDetails]] = {
      optSACredentialId.flatMap {
        case Some(credId) if credId == currentAccountDetails.credId =>
          EitherT.right[TaxEnrolmentAssignmentErrors](Future.successful(Some(currentAccountDetails)))
        case Some(credId) if credId == ptAccountDetails.credId =>
          EitherT.right[TaxEnrolmentAssignmentErrors](Future.successful(Some(ptAccountDetails)))
        case Some(credId) =>
          usersGroupSearchService.getAccountDetails(credId)(implicitly, implicitly, requestWithUserDetails).map(Some(_))
        case None =>
          EitherT.right[TaxEnrolmentAssignmentErrors](Future.successful(Option.empty[AccountDetails]))
      }
    }

    for {
      _ <- EitherT(Future(checkAccessAllowedForPage(List(PT_ASSIGNED_TO_OTHER_USER))))
      currentAccountDetails <- usersGroupSearchService.getAccountDetails(requestWithUserDetails.userDetails.credId
      )(implicitly, implicitly, requestWithUserDetails)
      ptAccountDetails <- getPTCredentialDetails
      saOnOtherAccountDetails <- getSAAccountDetails(currentAccountDetails, ptAccountDetails)
    } yield
        CADetailsPTADetailsSADetailsIfExists(
          currentAccountDetails,
          ptAccountDetails,
          saOnOtherAccountDetails
        )
    }

  }
