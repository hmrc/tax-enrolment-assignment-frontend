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
import uk.gov.hmrc.taxenrolmentassignmentfrontend.forms.AccountsBelongToUserForm
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.forms.AccountsBelongToUser

class AccountsBelongToUserFormSpec extends TestFixture {

  "AccountsBelongToUserForm" should {
    lazy val form = AccountsBelongToUserForm.accountsBelongToUserForm

    "bind with no errors when supplied with a valid ConfirmModel" in {
      form.bind(Map("recogniseIds" -> "yes")).errors.size shouldEqual 0
    }

    "fill with no errors when supplied with a valid Confirm Model" in {
      form.fill(AccountsBelongToUser(true)).errors.size shouldEqual 0
    }

    "bind with an error" when {
      "supplied with an invalid data" should {
        lazy val boundForm = form.bind(Map("recogniseIds" -> ""))

        "have 1 errors" in {
          boundForm.errors.map(error => (error.key, error.message)) shouldEqual Seq(
            "recogniseIds" -> "associatedAccounts.error.required"
          )
        }
      }
    }

    "unbind to 'yes'" in {
      form.fill(AccountsBelongToUser(true)).data shouldBe Map(
        "recogniseIds" -> "yes"
      )
    }
    "unbind to 'no'" in {
      form.fill(AccountsBelongToUser(false)).data shouldBe Map(
        "recogniseIds" -> "no"
      )
    }
  }

}
