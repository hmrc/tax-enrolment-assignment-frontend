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

package uk.gov.hmrc.taxenrolmentassignmentfrontend.utils

import cats.data.EitherT
import cats.implicits.toTraverseOps
import uk.gov.hmrc.auth.core.{EnrolmentIdentifier, Enrolments}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.connectors.TaxEnrolmentsConnector
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.enums.EnrolmentEnum.hmrcPTKey

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class HmrcPTEnrolment @Inject() (taxEnrolmentsConnector: TaxEnrolmentsConnector) {
  def findAndDeleteWrongPTEnrolment(nino: Nino, enrolments: Enrolments, groupId: String)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): EitherT[Future, UpstreamErrorResponse, Unit] = {
    val invalidEnrolmentIdentifiers: List[EnrolmentIdentifier] = enrolments.enrolments
      .filter(_.key == s"$hmrcPTKey")
      .flatMap(_.identifiers.filter(id => id.key == "NINO"))
      .filter(enrolmentIdentifier => Nino(enrolmentIdentifier.value) != nino)
      .toList

    invalidEnrolmentIdentifiers
      .map { enrolmentIdentifier =>
        taxEnrolmentsConnector
          .deallocateEnrolment(groupId, s"$hmrcPTKey~NINO~${enrolmentIdentifier.value}")
      }
      .sequence
      .map(_ => ())
  }
}
