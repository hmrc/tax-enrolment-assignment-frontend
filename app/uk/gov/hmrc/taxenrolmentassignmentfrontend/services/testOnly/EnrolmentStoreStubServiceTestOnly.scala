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
import uk.gov.hmrc.service.TEAFResult
import uk.gov.hmrc.taxenrolmentassignmentfrontend.connectors.testOnly.EnrolmentStoreStubConnectorTestOnly

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class EnrolmentStoreStubServiceTestOnly @Inject() (
  enrolmentStoreStubConnectorTestOnly: EnrolmentStoreStubConnectorTestOnly
)(implicit ec: ExecutionContext) {

  def deleteAccountIfExist(groupId: String)(implicit hc: HeaderCarrier): TEAFResult[Unit] =
    enrolmentStoreStubConnectorTestOnly.getStubAccount(groupId).flatMap {
      case None    => EitherT.rightT(()): TEAFResult[Unit]
      case Some(_) => enrolmentStoreStubConnectorTestOnly.deleteStubAccount(groupId)
    }
}
