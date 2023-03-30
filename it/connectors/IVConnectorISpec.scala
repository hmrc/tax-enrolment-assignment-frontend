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

package connectors

import helpers.{IntegrationSpecBase}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.connectors.IVConnector
import helpers.TestITData._
import play.api.http.Status
import play.api.http.Status.OK
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.UnexpectedResponseFromIV

class IVConnectorISpec extends IntegrationSpecBase {

  lazy val connector: IVConnector = app.injector.instanceOf[IVConnector]
  lazy val path = "/identity-verification/nino"
  "getCredentialsWithNino" when {
    "multiple credentials have the same nino" should {
      "return the list of credentials with confidence levels" in {
        stubGetWithQueryParam(
          path,
          "nino",
          NINO,
          Status.OK,
          ivResponseMultiCredsJsonString
        )
        stubPost(s"/write/.*", OK, """{"x":2}""")
        whenReady(connector.getCredentialsWithNino(NINO).value) { response =>
          response shouldBe Right(multiIVCreds)
        }
      }
    }

    "one credential has the nino" should {
      "return the credential with confidence level" in {
        stubGetWithQueryParam(
          path,
          "nino",
          NINO,
          Status.OK,
          ivResponseSingleCredsJsonString
        )
        stubPost(s"/write/.*", OK, """{"x":2}""")
        whenReady(connector.getCredentialsWithNino(NINO).value) { response =>
          response shouldBe Right(List(ivNinoStoreEntry1))
        }
      }
    }

    "a non 404 is returned" should {
      "return an UnexpectedResponseFromIV error" in {
        stubGetWithQueryParam(path, "nino", NINO, Status.NOT_FOUND, "")
        stubPost(s"/write/.*", OK, """{"x":2}""")
        whenReady(connector.getCredentialsWithNino(NINO).value) { response =>
          response shouldBe Left(UnexpectedResponseFromIV)
        }
      }
    }

    "a non 400 is returned" should {
      "return an UnexpectedResponseFromIV error" in {
        stubGetWithQueryParam(path, "nino", NINO, Status.BAD_REQUEST, "")
        stubPost(s"/write/.*", OK, """{"x":2}""")
        whenReady(connector.getCredentialsWithNino(NINO).value) { response =>
          response shouldBe Left(UnexpectedResponseFromIV)
        }
      }
    }

    "a non 500 is returned" should {
      "return an UnexpectedResponseFromIV error" in {
        stubGetWithQueryParam(
          path,
          "nino",
          NINO,
          Status.INTERNAL_SERVER_ERROR,
          ""
        )
        stubPost(s"/write/.*", OK, """{"x":2}""")
        whenReady(connector.getCredentialsWithNino(NINO).value) { response =>
          response shouldBe Left(UnexpectedResponseFromIV)
        }
      }
    }
  }
}
