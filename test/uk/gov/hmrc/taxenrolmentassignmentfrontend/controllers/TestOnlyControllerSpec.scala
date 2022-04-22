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

package uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers

import play.api.libs.json.{JsString, Json}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.TestFixture

class TestOnlyControllerSpec extends TestFixture {

  private val fakeReq =
    FakeRequest("GET", "/users-group-search/test-only/users/:credId")

  "usersGroupSearchCall" when {
    "the credential is recognised" should {
      "return OK with the userdetails" in {
        val credId = "4684455594391511"
        val expectedResponse = {
          Json.obj(
            ("obfuscatedUserId", JsString("********3469")),
            ("email", JsString("email1@test.com")),
            ("lastAccessedTimestamp", JsString("2022-01-16T14:40:25Z")),
            (
              "additionalFactors",
              Json.arr(
                Json.obj(
                  ("factorType", JsString("sms")),
                  ("phoneNumber", JsString("07783924321"))
                )
              )
            )
          )
        }
        val res = testOnlyController.usersGroupSearchCall(credId)(fakeReq)
        status(res) shouldBe NON_AUTHORITATIVE_INFORMATION
        contentAsJson(res) shouldBe expectedResponse

      }
    }
    "the credential is not recognised" should {
      "return not found" in {
        val credId = "3568836745857979"
        val res = testOnlyController.usersGroupSearchCall(credId)(fakeReq)
        status(res) shouldBe NOT_FOUND
      }
    }
  }
}
