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
import uk.gov.hmrc.taxenrolmentassignmentfrontend.testOnly.connectors.IdentityVerificationConnectorTestOnly

class IdentityVerificationConnectorTestOnlyISpec extends IntegrationSpecBase {
  lazy val connector: IdentityVerificationConnectorTestOnly =
    app.injector.instanceOf[IdentityVerificationConnectorTestOnly]

  "deleteCredId" must {
    val credId = "credId"
    val apiUrl = s"/test-only/nino/$credId"

    "delete credId" when {
      "response is OK" in {
        stubDelete(apiUrl, Status.OK)
        whenReady(connector.deleteCredId(credId).value) { response =>
          response shouldBe Right(())
        }
      }
    }

    "return an UpstreamUnexpected2XX error" in {
      stubDelete(apiUrl, Status.NO_CONTENT)
      whenReady(connector.deleteCredId(credId).value) { response =>
        response shouldBe Left(UpstreamUnexpected2XX("", Status.NO_CONTENT))
      }
    }

    "return an UpstreamError error" in {
      stubDelete(apiUrl, Status.INTERNAL_SERVER_ERROR)
      whenReady(connector.deleteCredId(credId).value) { response =>
        response shouldBe a[Left[UpstreamError, _]]
      }
    }
  }

  "insertCredId" must {
    val credId = "credId"
    val apiUrl = s"/identity-verification/nino/$credId"
    val requestBody =
      s"""
         |{
         |  "credId": "$credId",
         |  "nino": "${nino.nino}",
         |  "confidenceLevel": 200
         |}
         |""".stripMargin
    val responseBody = ""

    "insert credId" when {
      "response is OK" in {
        stubPutWithRequestBody(apiUrl, Status.OK, requestBody, responseBody)
        whenReady(connector.insertCredId(credId, nino).value) { response =>
          response shouldBe Right(())
        }
      }
    }

    "return an UpstreamUnexpected2XX error" in {
      stubPutWithRequestBody(apiUrl, Status.NO_CONTENT, requestBody, responseBody)
      whenReady(connector.insertCredId(credId, nino).value) { response =>
        response shouldBe Left(UpstreamUnexpected2XX("", Status.NO_CONTENT))
      }
    }

    "return an UpstreamError error" in {
      stubPutWithRequestBody(apiUrl, Status.INTERNAL_SERVER_ERROR, requestBody, responseBody)
      whenReady(connector.insertCredId(credId, nino).value) { response =>
        response shouldBe a[Left[UpstreamError, _]]
      }
    }
  }
}
