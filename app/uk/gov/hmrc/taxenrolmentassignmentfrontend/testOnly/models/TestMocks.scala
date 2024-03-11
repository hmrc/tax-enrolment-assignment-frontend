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
  val mocks: List[String] = List(
    "Multiple Accounts: No Enrolments",
    "Multiple Accounts: One with PT and SA Enrolment",
    "Multiple Accounts: One with PT Enrolment",
    "Multiple Accounts: One with PT Enrolment, another with SA Enrolment",
    "Multiple Accounts: One with SA Enrolment",
    "Single User: No enrolments",
    "Single User: SA Enrolments",
    "Throttled Multiple Accounts: Has SA Enrolment",
    "Throttled Multiple Accounts: No SA Enrolment",
    "Throttled Multiple Accounts: One with PT Enrolment"
  )
}
