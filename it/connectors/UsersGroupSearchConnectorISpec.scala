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

package connectors

import helpers.IntegrationSpecBase
import helpers.TestITData._
import helpers.WiremockHelper._
import play.api.http.Status._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.connectors.UsersGroupSearchConnector
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.UnexpectedResponseFromUsersGroupSearch

class UsersGroupSearchConnectorISpec extends IntegrationSpecBase {

  lazy val connector: UsersGroupSearchConnector =
    app.injector.instanceOf[UsersGroupSearchConnector]

  "getUserDetails" when {
    val PATH =
      s"/users-group-search/test-only/users/$CREDENTIAL_ID"
    s"no errors occur" should {
      "return the user details" in {
        stubGet(PATH, OK, usergroupsResponseJson().toString())
        stubPost(s"/write/.*", OK, """{"x":2}""")
        whenReady(connector.getUserDetails(CREDENTIAL_ID).value) { response =>
          response shouldBe Right(usersGroupSearchResponse)
        }
      }
    }

    s"the user has no record in users-group-search" should {
      "return an UnexpectedResponseFromUsersGroupSearch" in {
        stubGet(PATH, NOT_FOUND, "")
        stubPost(s"/write/.*", OK, """{"x":2}""")
        whenReady(connector.getUserDetails(CREDENTIAL_ID).value) { response =>
          response shouldBe Left(UnexpectedResponseFromUsersGroupSearch)
        }
      }
    }

    "a BAD_REQUEST is returned" should {
      "return an UnexpectedResponseFromTaxEnrolments error" in {
        stubGet(PATH, BAD_REQUEST, "")
        stubPost(s"/write/.*", OK, """{"x":2}""")
        whenReady(connector.getUserDetails(CREDENTIAL_ID).value) { response =>
          response shouldBe Left(UnexpectedResponseFromUsersGroupSearch)
        }
      }
    }

    "a UNAUTHORIZED is returned" should {
      "return an UnexpectedResponseFromTaxEnrolments error" in {
        stubGet(PATH, UNAUTHORIZED, "")
        stubPost(s"/write/.*", OK, """{"x":2}""")
        whenReady(connector.getUserDetails(CREDENTIAL_ID).value) { response =>
          response shouldBe Left(UnexpectedResponseFromUsersGroupSearch)
        }
      }
    }

    "a INTERNAL_SERVER_ERROR is returned" should {
      "return an UnexpectedResponseFromTaxEnrolments error" in {
        stubGet(PATH, INTERNAL_SERVER_ERROR, "")
        stubPost(s"/write/.*", OK, """{"x":2}""")
        whenReady(connector.getUserDetails(CREDENTIAL_ID).value) { response =>
          response shouldBe Left(UnexpectedResponseFromUsersGroupSearch)
        }
      }
    }
  }
}
