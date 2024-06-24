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
    "Multiple Accounts: No Enrolments"                                    -> "multipleAccountsNoEnrolments",
    "Multiple Accounts: One with PT and SA Enrolment"                     -> "multipleAccountsOneWithPTAndSAEnrolment",
    "Multiple Accounts: One with PT Enrolment"                            -> "multipleAccountsOneWithPTEnrolment",
    "Multiple Accounts: One with PT Enrolment, another with SA Enrolment" -> "multipleAccountsOneWithPTEnrolmentOtherWithSA",
    "Multiple Accounts: One with SA Enrolment"                            -> "multipleAccountsOneWithSAEnrolment",
    "Single User: No enrolments"                                          -> "singleUserNoEnrolments",
    "Single User: SA Enrolments"                                          -> "singleUserWithSAEnrolment",
    "E2E Two Credentials: One with SA Enrolment"                          -> "e2eTwoCredentialsOneWithSAEnrolment",
    "E2E Two Credentials: One with SA and PT Enrolment"                   -> "e2eTwoCredentialsWithSAandPTEnrolment",
    "Two Credentials: No enrolment"                                       -> "twoCredentialsNoEnrolment",
    "Two Credentials: One with SA Enrolment"                              -> "twoCredentialsOneWithSAEnrolment",
    "Two Credentials: One with SA and PT Enrolment"                       -> "twoCredentialsWithSAandPTEnrolment",
    "One Login Multiple Accounts: One with SA Enrolment"                  -> "OneLoginMultipleAccountsOneWithSAEnrolment"
  )
}
