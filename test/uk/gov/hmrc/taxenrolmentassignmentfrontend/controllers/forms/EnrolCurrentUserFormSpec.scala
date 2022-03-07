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

package uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.forms

import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.helpers.TestFixture
import uk.gov.hmrc.taxenrolmentassignmentfrontend.forms.{
  AccountsBelongToUserForm,
  EnrolCurrentUserIdForm
}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.forms.{
  AccountsBelongToUser,
  EnrolCurrentUserId
}

class EnrolCurrentUserFormSpec extends TestFixture {

  "EnrolCurrentUserIdForm" should {
    lazy val form = EnrolCurrentUserIdForm.enrolCurrentUserIdForm

    "bind with no errors when supplied with a valid ConfirmModel" in {
      form.bind(Map("enrolCurrentUserId" -> "yes")).errors.size shouldEqual 0
    }

    "fill with no errors when supplied with a valid Confirm Model" in {
      form.fill(EnrolCurrentUserId(true)).errors.size shouldEqual 0
    }

    "bind with an error" when {
      "supplied with an invalid data" should {
        lazy val boundForm = form.bind(Map("enrolCurrentUserId" -> ""))

        "have 1 error" in {
          boundForm.errors.map(error => (error.key, error.message)) shouldEqual Seq(
            "enrolCurrentUserId" -> "enrolCurrentUserId.error.required"
          )
        }
      }
    }

    "unbind to 'yes'" in {
      form.fill(EnrolCurrentUserId(true)).data shouldBe Map(
        "enrolCurrentUserId" -> "yes"
      )
    }
    "unbind to 'no'" in {
      form.fill(EnrolCurrentUserId(false)).data shouldBe Map(
        "enrolCurrentUserId" -> "no"
      )
    }
  }

}
