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

package uk.gov.hmrc.taxenrolmentassignmentfrontend.models

import uk.gov.hmrc.auth.core.{AffinityGroup, Enrolments}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.actions.UserDetailsFromSession
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.TestData.{CURRENT_USER_EMAIL, UTR, saEnrolmentOnly}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.TestFixture
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.enums.EnrolmentEnum.IRSAKey

class UserDetailsFromSessionSpec extends TestFixture {

  "utr" should {
    s"extract correctly if $IRSAKey exists" in {
      UserDetailsFromSession("","", "",Some(CURRENT_USER_EMAIL), AffinityGroup.Agent, saEnrolmentOnly,true, true).utr.get shouldBe UTR
    }
    s"return None if $IRSAKey doesnt exist" in {
      UserDetailsFromSession("","", "",Some(CURRENT_USER_EMAIL), AffinityGroup.Agent, Enrolments(Set.empty),true, true).utr shouldBe None
    }
  }
}
