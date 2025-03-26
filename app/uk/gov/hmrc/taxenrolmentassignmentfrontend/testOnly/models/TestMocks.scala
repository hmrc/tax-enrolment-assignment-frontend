/*
 * Copyright 2024 HM Revenue & Customs
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

package uk.gov.hmrc.taxenrolmentassignmentfrontend.testOnly.models

object TestMocks {
  val mocks: List[(String, String)] = List(
    "Scenario 3 + 4: GG Login Multiple Accounts: One with SA Enrolment"                  -> "multipleAccountsOneWithSAEnrolment",
    "Scenario 3 + 4: One Login Multiple Accounts: One with SA Enrolment"                 -> "OneLoginMultipleAccountsOneWithSAEnrolment",
    "Scenario 3 + 4: GG Account with No Enrolments, OL with SA Enrolment"                -> "GGAccountWithNoEnrolmentsOneLoginAccountWithSAEnrolment",
    "Scenario 3 + 4: OL Account with No Enrolments, GG with SA Enrolment"                -> "OneLoginAccountWithNoEnrolmentsGGAccountWithSAEnrolment",
    "Scenario 5: GG Account with PT and SA Enrolment, OL with No Enrolment"              -> "GGAccountWithPTAndSAEnrolmentOneLoginAccountWithNoEnrolment",
    "Scenario 5: OL Multiple Accounts: One with PT and SA Enrolment"                     -> "OneLoginMultipleAccountsOneWithPTAndSAEnrolment",
    "Scenario 5: GG Multiple Accounts: One with PT and SA Enrolment"                     -> "multipleAccountsOneWithPTAndSAEnrolment",
    "Scenario 5: GG Account with No Enrolment, OL with PT and SA Enrolment"              -> "GGAccountWithNoEnrolmentOneLoginAccountWithPTAndSAEnrolment",
    "Scenario 6: GG Multiple Accounts: One with PT Enrolment"                            -> "multipleAccountsOneWithPTEnrolment",
    "Scenario 6: OL Multiple Accounts: One with PT Enrolment"                            -> "OneLoginMultipleAccountsOneWithPTEnrolment",
    "Scenario 6: GG Account with PT Enrolment, OL with No Enrolment"                     -> "GGAccountWithPTEnrolmentOneLoginAccountWithNoEnrolment",
    "Scenario 6: GG Account with No Enrolment, OL with PT Enrolment"                     -> "GGAccountWithNoEnrolmentOneLoginAccountWithPTEnrolment",
    "Scenario 7: GG Account with PT Enrolment, OL with SA Enrolment"                     -> "GGAccountWithPTEnrolmentOneLoginAccountWithSAEnrolment",
    "Scenario 7: GG Account with SA Enrolment, OL with PT Enrolment"                     -> "GGAccountWithSAEnrolmentOneLoginAccountWithPTEnrolment",
    "Scenario 7: GG Multiple Accounts: One with PT Enrolment, another with SA Enrolment" -> "multipleAccountsOneWithPTEnrolmentOtherWithSA",
    "Scenario 7: OL Multiple Accounts: One with PT Enrolment, another with SA Enrolment" -> "OneLoginMultipleAccountsOneWithPTEnrolmentOtherWithSA",
    "Multiple Accounts: No Enrolments"                                                   -> "multipleAccountsNoEnrolments",
    "Multiple Accounts: One with PT Enrolment, one with SA Enrolment, one with none"     -> "multipleAccountsOneWithPTEnrolmentOneWithSAOneWithNone",
    "Multiple Accounts: One with SA Enrolment"                                           -> "multipleAccountsOneWithSAEnrolment",
    "Single User: No enrolments"                                                         -> "singleUserNoEnrolments",
    "Single User: SA Enrolments"                                                         -> "singleUserWithSAEnrolment"
  )
}
