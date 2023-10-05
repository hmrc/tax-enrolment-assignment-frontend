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

package uk.gov.hmrc.taxenrolmentassignmentfrontend.services

import cats.data.EitherT
import uk.gov.hmrc.auth.core.{Enrolment, EnrolmentIdentifier}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.service.TEAFResult
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes.{PT_ASSIGNED_TO_CURRENT_USER, PT_ASSIGNED_TO_OTHER_USER, SINGLE_ACCOUNT}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.config.AppConfig
import uk.gov.hmrc.taxenrolmentassignmentfrontend.connectors.LegacyAuthConnector
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.TaxEnrolmentAssignmentErrors
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.enums.EnrolmentEnum.hmrcPTKey

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

sealed trait ThrottleResult
case object ThrottleApplied extends ThrottleResult
case object ThrottleDoesNotApply extends ThrottleResult

class ThrottlingService @Inject() (legacyAuthConnector: LegacyAuthConnector, appConfig: AppConfig) {

  def throttle(accountType: AccountTypes.Value, nino: Nino, currentEnrolments: Set[Enrolment])(implicit
    ec: ExecutionContext,
    hc: HeaderCarrier
  ): TEAFResult[ThrottleResult] =
    if (shouldAccountTypeBeThrottled(accountType, appConfig.percentageOfUsersThrottledToGetFakeEnrolment, nino)) {
      legacyAuthConnector
        .updateEnrolments(addPTEnrolmentToEnrolments(currentEnrolments, nino))
        .map(_ => ThrottleApplied)
    } else {
      EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](ThrottleDoesNotApply)
    }

  private[services] def addPTEnrolmentToEnrolments(currentEnrolments: Set[Enrolment], nino: Nino): Set[Enrolment] =
    currentEnrolments + newPTEnrolment(nino)

  private[services] def newPTEnrolment(nino: Nino): Enrolment =
    Enrolment(s"$hmrcPTKey", Seq(EnrolmentIdentifier("NINO", nino.nino)), "Activated", None)
  private[services] def isNinoWithinThrottleThreshold(nino: Nino, percentageToThrottle: Int): Boolean =
    percentageToThrottle match {
      case n if n >= 100 || n < 0 => false
      case _ if nino.nino.length != 9 =>
        throw new IllegalArgumentException(s"nino is incorrect length ${nino.nino.length}")
      case n =>
        Try(nino.nino.substring(6, 8).toInt)
          .map(ninoNumber => ninoNumber <= n)
          .getOrElse(
            throw new IllegalArgumentException(s"nino was not valid format for throttle")
          )
    }

  private[services] def shouldAccountTypeBeThrottled(
    accountType: AccountTypes.Value,
    percentageToThrottle: Int,
    nino: Nino
  ): Boolean =
    !List(SINGLE_ACCOUNT, PT_ASSIGNED_TO_CURRENT_USER, PT_ASSIGNED_TO_OTHER_USER).contains(
      accountType
    ) && isNinoWithinThrottleThreshold(nino, percentageToThrottle)

}
