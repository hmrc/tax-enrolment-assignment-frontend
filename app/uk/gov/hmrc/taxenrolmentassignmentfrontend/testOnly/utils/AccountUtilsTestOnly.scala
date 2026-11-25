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

package uk.gov.hmrc.taxenrolmentassignmentfrontend.testOnly.utils

import cats.data.EitherT
import cats.implicits.toTraverseOps
import play.api.Logging
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.service.TEAFResult
import uk.gov.hmrc.taxenrolmentassignmentfrontend.connectors.UsersGroupsSearchConnector
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.TaxEnrolmentAssignmentErrors
import uk.gov.hmrc.taxenrolmentassignmentfrontend.testOnly.connectors.*
import uk.gov.hmrc.taxenrolmentassignmentfrontend.testOnly.models.AccountDetailsTestOnly
import uk.gov.hmrc.taxenrolmentassignmentfrontend.testOnly.services.EnrolmentStoreServiceTestOnly

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AccountUtilsTestOnly @Inject() (
  enrolmentStoreServiceTestOnly: EnrolmentStoreServiceTestOnly,
  identityVerificationConnectorTestOnly: IdentityVerificationConnectorTestOnly,
  basStubsConnectorTestOnly: BasStubsConnectorTestOnly,
  oneLoginStubConnectorTestOnly: OneLoginStubConnectorTestOnly,
  usersGroupsSearchConnector: UsersGroupsSearchConnector
)(implicit ec: ExecutionContext)
    extends Logging {

  def deleteAccountsLinkedToNino(account: AccountDetailsTestOnly)(implicit hc: HeaderCarrier): TEAFResult[Unit] =
    EitherT[Future, TaxEnrolmentAssignmentErrors, List[String]](
      usersGroupsSearchConnector.getAllCredIdsByNino(account.nino.nino).value.map {
        case Right(credIds) => Right(credIds.map(_.credId).distinct.toList)
        case Left(error)    =>
          logger.warn(s"Could not look up existing accounts for NINO ${account.nino.nino}, skipping clean-up: $error")
          Right(List.empty[String])
      }
    )
      .flatMap { credIds =>
        credIds
          .map(credId => deleteAccountDetails(account.copy(user = account.user.copy(credId = credId))))
          .sequence
          .map(_ => ())
      }

  def deleteAccountDetails(account: AccountDetailsTestOnly)(implicit hc: HeaderCarrier): TEAFResult[Unit] =
    for {
      // delete account from IPAC
      eacdId <- oneLoginStubConnectorTestOnly.getAccount(account.user.credId)
      _      <- eacdId.map(id => oneLoginStubConnectorTestOnly.deleteAccount(id)).sequence
      /*  Overwrite the account first
          Sometimes the account is not of type individual causing a later call to fail
       */
      _      <- basStubsConnectorTestOnly.putAccount(account)
      // delete identity-verification data - Link nino / confidence level to account and holds mfa details
      _      <- identityVerificationConnectorTestOnly.deleteCredId(account.user.credId)
      // delete enrolment-store data
      _      <- account.enrolments.map(enrolmentStoreServiceTestOnly.deallocateEnrolmentFromGroups(_)).sequence
      _      <- account.enrolments.map(enrolmentStoreServiceTestOnly.deallocateEnrolmentFromUsers(_)).sequence
      _      <- account.enrolments.map(enrolmentStoreServiceTestOnly.deleteEnrolment(_)).sequence
      // Search and delete other known facts that might remains after the step above
      _      <- enrolmentStoreServiceTestOnly.deleteAllKnownFactsForNino(account.nino)
      _      <- enrolmentStoreServiceTestOnly.deleteGroup(account.groupId)
      _      <- enrolmentStoreServiceTestOnly.deleteAccount(account.groupId)
      _      <- enrolmentStoreServiceTestOnly.deallocateEnrolmentsFromGroup(account.groupId)
      _      <- enrolmentStoreServiceTestOnly.deallocateEnrolmentsFromUser(account.user.credId)
      // delete bas-stub data - The users accounts
      _      <- basStubsConnectorTestOnly.deleteAdditionalFactors(account.user.credId)
      _      <- basStubsConnectorTestOnly.deleteAccount(account)
    } yield ()

  def insertAccountDetails(account: AccountDetailsTestOnly)(implicit hc: HeaderCarrier): TEAFResult[Unit] =
    if (account.identityProviderType == "ONE_LOGIN") {
      for {
        // Insert enrolment-store data
        _ <- enrolmentStoreServiceTestOnly.insertAccount(account)
        _ <- account.enrolments.map(enrolment => enrolmentStoreServiceTestOnly.upsertEnrolment(enrolment)).sequence
        _ <-
          account.enrolments
            .map(enrolment =>
              enrolmentStoreServiceTestOnly.addEnrolmentToGroup(account.groupId, account.user.credId, enrolment)
            )
            .sequence
        _ <- oneLoginStubConnectorTestOnly.postAccount(account)
      } yield ()
    } else {
      for {
        // Insert enrolment-store data
        _ <- enrolmentStoreServiceTestOnly.insertAccount(account)
        _ <- account.enrolments.map(enrolment => enrolmentStoreServiceTestOnly.upsertEnrolment(enrolment)).sequence
        _ <-
          account.enrolments
            .map(enrolment =>
              enrolmentStoreServiceTestOnly.addEnrolmentToGroup(account.groupId, account.user.credId, enrolment)
            )
            .sequence
        // insert identity-verification data - Link nino / confidence level to account and holds mfa details
        _ <- identityVerificationConnectorTestOnly.insertCredId(account.user.credId, account.nino)
        // insert bas-stubs data - The users accounts
        _ <- basStubsConnectorTestOnly.putAccount(account)
        _ <- basStubsConnectorTestOnly.putAdditionalFactors(account)
      } yield ()
    }

}
