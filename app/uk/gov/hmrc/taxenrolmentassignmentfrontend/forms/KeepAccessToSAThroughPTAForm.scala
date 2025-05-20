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

package uk.gov.hmrc.taxenrolmentassignmentfrontend.forms

import play.api.data.Form
import play.api.data.Forms.{default, mapping, text}
import play.api.data.validation.Constraints._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.forms.KeepAccessToSAThroughPTA

object KeepAccessToSAThroughPTAForm {

  val keepAccessToSAThroughPTAForm: Form[KeepAccessToSAThroughPTA] = Form(
    mapping(
      "select-continue" -> default[String](text, "")
        .verifying(pattern("yes|no".r, error = "keepAccessToSA.error.required"))
        .transform[Boolean](_ == "yes", x => if (x) "yes" else "no")
    )(KeepAccessToSAThroughPTA.apply)(ka => Some(ka.keepAccessToSAThroughPTA))
  )
}
