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
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.{UpstreamError, UpstreamUnexpected2XX}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.testOnly.connectors.BasStubsConnectorTestOnly
import uk.gov.hmrc.taxenrolmentassignmentfrontend.testOnly.models.{AccountDetailsTestOnly, UserTestOnly}

class BasStubsConnectorTestOnlyISpec extends IntegrationSpecBase {

  lazy val connector: BasStubsConnectorTestOnly = app.injector.instanceOf[BasStubsConnectorTestOnly]

  "putAccount" should {

    val account = AccountDetailsTestOnly(
      "SCP",
      "groupId",
      generateNino,
      "Individual",
      UserTestOnly("credId", "name", "email@example.invalid"),
      List.empty,
      List.empty
    )
    val requestBody =
      """
        |{
        |  "credId": "credId",
        |  "userId": "credId",
        |  "isAdmin": true,
        |  "accountType" : "Individual",
        |  "email": "email@example.invalid",
        |  "emailVerified": true,
        |  "profile": "/profile",
        |  "groupId": "groupId",
        |  "groupProfile": "/group/profile",
        |  "trustId": "trustId",
        |  "name": "Name",
        |  "suspended": false
        |}
        |""".stripMargin

    val apiUrl = "/bas-stubs/account"

    "insert a new account in stubs successfully" in {
      stubPutWithRequestBody(apiUrl, Status.CREATED, requestBody, "")
      whenReady(connector.putAccount(account).value) { response =>
        response shouldBe Right(())
      }
    }

    "return an UpstreamUnexpected2XX error" in {
      stubPutWithRequestBody(apiUrl, Status.OK, requestBody, "")
      whenReady(connector.putAccount(account).value) { response =>
        response shouldBe a[Left[UpstreamUnexpected2XX, _]]
      }
    }

    "return an UpstreamError error" in {
      stubPutWithRequestBody(apiUrl, Status.INTERNAL_SERVER_ERROR, requestBody, "Oops service broken")
      whenReady(connector.putAccount(account).value) { response =>
        response shouldBe a[Left[UpstreamError, _]]
      }
    }
  }

  "putAdditionalFactors" should {
    val account = AccountDetailsTestOnly(
      "SCP",
      "groupId",
      generateNino,
      "Individual",
      UserTestOnly("credId", "name", "email@example.invalid"),
      List.empty,
      List.empty
    )
    val requestBody =
      """
        |{
        |  "sub" : "credId",
        |  "userId" : {
        |    "createdDate" : "2016-10-16T14:40:25Z"
        |  },
        |  "recoveryWord" : true,
        |  "password" : {
        |    "authType" : "password",
        |    "status" : "not locked"
        |  },
        |  "additionalFactors" : [ ]
        |}
        |""".stripMargin

    val apiUrl = "/bas-stubs/credentials/factors"

    "insert a new factors in stubs successfully" in {
      stubPutWithRequestBody(apiUrl, Status.CREATED, requestBody, "")
      whenReady(connector.putAdditionalFactors(account).value) { response =>
        response shouldBe Right(())
      }
    }

    "return an UpstreamUnexpected2XX error" in {
      stubPutWithRequestBody(apiUrl, Status.OK, requestBody, "")
      whenReady(connector.putAdditionalFactors(account).value) { response =>
        response shouldBe Left(UpstreamUnexpected2XX("", Status.OK))
      }
    }

    "return an UpstreamError error" in {
      stubPutWithRequestBody(apiUrl, Status.INTERNAL_SERVER_ERROR, requestBody, "Oops service broken")
      whenReady(connector.putAdditionalFactors(account).value) { response =>
        response shouldBe a[Left[UpstreamError, _]]
      }
    }
  }

  "deleteAdditionalFactors" should {

    val credId = "credId"
    val apiUrl = s"/bas-stubs/credentials/$credId/factors/additionalfactors"

    "delete additional factors" when {
      "response is NO_CONTENT" in {
        stubDelete(apiUrl, Status.NO_CONTENT)
        whenReady(connector.deleteAdditionalFactors(credId).value) { response =>
          response shouldBe Right(())
        }
      }

      "response is NOT_FOUND" in {
        stubDelete(apiUrl, Status.NOT_FOUND)
        whenReady(connector.deleteAdditionalFactors(credId).value) { response =>
          response shouldBe Right(())
        }
      }
    }

    "return an UpstreamUnexpected2XX error" in {
      stubDelete(apiUrl, Status.OK)
      whenReady(connector.deleteAdditionalFactors(credId).value) { response =>
        response shouldBe Left(UpstreamUnexpected2XX("", Status.OK))
      }
    }

    "return an UpstreamError error" in {
      stubDelete(apiUrl, Status.INTERNAL_SERVER_ERROR)
      whenReady(connector.deleteAdditionalFactors(credId).value) { response =>
        response shouldBe a[Left[UpstreamError, _]]
      }
    }
  }
}
