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

package uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers

import play.api.Application
import play.api.inject.bind
import play.api.libs.json.{JsString, Json}
import play.api.mvc.BodyParsers
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.{AffinityGroup, AuthConnector, Enrolments}
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve.{Credentials, Retrieval, ~}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.testOnly.TestOnlyController
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.TestData.{predicates, retrievalResponse, retrievals, saEnrolmentOnly}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.{BaseSpec, ControllersBaseSpec}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.formats.EnrolmentsFormats
import uk.gov.hmrc.taxenrolmentassignmentfrontend.orchestrators.{AccountCheckOrchestrator, MultipleAccountsOrchestrator}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.reporting.AuditHandler
import uk.gov.hmrc.taxenrolmentassignmentfrontend.services.{SilentAssignmentService, ThrottlingService}

import scala.concurrent.{ExecutionContext, Future}

class TestOnlyControllerSpec extends ControllersBaseSpec {

  override implicit lazy val app: Application = localGuiceApplicationBuilder()
    .overrides(
      bind[AuthConnector].toInstance(mockAuthConnector)
    )
    .build()

  lazy val testOnlyController = app.injector.instanceOf[TestOnlyController]

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
      "return the default userdetails" in {
        val credId = "3568836745857979"
        val expectedResponse = {
          Json.obj(
            ("obfuscatedUserId", JsString("********6121")),
            ("email", JsString("email11@test.com")),
            ("lastAccessedTimestamp", JsString("2022-09-16T14:40:25Z")),
            (
              "additionalFactors",
              Json.arr(
                Json.obj(
                  ("factorType", JsString("totp")),
                  ("name", JsString("HMRC App"))
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
  }

  "enrolmentsFromAuth" should {
    "return the enrolments from auth for a user" in {
      (mockAuthConnector
        .authorise(
          _: Predicate,
          _: Retrieval[
            ((Option[String] ~ Option[Credentials]) ~ Enrolments) ~ Option[
              String
            ] ~ Option[AffinityGroup] ~ Option[String]
          ]
        )(_: HeaderCarrier, _: ExecutionContext))
        .expects(predicates, retrievals, *, *)
        .returning(Future.successful(retrievalResponse(enrolments = saEnrolmentOnly)))

      val res = testOnlyController.enrolmentsFromAuth()(fakeReq)
      status(res) shouldBe OK
      contentAsJson(res) shouldBe Json.toJson(saEnrolmentOnly.enrolments)(EnrolmentsFormats.writes)

    }
  }

  "successfulSACall" should {
    s"return $OK with success message" in {
      val res = testOnlyController.successfulSACall()(fakeReq)
      status(res) shouldBe OK
      contentAsString(res) shouldBe "Successful Redirect to SA"
    }
  }

  "authStub" should {
    s"return $OK" in {
      val res = testOnlyController.authStub()(fakeReq)
      status(res) shouldBe OK
    }
  }

  "taxEnrolmentsStub" should {
    s"return $NO_CONTENT" in {
      val res = testOnlyController.taxEnrolmentsStub()(fakeReq)
      status(res) shouldBe NO_CONTENT
    }
  }
}
