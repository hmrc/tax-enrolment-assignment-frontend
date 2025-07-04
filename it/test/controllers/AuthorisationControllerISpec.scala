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

package controllers

import helpers.IntegrationSpecBase
import helpers.TestITData.xAuthToken
import play.api.http.Status.UNAUTHORIZED
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, defaultAwaitTimeout, route, status, writeableOf_AnyContentAsEmpty}

class AuthorisationControllerISpec extends IntegrationSpecBase {

  "GET /unauthorised" when {
    "called" should {
      "return Unauthorized and take the user to the errorView" in {
        val request = FakeRequest(GET, unauthorizedPath)
          .withSession(xAuthToken)
        val result  = route(app, request).get
        status(result) shouldBe UNAUTHORIZED
      }
    }
  }
}
