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

package testOnly.connectors

import helpers.IntegrationSpecBase
import play.api.http.Status
import play.api.libs.json.Json
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.{UpstreamError, UpstreamUnexpected2XX}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.testOnly.connectors.IdentityProviderAccountContextConnectorTestOnly
import uk.gov.hmrc.taxenrolmentassignmentfrontend.testOnly.models.{AccountDetailsTestOnly, UserTestOnly}

class IdentityProviderAccountContextConnectorTestOnlyISpec extends IntegrationSpecBase {
  lazy val connector: IdentityProviderAccountContextConnectorTestOnly =
    app.injector.instanceOf[IdentityProviderAccountContextConnectorTestOnly]

  "postAccount" must {
    val credId = "credId"
    val apiUrl = "/identity-provider-account-context/test-only/accounts"
    val account = AccountDetailsTestOnly(
      "SCP",
      "groupId",
      nino,
      "Individual",
      UserTestOnly(credId, "name", "email"),
      List.empty,
      List.empty
    )

    "create account" when {
      "response is OK" in {
        val requestBody = Json
          .obj(
            "action"               -> "create",
            "identityProviderId"   -> credId,
            "identityProviderType" -> "SCP",
            "email"                -> "email"
          )
          .toString()

        stubPost(apiUrl, requestBody, Status.CREATED, """{"caUserId": "uuid"}""")
        whenReady(connector.postAccount(account).value) { response =>
          response shouldBe Right("uuid")
        }
      }
    }

    "return an UpstreamUnexpected2XX error" in {
      val requestBody = Json
        .obj(
          "action"               -> "create",
          "identityProviderId"   -> credId,
          "identityProviderType" -> "SCP",
          "email"                -> "email"
        )
        .toString()

      stubPost(apiUrl, requestBody, Status.OK, """Invalid 2XX""")
      whenReady(connector.postAccount(account).value) { response =>
        response shouldBe Left(UpstreamUnexpected2XX("Invalid 2XX", Status.OK))
      }
    }

    "return an UpstreamError error" in {
      val requestBody = Json
        .obj(
          "action"               -> "create",
          "identityProviderId"   -> credId,
          "identityProviderType" -> "SCP",
          "email"                -> "email"
        )
        .toString()

      stubPost(apiUrl, requestBody, Status.INTERNAL_SERVER_ERROR, """Server error""")
      whenReady(connector.postAccount(account).value) { response =>
        response shouldBe a[Left[UpstreamError, _]]
      }
    }
  }

  "postIndividual" must {
    val credId = "credId"
    val apiUrl = "/identity-provider-account-context/contexts/individual"
    val account = AccountDetailsTestOnly(
      "SCP",
      "groupId",
      nino,
      "Individual",
      UserTestOnly(credId, "name", "email"),
      List.empty,
      List.empty
    )
    val caUserId = "caUserId"

    val requestBody =
      s"""
         |{
         |  "credId": "$credId",
         |  "nino": "${nino.nino}",
         |  "confidenceLevel": 200
         |}
         |""".stripMargin
    val responseBody = ""

    "insert nino" when {
      "response is OK" in {
        val requestBody = Json
          .obj(
            "caUserId" -> caUserId,
            "nino"     -> nino
          )
          .toString()

        stubPost(apiUrl, requestBody, Status.CREATED, "")
        whenReady(connector.postIndividual(account, caUserId).value) { response =>
          response shouldBe Right(())
        }
      }
    }

    "return an UpstreamUnexpected2XX error" in {
      val requestBody = Json
        .obj(
          "caUserId" -> caUserId,
          "nino"     -> nino
        )
        .toString()

      stubPost(apiUrl, requestBody, Status.OK, "Invalid 2XX")
      whenReady(connector.postIndividual(account, caUserId).value) { response =>
        response shouldBe Left(UpstreamUnexpected2XX("Invalid 2XX", Status.OK))
      }
    }

    "return an UpstreamError error" in {
      val requestBody = Json
        .obj(
          "caUserId" -> caUserId,
          "nino"     -> nino
        )
        .toString()

      stubPost(apiUrl, requestBody, Status.INTERNAL_SERVER_ERROR, "Server error")
      whenReady(connector.postIndividual(account, caUserId).value) { response =>
        response shouldBe a[Left[UpstreamError, _]]
      }
    }
  }
}
