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
import cats.implicits.toTraverseOps
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.service.TEAFResult
import uk.gov.hmrc.taxenrolmentassignmentfrontend.testOnly.connectors.{EnrolmentStoreConnectorTestOnly, EnrolmentStoreStubConnectorTestOnly}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.testOnly.models.EnrolmentDetailsTestOnly

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class EnrolmentStoreServiceTestOnly @Inject() (
  enrolmentStoreConnectorTestOnly: EnrolmentStoreConnectorTestOnly,
  enrolmentStoreStubConnectorTestOnly: EnrolmentStoreStubConnectorTestOnly
)(implicit ec: ExecutionContext) {

  def deleteAccountIfExist(groupId: String)(implicit hc: HeaderCarrier): TEAFResult[Unit] =
    enrolmentStoreStubConnectorTestOnly.getStubAccount(groupId).flatMap {
      case None    => EitherT.rightT(()): TEAFResult[Unit]
      case Some(_) => enrolmentStoreStubConnectorTestOnly.deleteStubAccount(groupId)
    }

  def deallocateEnrolmentFromGroups(
    enrolment: EnrolmentDetailsTestOnly
  )(implicit hc: HeaderCarrier): TEAFResult[Unit] = {
    val enrolmentKey = s"${enrolment.serviceName}~${enrolment.identifiers.key}~${enrolment.identifiers.value}"
    enrolmentStoreConnectorTestOnly.getGroupsFromEnrolment(enrolmentKey).flatMap { groupList =>
      groupList.map { groupId =>
        enrolmentStoreConnectorTestOnly.deleteEnrolmentFromGroup(enrolmentKey, groupId)
      }.sequence
    }
  }.map(_ => ())

  def deallocateEnrolmentFromUsers(
    enrolment: EnrolmentDetailsTestOnly
  )(implicit hc: HeaderCarrier): TEAFResult[Unit] = {
    val enrolmentKey = s"${enrolment.serviceName}~${enrolment.identifiers.key}~${enrolment.identifiers.value}"
    enrolmentStoreConnectorTestOnly.getUsersFromEnrolment(enrolmentKey).flatMap { groupList =>
      groupList.map { groupId =>
        enrolmentStoreConnectorTestOnly.deleteEnrolmentFromUser(enrolmentKey, groupId)
      }.sequence
    }
  }.map(_ => ())

  def deleteEnrolment(
    enrolment: EnrolmentDetailsTestOnly
  )(implicit hc: HeaderCarrier): TEAFResult[Unit] = {
    val enrolmentKey = s"${enrolment.serviceName}~${enrolment.identifiers.key}~${enrolment.identifiers.value}"
    enrolmentStoreConnectorTestOnly.deleteEnrolment(enrolmentKey)
  }

  def deallocateEnrolmentsFromGroup(
    groupId: String
  )(implicit hc: HeaderCarrier): TEAFResult[Unit] =
    enrolmentStoreConnectorTestOnly
      .getEnrolmentsFromGroup(groupId)
      .flatMap { enrolmentKeys =>
        enrolmentKeys.map { enrolmentKey =>
          enrolmentStoreConnectorTestOnly.deleteEnrolmentFromGroup(enrolmentKey, groupId)
        }.sequence
      }
      .map(_ => ())

  def deallocateEnrolmentsFromUser(
    credId: String
  )(implicit hc: HeaderCarrier): TEAFResult[Unit] =
    enrolmentStoreConnectorTestOnly
      .getEnrolmentsFromUser(credId)
      .flatMap { enrolmentKeys =>
        enrolmentKeys.map { enrolmentKey =>
          enrolmentStoreConnectorTestOnly.deleteEnrolmentFromUser(enrolmentKey, credId)
        }.sequence
      }
      .map(_ => ())

}
