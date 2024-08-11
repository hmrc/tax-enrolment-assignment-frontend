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

package uk.gov.hmrc.taxenrolmentassignmentfrontend.testOnly.services

import cats.data.EitherT
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.MockitoSugar.{mock, when}
import org.scalatest.matchers.must.Matchers._
import play.api.Application
import play.api.inject.bind
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.{InvalidRedirectUrl, TaxEnrolmentAssignmentErrors, UnexpectedError, UnexpectedResponseFromEACD}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.BaseSpec
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.TestData.{NINO, UsersAssignedEnrolment1}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.IdentifiersOrVerifiers
import uk.gov.hmrc.taxenrolmentassignmentfrontend.testOnly.connectors.{EnrolmentStoreConnectorTestOnly, EnrolmentStoreStubConnectorTestOnly}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.testOnly.models.{AccountDetailsTestOnly, EnrolmentDetailsTestOnly, UserTestOnly}

import scala.concurrent.Future

class EnrolmentStoreServiceTestOnlySpec extends BaseSpec {

  val accountDetailsTestOnly: AccountDetailsTestOnly = AccountDetailsTestOnly(
    "SCP",
    "groupId",
    generateNino,
    "Individual",
    UserTestOnly("credId", "name", "email"),
    List.empty,
    List.empty
  )

  lazy val mockEnrolmentStoreStubConnectorTestOnly: EnrolmentStoreStubConnectorTestOnly =
    mock[EnrolmentStoreStubConnectorTestOnly]
  lazy val mockEnrolmentStoreConnectorTestOnly: EnrolmentStoreConnectorTestOnly = mock[EnrolmentStoreConnectorTestOnly]

  override implicit lazy val app: Application = localGuiceApplicationBuilder()
    .overrides(
      bind[EnrolmentStoreStubConnectorTestOnly].toInstance(mockEnrolmentStoreStubConnectorTestOnly),
      bind[EnrolmentStoreConnectorTestOnly].toInstance(mockEnrolmentStoreConnectorTestOnly)
    )
    .build()

  lazy val sut: EnrolmentStoreServiceTestOnly = app.injector.instanceOf[EnrolmentStoreServiceTestOnly]

  "deleteAccount" must {
    val groupId = "groupId"

    "delete account" in {
      when(mockEnrolmentStoreStubConnectorTestOnly.deleteStubAccount(groupId))
        .thenReturn(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](()))

      val result = sut.deleteAccount(groupId).value.futureValue

      result mustBe Right(())
    }

    "return Left" in {
      when(mockEnrolmentStoreStubConnectorTestOnly.deleteStubAccount(groupId))
        .thenReturn(EitherT.leftT[Future, Unit](InvalidRedirectUrl))

      val result = sut.deleteAccount(groupId).value.futureValue

      result mustBe a[Left[TaxEnrolmentAssignmentErrors, _]]
    }
  }

  "deleteGroup" must {
    val groupId = "groupId"

    "delete group" in {

      when(mockEnrolmentStoreConnectorTestOnly.deleteGroup(groupId))
        .thenReturn(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](()))

      val result = sut.deleteGroup(groupId).value.futureValue

      result mustBe Right(())
    }

    "return Left" in {
      when(mockEnrolmentStoreConnectorTestOnly.deleteGroup(groupId))
        .thenReturn(EitherT.leftT[Future, Unit](InvalidRedirectUrl))

      val result = sut.deleteGroup(groupId).value.futureValue

      result mustBe a[Left[TaxEnrolmentAssignmentErrors, _]]
    }
  }

  "insertAccount" must {
    "insert account" in {
      when(mockEnrolmentStoreStubConnectorTestOnly.addStubAccount(accountDetailsTestOnly))
        .thenReturn(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](()))

      val result = sut.insertAccount(accountDetailsTestOnly).value.futureValue

      result mustBe Right(())
    }

    "return Left" in {
      when(mockEnrolmentStoreStubConnectorTestOnly.addStubAccount(accountDetailsTestOnly))
        .thenReturn(EitherT.leftT[Future, Unit](InvalidRedirectUrl))

      val result = sut.insertAccount(accountDetailsTestOnly).value.futureValue

      result mustBe a[Left[TaxEnrolmentAssignmentErrors, _]]
    }
  }

  "deallocateEnrolmentFromGroups" must {
    val enrolment = EnrolmentDetailsTestOnly(
      "serviceName",
      IdentifiersOrVerifiers("KEY", "VALUE"),
      List(IdentifiersOrVerifiers("KEY2", "VALUE2")),
      "enrolment Friendly name",
      "state",
      "type"
    )

    "deallocate enrolment" in {
      val enrolmentKey = s"${enrolment.serviceName}~${enrolment.identifiers.key}~${enrolment.identifiers.value}"
      when(mockEnrolmentStoreConnectorTestOnly.getGroupsFromEnrolment(enrolmentKey))
        .thenReturn(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](List("a", "b")))

      when(mockEnrolmentStoreConnectorTestOnly.deleteEnrolmentFromGroup(enrolmentKey, "a"))
        .thenReturn(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](()))

      when(mockEnrolmentStoreConnectorTestOnly.deleteEnrolmentFromGroup(enrolmentKey, "b"))
        .thenReturn(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](()))

      val result = sut.deallocateEnrolmentFromGroups(enrolment).value.futureValue

      result mustBe a[Right[_, Unit]]
    }

    "return Left" when {
      "deleteEnrolmentFromGroup is failing" in {
        val enrolmentKey = s"${enrolment.serviceName}~${enrolment.identifiers.key}~${enrolment.identifiers.value}"

        when(mockEnrolmentStoreConnectorTestOnly.getGroupsFromEnrolment(enrolmentKey))
          .thenReturn(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](List("a", "b")))

        when(mockEnrolmentStoreConnectorTestOnly.deleteEnrolmentFromGroup(enrolmentKey, "a"))
          .thenReturn(EitherT.leftT[Future, Unit](UnexpectedResponseFromEACD))

        when(mockEnrolmentStoreConnectorTestOnly.deleteEnrolmentFromGroup(enrolmentKey, "b"))
          .thenReturn(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](()))

        val result = sut.deallocateEnrolmentFromGroups(enrolment).value.futureValue

        result mustBe a[Left[TaxEnrolmentAssignmentErrors, _]]
      }

      "getGroupsFromEnrolment is failing" in {
        val enrolmentKey = s"${enrolment.serviceName}~${enrolment.identifiers.key}~${enrolment.identifiers.value}"

        when(mockEnrolmentStoreConnectorTestOnly.getGroupsFromEnrolment(enrolmentKey))
          .thenReturn(EitherT.leftT[Future, List[String]](UnexpectedResponseFromEACD))

        val result = sut.deallocateEnrolmentFromGroups(enrolment).value.futureValue

        result mustBe a[Left[TaxEnrolmentAssignmentErrors, _]]
      }
    }
  }

  "deallocateEnrolmentFromUsers" must {
    val enrolment = EnrolmentDetailsTestOnly(
      "serviceName",
      IdentifiersOrVerifiers("KEY", "VALUE"),
      List(IdentifiersOrVerifiers("KEY2", "VALUE2")),
      "enrolment Friendly name",
      "state",
      "type"
    )

    "deallocate enrolment" in {
      val enrolmentKey = s"${enrolment.serviceName}~${enrolment.identifiers.key}~${enrolment.identifiers.value}"

      when(mockEnrolmentStoreConnectorTestOnly.getUsersFromEnrolment(enrolmentKey))
        .thenReturn(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](List("a", "b")))

      when(mockEnrolmentStoreConnectorTestOnly.deleteEnrolmentFromUser(enrolmentKey, "a"))
        .thenReturn(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](()))

      when(mockEnrolmentStoreConnectorTestOnly.deleteEnrolmentFromUser(enrolmentKey, "b"))
        .thenReturn(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](()))

      val result = sut.deallocateEnrolmentFromUsers(enrolment).value.futureValue

      result mustBe a[Right[_, Unit]]
    }

    "return Left" when {
      "deleteEnrolmentFromUser is failing" in {
        val enrolmentKey = s"${enrolment.serviceName}~${enrolment.identifiers.key}~${enrolment.identifiers.value}"

        when(mockEnrolmentStoreConnectorTestOnly.getUsersFromEnrolment(enrolmentKey))
          .thenReturn(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](List("a", "b")))

        when(mockEnrolmentStoreConnectorTestOnly.deleteEnrolmentFromUser(enrolmentKey, "a"))
          .thenReturn(EitherT.leftT[Future, Unit](UnexpectedResponseFromEACD))

        when(mockEnrolmentStoreConnectorTestOnly.deleteEnrolmentFromUser(enrolmentKey, "b"))
          .thenReturn(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](()))

        val result = sut.deallocateEnrolmentFromUsers(enrolment).value.futureValue

        result mustBe a[Left[TaxEnrolmentAssignmentErrors, _]]
      }

      "getUsersFromEnrolment is failing" in {
        val enrolmentKey = s"${enrolment.serviceName}~${enrolment.identifiers.key}~${enrolment.identifiers.value}"

        when(mockEnrolmentStoreConnectorTestOnly.getUsersFromEnrolment(enrolmentKey))
          .thenReturn(EitherT.leftT[Future, List[String]](UnexpectedResponseFromEACD))

        val result = sut.deallocateEnrolmentFromUsers(enrolment).value.futureValue

        result mustBe a[Left[TaxEnrolmentAssignmentErrors, _]]
      }
    }
  }

  "deallocateEnrolmentsFromUser" must {
    val credId = "credId"

    "deallocate enrolments" in {
      when(mockEnrolmentStoreConnectorTestOnly.getEnrolmentsFromUser(credId))
        .thenReturn(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](List("a", "b")))

      when(mockEnrolmentStoreConnectorTestOnly.deleteEnrolmentFromUser("a", credId))
        .thenReturn(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](()))

      when(mockEnrolmentStoreConnectorTestOnly.deleteEnrolmentFromUser("b", credId))
        .thenReturn(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](()))

      val result = sut.deallocateEnrolmentsFromUser(credId).value.futureValue

      result mustBe a[Right[_, Unit]]
    }

    "return Left" when {
      "deleteEnrolmentFromUser is failing" in {

        when(mockEnrolmentStoreConnectorTestOnly.getEnrolmentsFromUser(credId))
          .thenReturn(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](List("a", "b")))

        when(mockEnrolmentStoreConnectorTestOnly.deleteEnrolmentFromUser("a", credId))
          .thenReturn(EitherT.leftT[Future, Unit](UnexpectedResponseFromEACD))

        when(mockEnrolmentStoreConnectorTestOnly.deleteEnrolmentFromUser("b", credId))
          .thenReturn(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](()))

        val result = sut.deallocateEnrolmentsFromUser(credId).value.futureValue

        result mustBe a[Left[TaxEnrolmentAssignmentErrors, _]]
      }

      "getEnrolmentsFromUser is failing" in {

        when(mockEnrolmentStoreConnectorTestOnly.getEnrolmentsFromUser(credId))
          .thenReturn(EitherT.leftT[Future, List[String]](UnexpectedResponseFromEACD))

        val result = sut.deallocateEnrolmentsFromUser(credId).value.futureValue

        result mustBe a[Left[TaxEnrolmentAssignmentErrors, _]]
      }
    }
  }

  "deallocateEnrolmentsFromGroup" must {
    val groupId = "groupId"

    "deallocate enrolments" in {

      when(mockEnrolmentStoreConnectorTestOnly.getEnrolmentsFromGroup(groupId))
        .thenReturn(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](List("a", "b")))

      when(mockEnrolmentStoreConnectorTestOnly.deleteEnrolmentFromGroup("a", groupId))
        .thenReturn(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](()))

      when(mockEnrolmentStoreConnectorTestOnly.deleteEnrolmentFromGroup("b", groupId))
        .thenReturn(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](()))

      val result = sut.deallocateEnrolmentsFromGroup(groupId).value.futureValue

      result mustBe a[Right[_, Unit]]
    }

    "return Left" when {
      "deleteEnrolmentFromGroup is failing" in {
        when(mockEnrolmentStoreConnectorTestOnly.getEnrolmentsFromGroup(groupId))
          .thenReturn(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](List("a", "b")))

        when(mockEnrolmentStoreConnectorTestOnly.deleteEnrolmentFromGroup("a", groupId))
          .thenReturn(EitherT.leftT[Future, Unit](UnexpectedResponseFromEACD))

        when(mockEnrolmentStoreConnectorTestOnly.deleteEnrolmentFromGroup("b", groupId))
          .thenReturn(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](()))

        val result = sut.deallocateEnrolmentsFromGroup(groupId).value.futureValue

        result mustBe a[Left[TaxEnrolmentAssignmentErrors, _]]
      }

      "getEnrolmentsFromUser is failing" in {

        when(mockEnrolmentStoreConnectorTestOnly.getEnrolmentsFromGroup(groupId))
          .thenReturn(EitherT.leftT[Future, List[String]](UnexpectedResponseFromEACD))

        val result = sut.deallocateEnrolmentsFromGroup(groupId).value.futureValue

        result mustBe a[Left[TaxEnrolmentAssignmentErrors, _]]
      }
    }
  }

  "deleteEnrolment" must {
    val enrolment = EnrolmentDetailsTestOnly(
      "serviceName",
      IdentifiersOrVerifiers("KEY", "VALUE"),
      List(IdentifiersOrVerifiers("KEY2", "VALUE2")),
      "enrolment Friendly name",
      "state",
      "type"
    )
    val enrolmentKey = s"${enrolment.serviceName}~${enrolment.identifiers.key}~${enrolment.identifiers.value}"

    "delete enrolment" in {

      when(mockEnrolmentStoreConnectorTestOnly.deleteEnrolment(enrolmentKey))
        .thenReturn(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](()))

      val result = sut.deleteEnrolment(enrolment).value.futureValue

      result mustBe a[Right[_, Unit]]
    }

    "return Left" in {

      when(mockEnrolmentStoreConnectorTestOnly.deleteEnrolment(enrolmentKey))
        .thenReturn(EitherT.leftT[Future, Unit](UnexpectedError))

      val result = sut.deleteEnrolment(enrolment).value.futureValue

      result mustBe a[Left[UnexpectedError.type, _]]
    }
  }

  "getUsersAssignedPTEnrolmentFromStub" when {
    "the a PT enrolment has been assigned for the nino" should {
      "call the EACD, save to cache and return the account details" in {

        when(mockEnrolmentStoreStubConnectorTestOnly.getUsersWithPTEnrolment(NINO))
          .thenReturn(createInboundResult(UsersAssignedEnrolment1))

        val result = sut.getUsersAssignedPTEnrolmentFromStub(NINO)
        whenReady(result.value) { res =>
          res shouldBe Right(UsersAssignedEnrolment1)
        }
      }
    }

    "EACD returns an error" should {
      "return an error" in {

        when(mockEnrolmentStoreStubConnectorTestOnly.getUsersWithPTEnrolment(NINO))
          .thenReturn(createInboundResultError(UnexpectedResponseFromEACD))

        val result = sut.getUsersAssignedPTEnrolmentFromStub(NINO)
        whenReady(result.value) { res =>
          res shouldBe Left(UnexpectedResponseFromEACD)
        }
      }
    }
  }

  "deleteAllKnownFactsForNino" must {
    "delete all known IR-SA and HMRC-PT known facts" in {

      when(mockEnrolmentStoreConnectorTestOnly.queryKnownFactsByVerifiers(ArgumentMatchers.eq("IR-SA"), any()))
        .thenReturn(createInboundResult(List("enrolmentKey1", "enrolmentKey2")))

      when(mockEnrolmentStoreConnectorTestOnly.queryKnownFactsByVerifiers(ArgumentMatchers.eq("HMRC-PT"), any()))
        .thenReturn(createInboundResult(List("enrolmentKey3")))

      List("enrolmentKey1", "enrolmentKey2", "enrolmentKey3").foreach { enrolmentKey =>
        when(mockEnrolmentStoreConnectorTestOnly.deleteEnrolment(enrolmentKey))
          .thenReturn(createInboundResult(()))
      }

      val result = sut.deleteAllKnownFactsForNino(NINO).value.futureValue
      result mustBe a[Right[_, Unit]]
    }
  }
}
