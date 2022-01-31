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

package uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.controllers


import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.helpers.TestFixture
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.UsersAssignedEnrolment


class TestOnlyControllerSpec extends TestFixture {

  private  val fakeReq = FakeRequest("GET", "/tax-enrolment-assignment-frontend/test-only/enrolment-store/enrolments/HMRC-PT~NINO~AB876543F/users")


  "eS0Call" when {
    "the request Json contains an enrolment key that matches the nino value" should {
      "return accepted" in {

        val enrolmentKey = "HMRC-PT~NINO~CP872173B"
        val jsonResp = UsersAssignedEnrolment(List("6145202884164547"), List.empty)
        val request     = fakeReq.withBody(Json.obj())
        val res         = testOnlyController.es0Call(enrolmentKey)(request)
        status(res) shouldBe OK
        contentAsJson(res) shouldBe Json.toJson(jsonResp)

      }
    }
    "the request Json contains an enrolment key that does not matched the nino value" should {
      "return no content" in {
        val enrolmentKey = "HMRC-PT~NINO~JK592173B"
        val request     = fakeReq.withBody(Json.obj())
        val res         = testOnlyController.es0Call(enrolmentKey)(request)
        status(res) shouldBe NO_CONTENT
      }
    }
  }
}
