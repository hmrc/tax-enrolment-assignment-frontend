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

package uk.gov.hmrc.taxenrolmentassignmentfrontend.services.testOnly

import cats.data.EitherT
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.taxenrolmentassignmentfrontend.connectors.testOnly.EnrolmentStoreConnectorTestOnly

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import cats.implicits.toTraverseOps
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.TaxEnrolmentAssignmentErrors
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.testOnly.EnrolmentDetailsTestOnly

@Singleton
class EnrolmentStoreServiceTestOnly @Inject() (enrolmentStoreConnectorTestOnly: EnrolmentStoreConnectorTestOnly)(
  implicit ec: ExecutionContext
) {
  def deallocateEnrolmentFromGroups(
    enrolment: EnrolmentDetailsTestOnly
  )(implicit hc: HeaderCarrier): EitherT[Future, TaxEnrolmentAssignmentErrors, Unit] = {
    val enrolmentKey = s"${enrolment.serviceName}~${enrolment.identifiers.head.key}~${enrolment.identifiers.head.value}"
    enrolmentStoreConnectorTestOnly.getGroupsFromEnrolment(enrolmentKey).flatMap { groupList =>
      groupList.map { groupId =>
        enrolmentStoreConnectorTestOnly.deleteEnrolmentFromGroup(enrolmentKey, groupId)
      }.sequence
    }
  }.map(_ => ())

  def deallocateEnrolmentFromUsers(
    enrolment: EnrolmentDetailsTestOnly
  )(implicit hc: HeaderCarrier): EitherT[Future, TaxEnrolmentAssignmentErrors, Unit] = {
    val enrolmentKey = s"${enrolment.serviceName}~${enrolment.identifiers.head.key}~${enrolment.identifiers.head.value}"
    enrolmentStoreConnectorTestOnly.getUsersFromEnrolment(enrolmentKey).flatMap { groupList =>
      groupList.map { groupId =>
        enrolmentStoreConnectorTestOnly.deleteEnrolmentFromUser(enrolmentKey, groupId)
      }.sequence
    }
  }.map(_ => ())

  def deleteEnrolment(
    enrolment: EnrolmentDetailsTestOnly
  )(implicit hc: HeaderCarrier): EitherT[Future, TaxEnrolmentAssignmentErrors, Unit] = {
    val enrolmentKey = s"${enrolment.serviceName}~${enrolment.identifiers.head.key}~${enrolment.identifiers.head.value}"
    enrolmentStoreConnectorTestOnly.deleteEnrolment(enrolmentKey)
  }
}
