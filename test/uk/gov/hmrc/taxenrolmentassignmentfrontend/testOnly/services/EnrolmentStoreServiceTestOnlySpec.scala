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
import org.scalatest.matchers.must.Matchers._
import play.api.Application
import play.api.inject.bind
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.{InvalidRedirectUrl, TaxEnrolmentAssignmentErrors, UnexpectedError, UnexpectedResponseFromEACD}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.BaseSpec
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.IdentifiersOrVerifiers
import uk.gov.hmrc.taxenrolmentassignmentfrontend.testOnly.connectors.{EnrolmentStoreConnectorTestOnly, EnrolmentStoreStubConnectorTestOnly}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.testOnly.models.EnrolmentDetailsTestOnly

import scala.concurrent.Future

class EnrolmentStoreServiceTestOnlySpec extends BaseSpec {

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
      (mockEnrolmentStoreStubConnectorTestOnly
        .deleteStubAccount(_: String)(_: HeaderCarrier))
        .expects(groupId, *)
        .returning(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](()))

      val result = sut.deleteAccount(groupId).value.futureValue

      result mustBe Right(())
    }

    "return Left" in {
      (mockEnrolmentStoreStubConnectorTestOnly
        .deleteStubAccount(_: String)(_: HeaderCarrier))
        .expects(groupId, *)
        .returning(EitherT.leftT[Future, Unit](InvalidRedirectUrl))

      val result = sut.deleteAccount(groupId).value.futureValue

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
      (mockEnrolmentStoreConnectorTestOnly
        .getGroupsFromEnrolment(_: String)(_: HeaderCarrier))
        .expects(enrolmentKey, *)
        .returning(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](List("a", "b")))

      (mockEnrolmentStoreConnectorTestOnly
        .deleteEnrolmentFromGroup(_: String, _: String)(_: HeaderCarrier))
        .expects(enrolmentKey, "a", *)
        .returning(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](()))

      (mockEnrolmentStoreConnectorTestOnly
        .deleteEnrolmentFromGroup(_: String, _: String)(_: HeaderCarrier))
        .expects(enrolmentKey, "b", *)
        .returning(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](()))

      val result = sut.deallocateEnrolmentFromGroups(enrolment).value.futureValue

      result mustBe a[Right[_, Unit]]
    }

    "return Left" when {
      "deleteEnrolmentFromGroup is failing" in {
        val enrolmentKey = s"${enrolment.serviceName}~${enrolment.identifiers.key}~${enrolment.identifiers.value}"
        (mockEnrolmentStoreConnectorTestOnly
          .getGroupsFromEnrolment(_: String)(_: HeaderCarrier))
          .expects(enrolmentKey, *)
          .returning(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](List("a", "b")))

        (mockEnrolmentStoreConnectorTestOnly
          .deleteEnrolmentFromGroup(_: String, _: String)(_: HeaderCarrier))
          .expects(enrolmentKey, "a", *)
          .returning(EitherT.leftT[Future, Unit](UnexpectedResponseFromEACD))

        (mockEnrolmentStoreConnectorTestOnly
          .deleteEnrolmentFromGroup(_: String, _: String)(_: HeaderCarrier))
          .expects(enrolmentKey, "b", *)
          .returning(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](()))

        val result = sut.deallocateEnrolmentFromGroups(enrolment).value.futureValue

        result mustBe a[Left[TaxEnrolmentAssignmentErrors, _]]
      }

      "getGroupsFromEnrolment is failing" in {
        val enrolmentKey = s"${enrolment.serviceName}~${enrolment.identifiers.key}~${enrolment.identifiers.value}"
        (mockEnrolmentStoreConnectorTestOnly
          .getGroupsFromEnrolment(_: String)(_: HeaderCarrier))
          .expects(enrolmentKey, *)
          .returning(EitherT.leftT[Future, List[String]](UnexpectedResponseFromEACD))

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
      (mockEnrolmentStoreConnectorTestOnly
        .getUsersFromEnrolment(_: String)(_: HeaderCarrier))
        .expects(enrolmentKey, *)
        .returning(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](List("a", "b")))

      (mockEnrolmentStoreConnectorTestOnly
        .deleteEnrolmentFromUser(_: String, _: String)(_: HeaderCarrier))
        .expects(enrolmentKey, "a", *)
        .returning(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](()))

      (mockEnrolmentStoreConnectorTestOnly
        .deleteEnrolmentFromUser(_: String, _: String)(_: HeaderCarrier))
        .expects(enrolmentKey, "b", *)
        .returning(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](()))

      val result = sut.deallocateEnrolmentFromUsers(enrolment).value.futureValue

      result mustBe a[Right[_, Unit]]
    }

    "return Left" when {
      "deleteEnrolmentFromUser is failing" in {
        val enrolmentKey = s"${enrolment.serviceName}~${enrolment.identifiers.key}~${enrolment.identifiers.value}"
        (mockEnrolmentStoreConnectorTestOnly
          .getUsersFromEnrolment(_: String)(_: HeaderCarrier))
          .expects(enrolmentKey, *)
          .returning(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](List("a", "b")))

        (mockEnrolmentStoreConnectorTestOnly
          .deleteEnrolmentFromUser(_: String, _: String)(_: HeaderCarrier))
          .expects(enrolmentKey, "a", *)
          .returning(EitherT.leftT[Future, Unit](UnexpectedResponseFromEACD))

        (mockEnrolmentStoreConnectorTestOnly
          .deleteEnrolmentFromUser(_: String, _: String)(_: HeaderCarrier))
          .expects(enrolmentKey, "b", *)
          .returning(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](()))

        val result = sut.deallocateEnrolmentFromUsers(enrolment).value.futureValue

        result mustBe a[Left[TaxEnrolmentAssignmentErrors, _]]
      }

      "getUsersFromEnrolment is failing" in {
        val enrolmentKey = s"${enrolment.serviceName}~${enrolment.identifiers.key}~${enrolment.identifiers.value}"
        (mockEnrolmentStoreConnectorTestOnly
          .getUsersFromEnrolment(_: String)(_: HeaderCarrier))
          .expects(enrolmentKey, *)
          .returning(EitherT.leftT[Future, List[String]](UnexpectedResponseFromEACD))

        val result = sut.deallocateEnrolmentFromUsers(enrolment).value.futureValue

        result mustBe a[Left[TaxEnrolmentAssignmentErrors, _]]
      }
    }
  }

  "deallocateEnrolmentsFromUser" must {
    val credId = "credId"

    "deallocate enrolments" in {
      (mockEnrolmentStoreConnectorTestOnly
        .getEnrolmentsFromUser(_: String)(_: HeaderCarrier))
        .expects(credId, *)
        .returning(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](List("a", "b")))

      (mockEnrolmentStoreConnectorTestOnly
        .deleteEnrolmentFromUser(_: String, _: String)(_: HeaderCarrier))
        .expects("a", credId, *)
        .returning(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](()))

      (mockEnrolmentStoreConnectorTestOnly
        .deleteEnrolmentFromUser(_: String, _: String)(_: HeaderCarrier))
        .expects("b", credId, *)
        .returning(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](()))

      val result = sut.deallocateEnrolmentsFromUser(credId).value.futureValue

      result mustBe a[Right[_, Unit]]
    }

    "return Left" when {
      "deleteEnrolmentFromUser is failing" in {
        (mockEnrolmentStoreConnectorTestOnly
          .getEnrolmentsFromUser(_: String)(_: HeaderCarrier))
          .expects(credId, *)
          .returning(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](List("a", "b")))

        (mockEnrolmentStoreConnectorTestOnly
          .deleteEnrolmentFromUser(_: String, _: String)(_: HeaderCarrier))
          .expects("a", credId, *)
          .returning(EitherT.leftT[Future, Unit](UnexpectedResponseFromEACD))

        (mockEnrolmentStoreConnectorTestOnly
          .deleteEnrolmentFromUser(_: String, _: String)(_: HeaderCarrier))
          .expects("b", credId, *)
          .returning(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](()))

        val result = sut.deallocateEnrolmentsFromUser(credId).value.futureValue

        result mustBe a[Left[TaxEnrolmentAssignmentErrors, _]]
      }

      "getEnrolmentsFromUser is failing" in {
        (mockEnrolmentStoreConnectorTestOnly
          .getEnrolmentsFromUser(_: String)(_: HeaderCarrier))
          .expects(credId, *)
          .returning(EitherT.leftT[Future, List[String]](UnexpectedResponseFromEACD))

        val result = sut.deallocateEnrolmentsFromUser(credId).value.futureValue

        result mustBe a[Left[TaxEnrolmentAssignmentErrors, _]]
      }
    }
  }

  "deallocateEnrolmentsFromGroup" must {
    val groupId = "groupId"

    "deallocate enrolments" in {
      (mockEnrolmentStoreConnectorTestOnly
        .getEnrolmentsFromGroup(_: String)(_: HeaderCarrier))
        .expects(groupId, *)
        .returning(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](List("a", "b")))

      (mockEnrolmentStoreConnectorTestOnly
        .deleteEnrolmentFromGroup(_: String, _: String)(_: HeaderCarrier))
        .expects("a", groupId, *)
        .returning(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](()))

      (mockEnrolmentStoreConnectorTestOnly
        .deleteEnrolmentFromGroup(_: String, _: String)(_: HeaderCarrier))
        .expects("b", groupId, *)
        .returning(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](()))

      val result = sut.deallocateEnrolmentsFromGroup(groupId).value.futureValue

      result mustBe a[Right[_, Unit]]
    }

    "return Left" when {
      "deleteEnrolmentFromGroup is failing" in {
        (mockEnrolmentStoreConnectorTestOnly
          .getEnrolmentsFromGroup(_: String)(_: HeaderCarrier))
          .expects(groupId, *)
          .returning(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](List("a", "b")))

        (mockEnrolmentStoreConnectorTestOnly
          .deleteEnrolmentFromGroup(_: String, _: String)(_: HeaderCarrier))
          .expects("a", groupId, *)
          .returning(EitherT.leftT[Future, Unit](UnexpectedResponseFromEACD))

        (mockEnrolmentStoreConnectorTestOnly
          .deleteEnrolmentFromGroup(_: String, _: String)(_: HeaderCarrier))
          .expects("b", groupId, *)
          .returning(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](()))

        val result = sut.deallocateEnrolmentsFromGroup(groupId).value.futureValue

        result mustBe a[Left[TaxEnrolmentAssignmentErrors, _]]
      }

      "getEnrolmentsFromUser is failing" in {
        (mockEnrolmentStoreConnectorTestOnly
          .getEnrolmentsFromGroup(_: String)(_: HeaderCarrier))
          .expects(groupId, *)
          .returning(EitherT.leftT[Future, List[String]](UnexpectedResponseFromEACD))

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
      (mockEnrolmentStoreConnectorTestOnly
        .deleteEnrolment(_: String)(_: HeaderCarrier))
        .expects(enrolmentKey, *)
        .returning(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](()))

      val result = sut.deleteEnrolment(enrolment).value.futureValue

      result mustBe a[Right[_, Unit]]
    }

    "return Left" in {
      (mockEnrolmentStoreConnectorTestOnly
        .deleteEnrolment(_: String)(_: HeaderCarrier))
        .expects(enrolmentKey, *)
        .returning(EitherT.leftT[Future, Unit](UnexpectedError))

      val result = sut.deleteEnrolment(enrolment).value.futureValue

      result mustBe a[Left[UnexpectedError.type, _]]
    }
  }
}
