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

package controllers

import helpers.IntegrationSpecBase
import helpers.TestITData.{authoriseResponseJson, csrfContent, saEnrolmentAsCaseClass, saEnrolmentOnly, xSessionId}
import helpers.WiremockHelper.stubAuthorizePost
import play.api.http.Status
import play.api.libs.json.Json
import play.api.libs.ws.DefaultWSCookie
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.formats.EnrolmentsFormats

class TestOnlyControllerISpec extends IntegrationSpecBase with Status {

  override def afterAll(): Unit = {
    super.afterAll()
  }

  s"GET /users-groups-search/test-only/users/:credId" should {
    "retrieve the users details" when {
      "the credential is recognised" in {

        val credId = "4684455594391511"
        val url = s"/users-groups-search/test-only/users/$credId"
        val expectedResponse =
          """{"obfuscatedUserId":"********3469","email":"email1@test.com","lastAccessedTimestamp":"2022-01-16T14:40:25Z","additionalFactors":[{"factorType":"sms","phoneNumber":"07783924321"}]}"""
        val res = buildTestOnlyRequest(url, followRedirects = true)
          .addCookies(DefaultWSCookie("mdtp", authCookie))
          .addHttpHeaders(xSessionId, csrfContent)
          .withBody(Json.obj())
          .get()

        whenReady(res) { resp =>
          resp.status shouldBe NON_AUTHORITATIVE_INFORMATION
          resp.body shouldBe expectedResponse
        }
      }

      "return default user details " when {
        "the credential is not recognised" in {
          val credId = "2568836745857973"
          val url = s"/users-groups-search/test-only/users/$credId"
          val expectedResponse =
            """{"obfuscatedUserId":"********6121","email":"email11@test.com","lastAccessedTimestamp":"2022-09-16T14:40:25Z","additionalFactors":[{"factorType":"totp","name":"HMRC App"}]}"""
          val res = buildTestOnlyRequest(url, followRedirects = true)
            .addCookies(DefaultWSCookie("mdtp", authCookie))
            .addHttpHeaders(xSessionId, csrfContent)
            .withBody(Json.obj())
            .get()

          whenReady(res) { resp =>
            resp.status shouldBe NON_AUTHORITATIVE_INFORMATION
            resp.body shouldBe expectedResponse
          }
        }
      }
    }
  }
  "GET /protect-tax-info/test-only.auth/enrolments" should {
    s"return enrolments and $OK" in {

      val authResponse = authoriseResponseJson(enrolments = saEnrolmentOnly)
      stubAuthorizePost(OK, authResponse.toString())
      val res = buildTestOnlyRequest("/protect-tax-info/test-only/auth/enrolments")
        .addCookies(DefaultWSCookie("mdtp", authCookie))
        .addHttpHeaders(xSessionId, csrfContent)
        .withBody(Json.obj())
        .get()

      whenReady(res) { resp =>
        resp.status shouldBe OK
        resp.json shouldBe Json.toJson(Set(saEnrolmentAsCaseClass))(EnrolmentsFormats.writes)
      }
    }
  }

  "GET /sa/test-only/start" should {
    s"return $OK with success message" in {
      val res = buildTestOnlyRequest("/sa/test-only/start")
        .addCookies(DefaultWSCookie("mdtp", authCookie))
        .addHttpHeaders(xSessionId, csrfContent)
        .get()

      whenReady(res) { resp =>
        resp.status shouldBe OK
        resp.body shouldBe "Successful Redirect to SA"
      }
    }
  }


  "PUT /tax-enrolments/test-only/service/HMRC-PT/enrolment" should {
    s"return $OK" in {
      val res = buildTestOnlyRequest("/tax-enrolments/test-only/service/HMRC-PT/enrolment")
        .addCookies(DefaultWSCookie("mdtp", authCookie))
        .addHttpHeaders(xSessionId, csrfContent)
        .put(Json.obj())

      whenReady(res) { resp =>
        resp.status shouldBe NO_CONTENT
      }
    }
  }

  "PUT /auth/test-only/enrolments" should {
    s"return $NO_CONTENT" in {
      val res = buildTestOnlyRequest("/auth/test-only/enrolments")
        .addCookies(DefaultWSCookie("mdtp", authCookie))
        .addHttpHeaders(xSessionId, csrfContent)
        .put(Json.obj())

      whenReady(res) { resp =>
        resp.status shouldBe OK
      }
    }
  }

}
