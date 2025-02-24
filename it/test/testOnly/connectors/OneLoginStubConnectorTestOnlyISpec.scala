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

import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, get, urlPathEqualTo}
import helpers.IntegrationSpecBase
import play.api.http.Status
import play.api.libs.json.Json
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.{UpstreamError, UpstreamUnexpected2XX}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.testOnly.connectors.OneLoginStubConnectorTestOnly
import uk.gov.hmrc.taxenrolmentassignmentfrontend.testOnly.models.{AccountDetailsTestOnly, UserTestOnly}

class OneLoginStubConnectorTestOnlyISpec extends IntegrationSpecBase {
  lazy val connector: OneLoginStubConnectorTestOnly =
    app.injector.instanceOf[OneLoginStubConnectorTestOnly]

  "postAccount" must {
    val credId = "credId"
    val apiUrl = "/one-login-stub/test/accounts"
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
            "eacdUserId"           -> credId,
            "identityProviderId"   -> credId,
            "identityProviderType" -> "SCP",
            "email"                -> "email"
          )
          .toString()

        stubPost(apiUrl, requestBody, Status.CREATED, """{"centralAuthUser": { "_id": "uuid"}}""")
        whenReady(connector.postAccount(account).value) { response =>
          response shouldBe Right("uuid")
        }
      }
    }

    "return an UpstreamUnexpected2XX error" in {
      val requestBody = Json
        .obj(
          "eacdUserId"           -> credId,
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
          "eacdUserId"           -> credId,
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

  "delete account" must {
    val eacdUserId = "eacdUserId"
    val apiUrl = s"/one-login-stub/test/accounts/$eacdUserId"
    "delete the account and return OK" in {

      stubDelete(apiUrl, Status.OK)

      whenReady(connector.deleteAccount(eacdUserId).value) { response =>
        response shouldBe Right(())

      }
    }
    "return an error when unexpected response is given" in {
      stubDelete(apiUrl, Status.CREATED)

      whenReady(connector.deleteAccount(eacdUserId).value) { response =>
        response shouldBe a[Left[UpstreamError, _]]

      }
    }

    "return an error when database responds with an error" in {
      stubDelete(apiUrl, Status.BAD_REQUEST)

      whenReady(connector.deleteAccount(eacdUserId).value) { response =>
        response shouldBe a[Left[UpstreamError, _]]

      }
    }
  }
  "get account" must {
    val identityProviderId = "id"
    val apiUrl = s"/one-login-stub/test/accounts?identityProviderId=$identityProviderId"
    "return the account with caUserID and OK status" in {
      val returnedBody =
        s"""
           |{
           |    "caUserId": "12345"
           |}
           |""".stripMargin

      stubGetMatching(apiUrl, Status.CREATED, returnedBody)

      whenReady(connector.getAccount(identityProviderId).value) { response =>
        response shouldBe Right(Some("12345"))

      }
    }
    "return None when no user is found with matching eacdUserId" in {
      stubGetMatching(apiUrl, Status.BAD_REQUEST, "")

      whenReady(connector.getAccount(identityProviderId).value) { response =>
        response shouldBe Right(None)

      }
    }
  }
}
