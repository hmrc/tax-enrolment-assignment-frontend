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
import helpers.TestITData.{csrfContent, xSessionId}
import play.api.http.Status
import play.api.libs.json.Json
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.testOnly
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.UsersAssignedEnrolment

class TestOnlyControllerISpec extends IntegrationSpecBase with Status {

  val teaHost = s"localhost:$port"

  override def afterAll(): Unit = {
    super.afterAll()
  }

  s"GET /tax-enrolment-assignment-frontend/test-only/enrolment-store/enrolments/:enrolmentKey/users" should {
    "retrieve the enrolment key from the uri" when {
      "the nino attached matches a record with a PT enrolment" should {
        "redirect to enrolment-check redirectUrl " in {

          val nino = "CP872173B"
          val enrolmentKey = "HMRC-PT~NINO~" + nino
          val es0Url =
            s"/test-only/enrolment-store/enrolments/$enrolmentKey/users"

          val returnUrl = testOnly.routes.TestOnlyController
            .es0Call(enrolmentKey)
            .absoluteURL(false, teaHost)

          val jsonResp =
            UsersAssignedEnrolment(List("6145202884164547"), List.empty)

          val res = buildRequest(es0Url, followRedirects = true)
            .withHttpHeaders(xSessionId, csrfContent)
            .withBody(Json.obj())
            .get()

          whenReady(res) { resp =>
            resp.status shouldBe OK
            resp.body shouldBe s"${Json.toJson(jsonResp)}"
            resp.uri.toString shouldBe returnUrl
          }
        }
      }

      "the nino attached matches a record with a PT enrolment" should {
        "return no content with empty body" in {
          val nino = "JT872173B"
          val enrolmentKey = "HMRC-PT~NINO~" + nino
          val es0Url =
            s"/test-only/enrolment-store/enrolments/$enrolmentKey/users"

          val res = buildRequest(es0Url, followRedirects = true)
            .withHttpHeaders(xSessionId, csrfContent)
            .withBody(Json.obj())
            .get()

          whenReady(res) { resp =>
            resp.status shouldBe NO_CONTENT
            resp.body shouldBe ""
          }
        }
      }
    }
  }

  s"GET /users-group-search/test-only/users/:credId" should {
    "retrieve the users details" when {
      "the credential is recognised" in {

        val credId = "2568836745857979"
        val url = s"/users-group-search/test-only/users/$credId"
        val expectedResponse =
          """{"obfuscatedUserId":"********6037","email":"email1@test.com","lastAccessedTimestamp":"2022-01-16T14:40:25Z","additionalFactors":[{"factorType":"sms","phoneNumber":"07783924321"}]}"""
        val res = buildTestOnlyRequest(url, followRedirects = true)
          .withHttpHeaders(xSessionId, csrfContent)
          .withBody(Json.obj())
          .get()

        whenReady(res) { resp =>
          resp.status shouldBe OK
          resp.body shouldBe expectedResponse
        }
      }

      "return NOT_FOUND " when {
        "the credential is not recognised" in {
          val credId = "2568836745857973"
          val url = s"/users-group-search/test-only/users/$credId"
          val res = buildTestOnlyRequest(url, followRedirects = true)
            .withHttpHeaders(xSessionId, csrfContent)
            .withBody(Json.obj())
            .get()

          whenReady(res) { resp =>
            resp.status shouldBe NOT_FOUND
          }
        }
      }
    }
  }

}
