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
import org.mockito.MockitoSugar.{mock, when}
import org.scalatest.matchers.must.Matchers._
import play.api.Application
import play.api.inject.bind
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.TaxEnrolmentAssignmentErrors
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.BaseSpec
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.{AdditonalFactors, IdentifiersOrVerifiers}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.testOnly.connectors.{BasStubsConnectorTestOnly, IdentityVerificationConnectorTestOnly}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.testOnly.models.{AccountDetailsTestOnly, EnrolmentDetailsTestOnly, UserTestOnly}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.testOnly.services.EnrolmentStoreServiceTestOnly

import scala.concurrent.Future

class AccountUtilsTestOnlySpec extends BaseSpec {

  lazy val mockEnrolmentStoreServiceTestOnly: EnrolmentStoreServiceTestOnly = mock[EnrolmentStoreServiceTestOnly]
  lazy val mockIdentityVerificationConnectorTestOnly: IdentityVerificationConnectorTestOnly =
    mock[IdentityVerificationConnectorTestOnly]
  lazy val mockBasStubsConnectorTestOnly: BasStubsConnectorTestOnly = mock[BasStubsConnectorTestOnly]

  override implicit lazy val app: Application = localGuiceApplicationBuilder()
    .overrides(
      bind[EnrolmentStoreServiceTestOnly].toInstance(mockEnrolmentStoreServiceTestOnly),
      bind[IdentityVerificationConnectorTestOnly].toInstance(mockIdentityVerificationConnectorTestOnly),
      bind[BasStubsConnectorTestOnly].toInstance(mockBasStubsConnectorTestOnly)
    )
    .build()

  lazy val sut: AccountUtilsTestOnly = app.injector.instanceOf[AccountUtilsTestOnly]

  val nino: Nino = generateNino

  "deleteAccountDetails" must {
    val credId = "credId"
    val groupId = "groupId"
    val identityProviderType = "SCP"
    val enrolment = EnrolmentDetailsTestOnly(
      "serviceName",
      IdentifiersOrVerifiers("KEY", "VALUE"),
      List(IdentifiersOrVerifiers("KEY2", "VALUE2")),
      "enrolmentFriendlyName",
      "state",
      "enrolmentType"
    )
    val account = AccountDetailsTestOnly(
      identityProviderType,
      groupId,
      nino,
      "Individual",
      UserTestOnly(credId, "name", "email"),
      List(enrolment),
      List(AdditonalFactors("factorType"))
    )

    "delete all details" in {
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

      val result = sut.deleteAccountDetails(account).value.futureValue

      result mustBe Right(())
    }

  }

  "insertAccountDetails" must {
    val credId = "credId"
    val groupId = "groupId"
    val identityProviderType = "SCP"
    val enrolment = EnrolmentDetailsTestOnly(
      "serviceName",
      IdentifiersOrVerifiers("KEY", "VALUE"),
      List(IdentifiersOrVerifiers("KEY2", "VALUE2")),
      "enrolmentFriendlyName",
      "state",
      "enrolmentType"
    )
    val account = AccountDetailsTestOnly(
      identityProviderType,
      groupId,
      nino,
      "Individual",
      UserTestOnly(credId, "name", "email"),
      List(enrolment),
      List(AdditonalFactors("factorType"))
    )

    "insert all details" in {

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

  }

}
