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
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.TaxEnrolmentAssignmentErrors

object LoggingEvent {

  def logSingleAccountHolderAssignedEnrolment(
    credentialId: String
  ): LoggingEvent =
    Info(
      Event(
        "[AccountCheckController][silentEnrol]",
        details = Some(
          s"PT enrolment assigned to single account credential $credentialId"
        )
      )
    )

  def logRedirectingToReturnUrl(credentialId: String,
                                classAndMethod: String): LoggingEvent = Info(
    Event(
      classAndMethod,
      details = Some(
        s"User with credential $credentialId is being redirected to return url"
      )
    )
  )

  def logMultipleAccountHolderAssignedEnrolment(
    credentialId: String
  ): LoggingEvent =
    Info(
      Event(
        "[AccountCheckController][silentEnrol]",
        details = Some(
          s"PT enrolment assigned to credential $credentialId which has multiple accounts"
        )
      )
    )

  def logAssignedEnrolmentAfterReportingFraud(
    credentialId: String
  ): LoggingEvent =
    Info(
      Event(
        "[ReportSuspiciousIdController][continue]",
        details = Some(
          s"PT enrolment assigned to credential $credentialId after reporting fraud on account with Self Assessment"
        )
      )
    )

  def logCurrentUserAlreadyHasPTEnrolment(credentialId: String): LoggingEvent =
    Info(
      Event(
        "[AccountCheckOrchestrator][getAccountType]",
        details = Some(
          s"PT enrolment has already been assigned to the current credential $credentialId"
        )
      )
    )

  def logCurrentUserHasSAEnrolment(credentialId: String): LoggingEvent =
    Info(
      Event(
        "[AccountCheckOrchestrator][getAccountType]",
        details = Some(s"Current credential $credentialId has SA enrolment.")
      )
    )

  def logAnotherAccountAlreadyHasPTEnrolment(
    credentialId: String,
    credentialWithPTEnrolment: String
  ): LoggingEvent =
    Info(
      Event(
        "[AccountCheckOrchestrator][getAccountType]",
        details = Some(
          s"Signed in with credential $credentialId has already been assigned to another credential $credentialWithPTEnrolment"
        )
      )
    )

  def logAnotherAccountHasSAEnrolment(
    credentialId: String,
    credentialWithSAEnrolment: String
  ): LoggingEvent =
    Info(
      Event(
        "[AccountCheckOrchestrator][getAccountType]",
        details = Some(
          s"Signed in with credential $credentialId has not got SA enrolment but another credential $credentialWithSAEnrolment does"
        )
      )
    )

  def logCurrentUserhasMultipleAccounts(credentialId: String): LoggingEvent =
    Info(
      Event(
        "[AccountCheckOrchestrator][getAccountType]",
        details =
          Some(s"Signed in credential $credentialId has multiple accounts")
      )
    )

  def logCurrentUserhasOneAccount(credentialId: String): LoggingEvent =
    Info(
      Event(
        "[AccountCheckOrchestrator][getAccountType]",
        details = Some(s"Signed in credential $credentialId has one account")
      )
    )

  def logIncorrectUserType(
    credentialId: String,
    expectedUserType: List[AccountTypes.Value],
    actualUserType: Option[AccountTypes.Value]
  ): LoggingEvent = {
    val expectedUserTypeString = expectedUserType.foldLeft[String]("") {
      (a, b) =>
        if (a.isEmpty) {
          b.toString
        } else {
          s"$a or ${b.toString}"
        }
    }

    val actualAccountTypeString =
      actualUserType.fold("no account type found")(_.toString)
    val errorMessage =
      s"User type of $expectedUserTypeString required but ${actualAccountTypeString} found"
    Warn(
      Event(
        "[MultipleAccountsOrchestrator][checkValidAccountTypeRedirectUrlInCache]",
        details = Some(errorMessage)
      )
    )
  }

  def logNoUserFoundWithPTEnrolment(credentialId: String): LoggingEvent =
    Error(
      Event(
        "[MultipleAccountsOrchestrator][getPTCredentialDetails]",
        details = Some(
          s"No PT enrolment found when user has account type of PT on other account for credential $credentialId"
        )
      )
    )

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
    statusReturned: Int,
    errorMsg: String = ""
  ): LoggingEvent =
    Error(
      Event(
        "[TaxEnrolmentsConnector][assignPTEnrolment]",
        errorDetails = Some(
          s"Tax Enrolments return status of $statusReturned when allocating PT enrolment for users with $nino NINO," +
            s"Error message - $errorMsg"
        )
      )
    )

  def logUnexpectedResponseFromUsersGroupSearch(
    credId: String,
    statusReturned: Int,
    errorMsg: String = "N/A"
  ): LoggingEvent =
    Error(
      Event(
        "[UsersGroupSearchConnector][getUsersDetails]",
        errorDetails = Some(
          s"Users Group Search return status of $statusReturned for credID $credId" +
            s"\nError Message: $errorMsg"
        )
      )
    )

  def logUnexpectedResponseFromTaxEnrolmentsKnownFacts(
    nino: String,
    statusReturned: Int,
    errorMsg: String = ""
  ): LoggingEvent =
    Error(
      Event(
        "[TaxEnrolmentsConnector][assignPTEnrolmentWithKnownFacts]",
        errorDetails = Some(
          s"Tax Enrolments return status of $statusReturned when allocating PT enrolment for users with $nino NINO," +
            s"Error message - $errorMsg"
        )
      )
    )

  def logUnexpectedResponseFromLandingPage(
    error: TaxEnrolmentAssignmentErrors
  ): LoggingEvent =
    Error(
      Event(
        "[LandingPageController][showLandingPage]",
        errorDetails =
          Some(s"Landing Page Controller returned an error: $error")
      )
    )

  def logES2ErrorFromEACD(credId: String,
                          statusReturned: Int,
                          eacdErrorMsg: String = "N/A"): LoggingEvent =
    Error(
      Event(
        "[EACDConnector][queryEnrolmentsAssignedToUser]",
        errorDetails = Some(
          s"EACD returned status of $statusReturned when searching for enrolments associated with credId $credId." +
            s"\nError Message: $eacdErrorMsg"
        )
      )
    )

  def logUnexpectedErrorOccurred(
    credentialId: String,
    classAndMethod: String,
    errorType: TaxEnrolmentAssignmentErrors
  ): LoggingEvent =
    Error(Event(classAndMethod, details = Some(errorType.toString)))

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
