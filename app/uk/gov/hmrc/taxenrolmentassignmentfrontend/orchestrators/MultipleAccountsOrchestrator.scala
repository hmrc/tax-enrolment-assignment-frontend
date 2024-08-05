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

import cats.data.EitherT
import cats.implicits._
import play.api.Logger
import play.api.data.Form
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.service.TEAFResult
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes.{PT_ASSIGNED_TO_OTHER_USER, SA_ASSIGNED_TO_CURRENT_USER, SA_ASSIGNED_TO_OTHER_USER, SINGLE_OR_MULTIPLE_ACCOUNTS}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.actions.DataRequest
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.forms.KeepAccessToSAThroughPTAForm
import uk.gov.hmrc.taxenrolmentassignmentfrontend.logging.EventLoggerService
import uk.gov.hmrc.taxenrolmentassignmentfrontend.logging.LoggingEvent.logNoUserFoundWithPTEnrolment
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.forms.KeepAccessToSAThroughPTA
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.{AccountDetails, CADetailsPTADetailsSADetailsIfExists, PTEnrolmentOnOtherAccount, UsersAssignedEnrolment}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.pages.KeepAccessToSAThroughPTAPage
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.JourneyCacheRepository
import uk.gov.hmrc.taxenrolmentassignmentfrontend.services.{EACDService, SilentAssignmentService, UsersGroupsSearchService}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class MultipleAccountsOrchestrator @Inject() (
  usersGroupSearchService: UsersGroupsSearchService,
  silentAssignmentService: SilentAssignmentService,
  eacdService: EACDService,
  logger: EventLoggerService,
  journeyCacheRepository: JourneyCacheRepository
) {

  implicit val baseLogger: Logger = Logger(this.getClass.getName)

  def getDetailsForEnrolledPT(implicit
    request: DataRequest[_],
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): TEAFResult[AccountDetails] =
    checkValidAccountType(
      List(SINGLE_OR_MULTIPLE_ACCOUNTS, SA_ASSIGNED_TO_CURRENT_USER, SA_ASSIGNED_TO_OTHER_USER)
    ) match {
      case Left(error) => EitherT.left(Future.successful(error))
      case Right(_) =>
        usersGroupSearchService
          .getAccountDetails(
            request.userDetails.credId
          )(implicitly, implicitly, request)
          .map(accountDetails =>
            accountDetails.copy(
              hasSA = Some(
                request.requestWithUserDetailsFromSessionAndMongo.get.accountDetailsFromMongo.accountType == SA_ASSIGNED_TO_CURRENT_USER
              )
            )
          )
    }

  def getDetailsForEnrolledPTWithSAOnOtherAccount(implicit
    request: DataRequest[_],
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): TEAFResult[AccountDetails] =
    checkValidAccountType(List(SA_ASSIGNED_TO_OTHER_USER)) match {
      case Left(error) => EitherT.left(Future.successful(error))
      case Right(_) =>
        usersGroupSearchService.getAccountDetails(
          request.userDetails.credId
        )(implicitly, implicitly, request)
    }

  def getDetailsForKeepAccessToSA(implicit
    request: DataRequest[_],
    ec: ExecutionContext
  ): TEAFResult[Form[KeepAccessToSAThroughPTA]] =
    checkAccessAllowedForPage(List(SA_ASSIGNED_TO_OTHER_USER)) match {
      case Left(error) => EitherT.left(Future.successful(error))
      case Right(_) =>
        EitherT.right[TaxEnrolmentAssignmentErrors](
          Future.successful(
            request.requestWithUserDetailsFromSessionAndMongo.get.accountDetailsFromMongo.optKeepAccessToSAFormData match {
              case Some(data) => KeepAccessToSAThroughPTAForm.keepAccessToSAThroughPTAForm.fill(data)
              case None       => KeepAccessToSAThroughPTAForm.keepAccessToSAThroughPTAForm
            }
          )
        )
    }

  def handleKeepAccessToSAChoice(keepAccessToSA: KeepAccessToSAThroughPTA)(implicit
    request: DataRequest[_],
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): TEAFResult[Boolean] =
    checkAccessAllowedForPage(List(SA_ASSIGNED_TO_OTHER_USER)) match {
      case Left(error) => EitherT.left(Future.successful(error))
      case Right(_) =>
        for {
          _ <- if (keepAccessToSA.keepAccessToSAThroughPTA) {
                 EitherT.right[TaxEnrolmentAssignmentErrors](Future.successful((): Unit))
               } else {
                 silentAssignmentService.enrolUser()(request, implicitly, implicitly)
               }
          _ <- EitherT.right[TaxEnrolmentAssignmentErrors](
                 journeyCacheRepository.set(
                   request.userAnswers
                     .setOrException(KeepAccessToSAThroughPTAPage, keepAccessToSA.keepAccessToSAThroughPTA)
                 )
               )
        } yield keepAccessToSA.keepAccessToSAThroughPTA
    }

  def getSACredentialIfNotFraud(implicit
    request: DataRequest[_],
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): TEAFResult[Option[AccountDetails]] = EitherT {
    request.requestWithUserDetailsFromSessionAndMongo.get.accountDetailsFromMongo.optReportedFraud match {
      case Some(true) => Future.successful(Right(None))
      case _          => getSACredentialDetails.map(Some(_)).value
    }
  }

  def getSACredentialDetails(implicit
    request: DataRequest[_],
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): TEAFResult[AccountDetails] = EitherT {
    val optCredential = request.requestWithUserDetailsFromSessionAndMongo.get.accountDetailsFromMongo.optUserAssignedSA
    optCredential.fold[Option[String]](None)(_.enrolledCredential) match {
      case Some(saCred) =>
        usersGroupSearchService.getAccountDetails(saCred)(implicitly, implicitly, request).value
      case _ =>
        eacdService.getUsersAssignedSAEnrolment(request, implicitly, implicitly).value.flatMap {
          case Right(UsersAssignedEnrolment(Some(credId))) =>
            usersGroupSearchService.getAccountDetails(credId)(implicitly, implicitly, request).value
          case _ => Future.successful(Left(NoSAEnrolmentWhenOneExpected))
        }
    }
  }

  def getPTCredentialDetails(implicit
    request: DataRequest[_],
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): TEAFResult[AccountDetails] = EitherT {
    val optCredential = request.requestWithUserDetailsFromSessionAndMongo.get.accountDetailsFromMongo.optUserAssignedPT
    optCredential.fold[Option[String]](None)(_.enrolledCredential) match {
      case Some(ptCred) if ptCred != request.requestWithUserDetailsFromSessionAndMongo.get.userDetails.credId =>
        usersGroupSearchService.getAccountDetails(ptCred)(implicitly, implicitly, request).value
      case _ =>
        logger.logEvent(
          logNoUserFoundWithPTEnrolment(
            request.userDetails.credId
          )
        )
        Future.successful(Left(NoPTEnrolmentWhenOneExpected))
    }
  }

  def checkValidAccountTypeAndEnrolForPT(
    expectedAccountType: AccountTypes.Value
  )(implicit
    request: DataRequest[_],
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): TEAFResult[Unit] =
    checkAccessAllowedForPage(List(expectedAccountType)) match {
      case Right(_)    => silentAssignmentService.enrolUser()(request, implicitly, implicitly)
      case Left(error) => EitherT.left(Future.successful(error))
    }

  def checkAccessAllowedForPage(validAccountTypes: List[AccountTypes.Value])(implicit
    request: DataRequest[_]
  ): Either[TaxEnrolmentAssignmentErrors, AccountTypes.Value] =
    (checkValidAccountType(validAccountTypes), checkPTEnrolmentDoesNotExist) match {
      case (Right(accountType), Right(_)) => Right(accountType)
      case (Right(_), Left(error))        => Left(error)
      case _ =>
        Left(
          IncorrectUserType(
            request.requestWithUserDetailsFromSessionAndMongo.get.accountDetailsFromMongo.redirectUrl,
            request.requestWithUserDetailsFromSessionAndMongo.get.accountDetailsFromMongo.accountType
          )
        )
    }

  def checkValidAccountType(validAccountTypes: List[AccountTypes.Value])(implicit
    request: DataRequest[_]
  ): Either[TaxEnrolmentAssignmentErrors, AccountTypes.Value] =
    if (
      validAccountTypes.contains(
        request.requestWithUserDetailsFromSessionAndMongo.get.accountDetailsFromMongo.accountType
      )
    ) {
      Right(request.requestWithUserDetailsFromSessionAndMongo.get.accountDetailsFromMongo.accountType)
    } else {
      Left(
        IncorrectUserType(
          request.requestWithUserDetailsFromSessionAndMongo.get.accountDetailsFromMongo.redirectUrl,
          request.requestWithUserDetailsFromSessionAndMongo.get.accountDetailsFromMongo.accountType
        )
      )
    }

  private def checkPTEnrolmentDoesNotExist(implicit
    request: DataRequest[_]
  ): Either[TaxEnrolmentAssignmentErrors, Unit] = {
    val hasPTEnrolment = request.userDetails.hasPTEnrolment

    if (hasPTEnrolment) {
      Left(
        UnexpectedPTEnrolment(request.requestWithUserDetailsFromSessionAndMongo.get.accountDetailsFromMongo.accountType)
      )
    } else {
      Right((): Unit)
    }
  }

  def getCurrentAndPTAAndSAIfExistsForUser(implicit
    request: DataRequest[_],
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): TEAFResult[PTEnrolmentOnOtherAccount] =
    getSAForPTAlreadyEnrolledDetails.map { model =>
      PTEnrolmentOnOtherAccount(
        model.currentAccountDetails,
        model.ptAccountDetails,
        model.saAccountDetails.map(_.userId)
      )
    }

  private def getSAForPTAlreadyEnrolledDetails(implicit
    request: DataRequest[_],
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): TEAFResult[CADetailsPTADetailsSADetailsIfExists] = {

    lazy val optSACredentialId: TEAFResult[Option[String]] =
      (
        request.userDetails.hasSAEnrolment,
        request.requestWithUserDetailsFromSessionAndMongo.get.accountDetailsFromMongo.optUserAssignedSA
      ) match {
        case (true, _) =>
          EitherT.right[TaxEnrolmentAssignmentErrors](
            Future.successful(Some(request.userDetails.credId))
          )
        case (false, Some(usersAssignedEnrolment)) =>
          EitherT.right[TaxEnrolmentAssignmentErrors](Future.successful(usersAssignedEnrolment.enrolledCredential))
        case (false, None) =>
          eacdService
            .getUsersAssignedSAEnrolment(request, implicitly, implicitly)
            .map(user => user.enrolledCredential)
      }

    def getSAAccountDetails(
      currentAccountDetails: AccountDetails,
      ptAccountDetails: AccountDetails
    ): TEAFResult[Option[AccountDetails]] =
      optSACredentialId.flatMap {
        case Some(credId) if credId == currentAccountDetails.credId =>
          EitherT.right[TaxEnrolmentAssignmentErrors](Future.successful(Some(currentAccountDetails)))
        case Some(credId) if credId == ptAccountDetails.credId =>
          EitherT.right[TaxEnrolmentAssignmentErrors](Future.successful(Some(ptAccountDetails)))
        case Some(credId) =>
          usersGroupSearchService.getAccountDetails(credId)(implicitly, implicitly, request).map(Some(_))
        case None =>
          EitherT.right[TaxEnrolmentAssignmentErrors](Future.successful(Option.empty[AccountDetails]))
      }

    for {
      _ <- EitherT(Future(checkAccessAllowedForPage(List(PT_ASSIGNED_TO_OTHER_USER))))
      currentAccountDetails <-
        usersGroupSearchService
          .getAccountDetails(request.userDetails.credId)(implicitly, implicitly, request)
      ptAccountDetails        <- getPTCredentialDetails
      saOnOtherAccountDetails <- getSAAccountDetails(currentAccountDetails, ptAccountDetails)
    } yield CADetailsPTADetailsSADetailsIfExists(
      currentAccountDetails,
      ptAccountDetails,
      saOnOtherAccountDetails
    )
  }

}
