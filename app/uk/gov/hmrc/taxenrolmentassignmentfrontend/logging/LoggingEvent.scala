/*
 * Copyright 2022 HM Revenue & Customs
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

package uk.gov.hmrc.taxenrolmentassignmentfrontend.logging

import play.api.libs.json.{Format, Json}

object LoggingEvent {

  def logAuthenticationFailure(errorDetails: String): LoggingEvent =
    Warn(Event("[AuthAction][invokeBlock]", errorDetails = Some(errorDetails)))

  val logSuccessfulRedirectToReturnUrl: LoggingEvent =
    Info(
      Event(
        "[TestOnlyController][successfulCall]",
        Some("Successfully Redirected")
      )
    )

  def logUnexpectedResponseFromIV(nino: String,
                                  statusReturned: Int): LoggingEvent =
    Error(
      Event(
        "[IVConnector][getCredentialsWithNino]",
        errorDetails = Some(
          s"Identity Verification return status of $statusReturned for NINO $nino"
        )
      )
    )

  def logUnexpectedResponseFromEACD(
    enrolmentType: String,
    statusReturned: Int,
    eacdErrorMsg: String = "N/A"
  ): LoggingEvent =
    Error(
      Event(
        "[EACDConnector][getUsersWithAssignedEnrolment]",
        errorDetails = Some(
          s"EACD returned status of $statusReturned when searching for users with $enrolmentType enrolment." +
            s"\nError Message: $eacdErrorMsg"
        )
      )
    )

  def logUnexpectedResponseFromEACDQueryKnownFacts(
    nino: String,
    statusReturned: Int,
    eacdErrorMsg: String = "N/A"
  ): LoggingEvent =
    Error(
      Event(
        "[EACDConnector][queryKnownFactsByNinoVerifier]",
        errorDetails = Some(
          s"EACD returned status of $statusReturned when searching for users with IR-SA enrolment for NINO $nino." +
            s"\nError Message: $eacdErrorMsg"
        )
      )
    )

  def logUnexpectedResponseFromTaxEnrolments(
    nino: String,
    statusReturned: Int
  ): LoggingEvent =
    Error(
      Event(
        "[TaxEnrolmentsConnector][assignPTEnrolment]",
        errorDetails = Some(
          s"Tax Enrolments return status of $statusReturned when allocating PT enrolment for users with $nino NINO"
        )
      )
    )

  def logUnexpectedResponseFromUsersGroupSearch(
    credId: String,
    statusReturned: Int
  ): LoggingEvent =
    Error(
      Event(
        "[UsersGroupSearchConnector][getUsersDetails]",
        errorDetails = Some(
          s"Users Group Search return status of $statusReturned for credID $credId"
        )
      )
    )

  implicit val formats: Format[Event] = Json.format[Event]

  sealed trait LoggingEvent {
    val event: Event
  }

  sealed case class Event(event: String,
                          details: Option[String] = None,
                          errorDetails: Option[String] = None)

  case class Info(event: Event) extends LoggingEvent

  case class Warn(event: Event) extends LoggingEvent

  case class Error(event: Event) extends LoggingEvent

}
