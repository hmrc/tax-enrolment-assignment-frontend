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
import helpers.TestITData.{authoriseResponseJson, saEnrolmentAsCaseClass, saEnrolmentOnly, xAuthToken}
import play.api.test.Helpers.{GET, PUT, contentAsJson, contentAsString, defaultAwaitTimeout, route, status, writeableOf_AnyContentAsEmpty, writeableOf_AnyContentAsJson}
import play.api.http.Status.{NON_AUTHORITATIVE_INFORMATION, NO_CONTENT, OK}
import play.api.libs.json.Json
import play.api.mvc.Cookie
import play.api.test.FakeRequest
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.formats.EnrolmentsFormats

class TestOnlyControllerISpec extends IntegrationSpecBase {

  s"GET /users-groups-search/test-only/users/:credId" should {
    "retrieve the users details" when {
      "the credential is recognised" in {

        val credId = "4684455594391511"
        val url = s"/users-groups-search/test-only/users/$credId"
        val expectedResponse =
          """{"obfuscatedUserId":"********3469","email":"email1@test.com","lastAccessedTimestamp":"2022-01-16T14:40:25Z","additionalFactors":[{"factorType":"sms","phoneNumber":"07783924321"}]}"""

        val request = FakeRequest(GET, url)
          .withSession(xAuthToken)
        val result = route(app, request).get

        status(result) shouldBe NON_AUTHORITATIVE_INFORMATION
        contentAsString(result) shouldBe expectedResponse

      }

      "return default user details " when {
        "the credential is not recognised" in {
          val credId = "2568836745857973"
          val url = s"/users-groups-search/test-only/users/$credId"
          val expectedResponse =
            """{"obfuscatedUserId":"********6121","email":"email11@test.com","lastAccessedTimestamp":"2022-09-16T14:40:25Z","additionalFactors":[{"factorType":"totp","name":"HMRC App"}]}"""

          val request = FakeRequest(GET, url)
            .withSession(xAuthToken)
          val result = route(app, request).get

            status(result) shouldBe NON_AUTHORITATIVE_INFORMATION
            contentAsString(result) shouldBe expectedResponse

        }
      }
    }
  }
  "GET /protect-tax-info/test-only.auth/enrolments" should {

  s"return enrolments and $OK" in {

      val authResponse = authoriseResponseJson(enrolments = saEnrolmentOnly)
      stubAuthorizePost(OK, authResponse.toString())

    val request = FakeRequest(GET, "/protect-tax-info/test-only/auth/enrolments")
      .withSession(xAuthToken)
    val result = route(app, request).get

        status(result) shouldBe OK
        contentAsJson(result) shouldBe Json.toJson(Set(saEnrolmentAsCaseClass))(EnrolmentsFormats.writes)

    }
  }

  "GET /sa/test-only/start" should {
  s"return $OK with success message" in {
    val request = FakeRequest(GET, "/sa/test-only/start")
      .withSession(xAuthToken)
    val result = route(app, request).get

        status(result) shouldBe OK
        contentAsString(result) shouldBe "Successful Redirect to SA"

    }
  }


  "PUT /tax-enrolments/test-only/service/HMRC-PT/enrolment" should {
    s"return $OK" in {
      val request = FakeRequest(PUT, "/tax-enrolments/test-only/service/HMRC-PT/enrolment")
        .withSession(xAuthToken)
        .withJsonBody(Json.obj())
      val result = route(app, request).get

        status(result) shouldBe NO_CONTENT

    }
  }

  "PUT /auth/test-only/enrolments" should {
    s"return $NO_CONTENT" in {
      val request = FakeRequest(PUT, "/auth/test-only/enrolments")
        .withSession(xAuthToken)
        .withJsonBody(Json.obj())
      val result = route(app, request).get

        status(result) shouldBe OK

    }
  }

}
