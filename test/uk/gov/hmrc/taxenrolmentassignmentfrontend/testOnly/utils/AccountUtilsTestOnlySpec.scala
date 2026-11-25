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
import org.mockito.ArgumentMatchers.{any, eq as ameq}
import org.mockito.Mockito.{never, verify, when}
import org.scalatest.matchers.must.Matchers.*
import play.api.Application
import play.api.inject.bind
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.taxenrolmentassignmentfrontend.connectors.UsersGroupsSearchConnector
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.{TaxEnrolmentAssignmentErrors, UnexpectedResponseFromUsersGroupsSearch}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.BaseSpec
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.{AdditonalFactors, IdentifiersOrVerifiers, IdentityProviderWithCredId, ONE_LOGIN, SCP}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.testOnly.connectors.{BasStubsConnectorTestOnly, IdentityVerificationConnectorTestOnly, OneLoginStubConnectorTestOnly}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.testOnly.models.{AccountDetailsTestOnly, EnrolmentDetailsTestOnly, UserTestOnly}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.testOnly.services.EnrolmentStoreServiceTestOnly

import scala.concurrent.Future

class AccountUtilsTestOnlySpec extends BaseSpec {

  lazy val mockEnrolmentStoreServiceTestOnly: EnrolmentStoreServiceTestOnly                 = mock[EnrolmentStoreServiceTestOnly]
  lazy val mockIdentityVerificationConnectorTestOnly: IdentityVerificationConnectorTestOnly =
    mock[IdentityVerificationConnectorTestOnly]
  lazy val mockBasStubsConnectorTestOnly: BasStubsConnectorTestOnly                         = mock[BasStubsConnectorTestOnly]
  lazy val mockOneLoginStubConnectorTestOnly: OneLoginStubConnectorTestOnly                 = mock[OneLoginStubConnectorTestOnly]
  lazy val mockUsersGroupsSearchConnector: UsersGroupsSearchConnector                       = mock[UsersGroupsSearchConnector]

  override implicit lazy val app: Application = localGuiceApplicationBuilder()
    .overrides(
      bind[EnrolmentStoreServiceTestOnly].toInstance(mockEnrolmentStoreServiceTestOnly),
      bind[IdentityVerificationConnectorTestOnly].toInstance(mockIdentityVerificationConnectorTestOnly),
      bind[BasStubsConnectorTestOnly].toInstance(mockBasStubsConnectorTestOnly),
      bind[OneLoginStubConnectorTestOnly].toInstance(mockOneLoginStubConnectorTestOnly),
      bind[UsersGroupsSearchConnector].toInstance(mockUsersGroupsSearchConnector)
    )
    .build()

  lazy val sut: AccountUtilsTestOnly = app.injector.instanceOf[AccountUtilsTestOnly]

  val nino: Nino = generateNino

  "deleteAccountDetails" must {
    val credId               = "credId"
    val groupId              = "groupId"
    val identityProviderType = "SCP"
    val enrolment            = EnrolmentDetailsTestOnly(
      "serviceName",
      IdentifiersOrVerifiers("KEY", "VALUE"),
      List(IdentifiersOrVerifiers("KEY2", "VALUE2")),
      "enrolmentFriendlyName",
      "state",
      "enrolmentType"
    )
    val account              = AccountDetailsTestOnly(
      identityProviderType,
      groupId,
      nino,
      "Individual",
      UserTestOnly(credId, "name", "email"),
      List(enrolment),
      List(AdditonalFactors("factorType"))
    )
    val caUserId             = "12345"

    "delete all details for GG user" in {
      when(mockBasStubsConnectorTestOnly.putAccount(account))
        .thenReturn(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](()))

      when(mockBasStubsConnectorTestOnly.deleteAccount(account))
        .thenReturn(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](()))

      when(mockIdentityVerificationConnectorTestOnly.deleteCredId(credId))
        .thenReturn(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](()))

      account.enrolments.foreach { enrolment =>
        when(mockEnrolmentStoreServiceTestOnly.deallocateEnrolmentFromGroups(enrolment))
          .thenReturn(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](()))

        when(mockEnrolmentStoreServiceTestOnly.deallocateEnrolmentFromUsers(enrolment))
          .thenReturn(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](()))

        when(mockEnrolmentStoreServiceTestOnly.deleteEnrolment(enrolment))
          .thenReturn(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](()))
      }

      when(mockEnrolmentStoreServiceTestOnly.deleteAllKnownFactsForNino(nino))
        .thenReturn(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](()))

      when(mockEnrolmentStoreServiceTestOnly.deleteGroup(groupId))
        .thenReturn(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](()))

      when(mockEnrolmentStoreServiceTestOnly.deleteAccount(groupId))
        .thenReturn(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](()))

      when(mockEnrolmentStoreServiceTestOnly.deallocateEnrolmentsFromGroup(groupId))
        .thenReturn(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](()))

      when(mockEnrolmentStoreServiceTestOnly.deallocateEnrolmentsFromUser(credId))
        .thenReturn(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](()))

      when(mockBasStubsConnectorTestOnly.deleteAdditionalFactors(credId))
        .thenReturn(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](()))

      account.enrolments.foreach { enrolment =>
        when(mockEnrolmentStoreServiceTestOnly.deallocateEnrolmentFromGroups(enrolment))
          .thenReturn(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](()))

        when(mockEnrolmentStoreServiceTestOnly.deallocateEnrolmentsFromGroup(groupId))
          .thenReturn(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](()))

        when(mockEnrolmentStoreServiceTestOnly.deleteEnrolment(enrolment))
          .thenReturn(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](()))
      }

      when(mockEnrolmentStoreServiceTestOnly.deleteAccount(groupId))
        .thenReturn(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](()))

      when(mockOneLoginStubConnectorTestOnly.getAccount(credId))
        .thenReturn(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](Some(caUserId)))

      when(mockOneLoginStubConnectorTestOnly.deleteAccount(caUserId))
        .thenReturn(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](()))

      when(mockEnrolmentStoreServiceTestOnly.deleteAllKnownFactsForNino(nino))
        .thenReturn(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](()))

      when(mockEnrolmentStoreServiceTestOnly.deleteGroup(groupId))
        .thenReturn(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](()))

      when(mockEnrolmentStoreServiceTestOnly.deallocateEnrolmentsFromUser(credId))
        .thenReturn(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](()))

      val result = sut.deleteAccountDetails(account).value.futureValue

      result mustBe Right(())
    }

  }

  "insertAccountDetails" must {
    val credId               = "credId"
    val groupId              = "groupId"
    val identityProviderType = "SCP"
    val enrolment            = EnrolmentDetailsTestOnly(
      "serviceName",
      IdentifiersOrVerifiers("KEY", "VALUE"),
      List(IdentifiersOrVerifiers("KEY2", "VALUE2")),
      "enrolmentFriendlyName",
      "state",
      "enrolmentType"
    )
    val account              = AccountDetailsTestOnly(
      identityProviderType,
      groupId,
      nino,
      "Individual",
      UserTestOnly(credId, "name", "email"),
      List(enrolment),
      List(AdditonalFactors("factorType"))
    )

    "insert all details for GG user" in {

      when(mockEnrolmentStoreServiceTestOnly.insertAccount(account))
        .thenReturn(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](()))

      account.enrolments.foreach { enrolment =>
        when(mockEnrolmentStoreServiceTestOnly.upsertEnrolment(enrolment))
          .thenReturn(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](()))

        when(mockEnrolmentStoreServiceTestOnly.addEnrolmentToGroup(groupId, credId, enrolment))
          .thenReturn(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](()))
      }

      when(mockIdentityVerificationConnectorTestOnly.insertCredId(credId, nino))
        .thenReturn(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](()))

      when(mockBasStubsConnectorTestOnly.putAccount(account))
        .thenReturn(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](()))

      when(mockBasStubsConnectorTestOnly.putAdditionalFactors(account))
        .thenReturn(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](()))

      val result = sut.insertAccountDetails(account).value.futureValue

      result mustBe Right(())
    }

    "insert all details for OLFG user" in {
      val olfgAccount = account.copy(identityProviderType = "ONE_LOGIN")

      when(mockEnrolmentStoreServiceTestOnly.insertAccount(olfgAccount))
        .thenReturn(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](()))

      account.enrolments.foreach { enrolment =>
        when(mockEnrolmentStoreServiceTestOnly.upsertEnrolment(enrolment))
          .thenReturn(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](()))

        when(mockEnrolmentStoreServiceTestOnly.addEnrolmentToGroup(groupId, credId, enrolment))
          .thenReturn(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](()))
      }

      when(mockOneLoginStubConnectorTestOnly.postAccount(olfgAccount))
        .thenReturn(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors]("centralAuthUserId"))

      val result = sut.insertAccountDetails(olfgAccount).value.futureValue

      result mustBe Right(())
    }

  }

  "deleteAccountsLinkedToNino" must {
    val linkedNino    = generateNino
    val linkedGroupId = "linkedGroupId"
    val account       = AccountDetailsTestOnly(
      "ONE_LOGIN",
      linkedGroupId,
      linkedNino,
      "Individual",
      UserTestOnly("scenarioCredId", "name", "email"),
      List.empty,
      List(AdditonalFactors("factorType"))
    )

    def stubDeleteAccountDetails(credId: String): Unit = {
      val accountWithCredId = account.copy(user = account.user.copy(credId = credId))
      when(mockOneLoginStubConnectorTestOnly.getAccount(credId))
        .thenReturn(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](Option.empty[String]))
      when(mockBasStubsConnectorTestOnly.putAccount(accountWithCredId))
        .thenReturn(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](()))
      when(mockIdentityVerificationConnectorTestOnly.deleteCredId(credId))
        .thenReturn(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](()))
      when(mockEnrolmentStoreServiceTestOnly.deleteAllKnownFactsForNino(linkedNino))
        .thenReturn(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](()))
      when(mockEnrolmentStoreServiceTestOnly.deleteGroup(linkedGroupId))
        .thenReturn(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](()))
      when(mockEnrolmentStoreServiceTestOnly.deleteAccount(linkedGroupId))
        .thenReturn(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](()))
      when(mockEnrolmentStoreServiceTestOnly.deallocateEnrolmentsFromGroup(linkedGroupId))
        .thenReturn(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](()))
      when(mockEnrolmentStoreServiceTestOnly.deallocateEnrolmentsFromUser(credId))
        .thenReturn(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](()))
      when(mockBasStubsConnectorTestOnly.deleteAdditionalFactors(credId))
        .thenReturn(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](()))
      when(mockBasStubsConnectorTestOnly.deleteAccount(accountWithCredId))
        .thenReturn(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](()))
    }

    "delete the details of every account linked to the NINO" in {
      when(mockUsersGroupsSearchConnector.getAllCredIdsByNino(ameq(linkedNino.nino))(any(), any()))
        .thenReturn(
          EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](
            Seq(
              IdentityProviderWithCredId("linkedCredId1", ONE_LOGIN),
              IdentityProviderWithCredId("linkedCredId2", SCP),
              IdentityProviderWithCredId("linkedCredId1", ONE_LOGIN)
            )
          )
        )
      stubDeleteAccountDetails("linkedCredId1")
      stubDeleteAccountDetails("linkedCredId2")

      val result = sut.deleteAccountsLinkedToNino(account).value.futureValue

      result mustBe Right(())
      verify(mockIdentityVerificationConnectorTestOnly).deleteCredId("linkedCredId1")
      verify(mockIdentityVerificationConnectorTestOnly).deleteCredId("linkedCredId2")
      verify(mockIdentityVerificationConnectorTestOnly, never()).deleteCredId("scenarioCredId")
    }

    "skip the clean-up and succeed when the NINO lookup fails" in {
      val otherNino    = generateNino
      val otherAccount = account.copy(nino = otherNino)

      when(mockUsersGroupsSearchConnector.getAllCredIdsByNino(ameq(otherNino.nino))(any(), any()))
        .thenReturn(EitherT.leftT[Future, Seq[IdentityProviderWithCredId]](UnexpectedResponseFromUsersGroupsSearch))

      val result = sut.deleteAccountsLinkedToNino(otherAccount).value.futureValue

      result mustBe Right(())
    }
  }
}
