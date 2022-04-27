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

package uk.gov.hmrc.taxenrolmentassignmentfrontend.models.forms

import uk.gov.hmrc.taxenrolmentassignmentfrontend.forms.KeepAccessToSAThroughPTAForm
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.TestFixture

class KeepAccessToSAThroughPTAFormSpec extends TestFixture {

  "KeepAccessToSAThroughPTAForm" should {
    lazy val form = KeepAccessToSAThroughPTAForm.keepAccessToSAThroughPTAForm

    "bind with no errors when supplied with a valid KeepAccessToSAThroughPTA model" in {
      form.bind(Map("select-continue" -> "yes")).errors.size shouldEqual 0
    }

    "fill with no errors when supplied with a valid Confirm Model" in {
      form.fill(KeepAccessToSAThroughPTA(true)).errors.size shouldEqual 0
    }

    "bind with an error" when {
      "supplied with an invalid data" should {
        lazy val boundForm = form.bind(Map("select-continue" -> ""))

        "have 1 errors" in {
          boundForm.errors.map(error => (error.key, error.message)) shouldEqual Seq(
            "select-continue" -> "keepAccessToSA.error.required"
          )
        }
      }
    }

    "unbind to 'yes'" in {
      form.fill(KeepAccessToSAThroughPTA(true)).data shouldBe Map(
        "select-continue" -> "yes"
      )
    }
    "unbind to 'no'" in {
      form.fill(KeepAccessToSAThroughPTA(false)).data shouldBe Map(
        "select-continue" -> "no"
      )
    }
  }
}
