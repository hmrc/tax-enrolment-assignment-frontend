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

package uk.gov.hmrc.taxenrolmentassignmentfrontend.errors

import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes

sealed trait TaxEnrolmentAssignmentErrors

object UnexpectedResponseFromIV extends TaxEnrolmentAssignmentErrors
object UnexpectedResponseFromEACD extends TaxEnrolmentAssignmentErrors
object NoAccountsHaveSA extends TaxEnrolmentAssignmentErrors
object UnexpectedResponseFromTaxEnrolments extends TaxEnrolmentAssignmentErrors
object UnexpectedResponseFromUsersGroupsSearch
    extends TaxEnrolmentAssignmentErrors
object NoPTEnrolmentWhenOneExpected extends TaxEnrolmentAssignmentErrors
object NoSAEnrolmentWhenOneExpected extends TaxEnrolmentAssignmentErrors
object NoRedirectUrlInCache extends TaxEnrolmentAssignmentErrors
object UnexpectedError extends TaxEnrolmentAssignmentErrors
object UnexpectedResponseAssigningTemporaryPTAEnrolment extends TaxEnrolmentAssignmentErrors
case class CacheNotCompleteOrNotCorrect(redirectUrl: Option[String],
                                        accountType: Option[AccountTypes.Value]) extends TaxEnrolmentAssignmentErrors
case class IncorrectUserType(redirectUrl: String, accountType: AccountTypes.Value) extends TaxEnrolmentAssignmentErrors

case class UnexpectedPTEnrolment(accountTypes: AccountTypes.Value) extends TaxEnrolmentAssignmentErrors

object ResponseBodyInvalidFromAddTaxesFrontendSASetup extends TaxEnrolmentAssignmentErrors
object UnexpectedResponseFromAddTaxesFrontendSASetup extends TaxEnrolmentAssignmentErrors
object InvalidRedirectUrl extends TaxEnrolmentAssignmentErrors

