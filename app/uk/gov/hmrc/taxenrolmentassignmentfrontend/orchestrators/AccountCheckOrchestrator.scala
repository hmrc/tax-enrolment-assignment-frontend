package uk.gov.hmrc.taxenrolmentassignmentfrontend.orchestrators

import cats.data.EitherT
import cats.implicits._
import com.google.inject.{Inject, Singleton}
import play.api.mvc.AnyContent
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.service.TEAFResult
import uk.gov.hmrc.taxenrolmentassignmentfrontend._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.connectors.{
  EACDConnector,
  IVConnector
}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.auth.{
  RequestWithUserDetails,
  UserDetailsFromSession
}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.services.SilentAssignmentService

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AccountCheckOrchestrator @Inject()(
  eacdConnector: EACDConnector,
  silentAssignmentService: SilentAssignmentService
) {

  def getAccountType(userDetails: UserDetailsFromSession)(
    implicit ec: ExecutionContext,
    hc: HeaderCarrier,
    requestWithUserDetails: RequestWithUserDetails[AnyContent]
  ): TEAFResult[AccountTypes] = EitherT {

    checkUsersWithPTEnrolmentAlreadyAssigned(userDetails).value.flatMap {
      case Right(Some(accountTypes)) => Future.successful(Right(accountTypes))
      case Right(None)               => checkIfMultipleAccounts.value
      case Left(error)               => Future.successful(Left(error))
    }
  }

  private def checkUsersWithPTEnrolmentAlreadyAssigned(
    sessionUserDetails: UserDetailsFromSession
  )(implicit hc: HeaderCarrier,
    ec: ExecutionContext): TEAFResult[Option[AccountTypes]] = {
    if (sessionUserDetails.hasPTEnrolment) {
      EitherT.right(Future.successful(Some(PT_ASSIGNED_TO_CURRENT_USER)))
    } else {
      eacdConnector
        .getUsersWithPTEnrolment(sessionUserDetails.nino)
        .map(
          optUsers =>
            optUsers.fold[Option[AccountTypes]](None) { users =>
              users.principalUserIds.headOption.map { ptCredId =>
                if (ptCredId == sessionUserDetails.credId) {
                  PT_ASSIGNED_TO_CURRENT_USER
                } else {
                  PT_ASSIGNED_TO_OTHER_USER
                }
              }
          }
        )
    }
  }

  private def checkIfMultipleAccounts(
    implicit requestWithUserDetails: RequestWithUserDetails[AnyContent],
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): TEAFResult[AccountTypes] = {
    silentAssignmentService.getOtherAccountsWithPTAAccess.map(
      otherCreds =>
        if (otherCreds.isEmpty) {
          SINGLE_ACCOUNT
        } else {
          MULTIPLE_ACCOUNTS
      }
    )
  }

}
