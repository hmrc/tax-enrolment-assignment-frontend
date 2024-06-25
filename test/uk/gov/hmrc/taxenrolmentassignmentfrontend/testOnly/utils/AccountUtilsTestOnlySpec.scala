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
import org.scalatest.matchers.must.Matchers._
import play.api.Application
import play.api.inject.bind
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
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
      (mockBasStubsConnectorTestOnly
        .putAccount(_: AccountDetailsTestOnly)(_: HeaderCarrier))
        .expects(account, *)
        .returning(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](()))

      (mockBasStubsConnectorTestOnly
        .deleteAccount(_: AccountDetailsTestOnly)(_: HeaderCarrier))
        .expects(account, *)
        .returning(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](()))

      (mockIdentityVerificationConnectorTestOnly
        .deleteCredId(_: String)(_: HeaderCarrier))
        .expects(credId, *)
        .returning(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](()))

      account.enrolments.foreach { enrolment =>
        (mockEnrolmentStoreServiceTestOnly
          .deallocateEnrolmentFromGroups(_: EnrolmentDetailsTestOnly)(_: HeaderCarrier))
          .expects(enrolment, *)
          .returning(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](()))
        (mockEnrolmentStoreServiceTestOnly
          .deallocateEnrolmentFromUsers(_: EnrolmentDetailsTestOnly)(_: HeaderCarrier))
          .expects(enrolment, *)
          .returning(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](()))
        (mockEnrolmentStoreServiceTestOnly
          .deleteEnrolment(_: EnrolmentDetailsTestOnly)(_: HeaderCarrier))
          .expects(enrolment, *)
          .returning(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](()))
      }

      (mockEnrolmentStoreServiceTestOnly
        .deleteAllKnownFactsForNino(_: Nino)(_: HeaderCarrier))
        .expects(nino, *)
        .returning(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](()))

      (mockEnrolmentStoreServiceTestOnly
        .deleteGroup(_: String)(_: HeaderCarrier))
        .expects(groupId, *)
        .returning(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](()))

      (mockEnrolmentStoreServiceTestOnly
        .deleteAccount(_: String)(_: HeaderCarrier))
        .expects(groupId, *)
        .returning(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](()))

      (mockEnrolmentStoreServiceTestOnly
        .deallocateEnrolmentsFromGroup(_: String)(_: HeaderCarrier))
        .expects(groupId, *)
        .returning(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](()))

      (mockEnrolmentStoreServiceTestOnly
        .deallocateEnrolmentsFromUser(_: String)(_: HeaderCarrier))
        .expects(credId, *)
        .returning(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](()))

      (mockBasStubsConnectorTestOnly
        .deleteAdditionalFactors(_: String)(_: HeaderCarrier))
        .expects(credId, *)
        .returning(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](()))

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

      (mockEnrolmentStoreServiceTestOnly
        .insertAccount(_: AccountDetailsTestOnly)(_: HeaderCarrier))
        .expects(account, *)
        .returning(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](()))

      account.enrolments.foreach { enrolment =>
        (mockEnrolmentStoreServiceTestOnly
          .upsertEnrolment(_: EnrolmentDetailsTestOnly)(_: HeaderCarrier))
          .expects(enrolment, *)
          .returning(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](()))

        (mockEnrolmentStoreServiceTestOnly
          .addEnrolmentToGroup(_: String, _: String, _: EnrolmentDetailsTestOnly)(_: HeaderCarrier))
          .expects(groupId, credId, enrolment, *)
          .returning(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](()))

      }

      (mockIdentityVerificationConnectorTestOnly
        .insertCredId(_: String, _: Nino)(_: HeaderCarrier))
        .expects(credId, nino, *)
        .returning(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](()))

      (mockBasStubsConnectorTestOnly
        .putAccount(_: AccountDetailsTestOnly)(_: HeaderCarrier))
        .expects(account, *)
        .returning(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](()))

      (mockBasStubsConnectorTestOnly
        .putAdditionalFactors(_: AccountDetailsTestOnly)(_: HeaderCarrier))
        .expects(account, *)
        .returning(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](()))

      val result = sut.insertAccountDetails(account).value.futureValue

      result mustBe Right(())
    }

  }

}
