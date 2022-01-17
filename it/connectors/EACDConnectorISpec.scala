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
import play.api.http.Status
import play.api.libs.json.Json
import uk.gov.hmrc.taxenrolmentassignmentfrontend.connectors.EACDConnector
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.UnexpectedResponseFromEACD
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.UsersAssignedEnrolment

class EACDConnectorISpec extends IntegrationSpecBase {

  lazy val connector: EACDConnector = app.injector.instanceOf[EACDConnector]

  "getUsersWithAssignedEnrolment" when {
    val ENROLMENT_KEY = s"HMRC-PT~NINO~$NINO"
    val PATH =
      s"/enrolment-store-proxy/enrolment-store/enrolments/$ENROLMENT_KEY/users"

    s"no users have the $ENROLMENT_KEY enrolment" should {
      "return None" in {
        stubGet(PATH, Status.NO_CONTENT, "")
        whenReady(connector.getUsersWithAssignedEnrolment(ENROLMENT_KEY).value) {
          response =>
            response shouldBe Right(None)
        }
      }
    }

    s"a user exists with the $ENROLMENT_KEY" should {
      "return the users credentialId" in {
        val eacdResponse =
          UsersAssignedEnrolment(List(CREDENTIAL_ID), List.empty)
        stubGet(PATH, Status.OK, Json.toJson(eacdResponse).toString())
        whenReady(connector.getUsersWithAssignedEnrolment(ENROLMENT_KEY).value) {
          response =>
            response shouldBe Right(Some(eacdResponse))
        }
      }
    }

    "a BAD_REQUEST is returned" should {
      "return an UnexpectedResponseFromIV error" in {
        stubGet(PATH, Status.BAD_REQUEST, "")
        whenReady(connector.getUsersWithAssignedEnrolment(ENROLMENT_KEY).value) {
          response =>
            response shouldBe Left(UnexpectedResponseFromEACD)
        }
      }
    }

    "a 5xx is returned" should {
      "return an UnexpectedResponseFromIV error" in {
        stubGet(PATH, Status.INTERNAL_SERVER_ERROR, "")
        whenReady(connector.getUsersWithAssignedEnrolment(ENROLMENT_KEY).value) {
          response =>
            response shouldBe Left(UnexpectedResponseFromEACD)
        }
      }
    }
  }

  "getUsersWithPTEnrolment" when {
    val ENROLMENT_KEY = s"HMRC-PT~NINO~$NINO"
    val PATH =
      s"/enrolment-store-proxy/enrolment-store/enrolments/$ENROLMENT_KEY/users"

    s"no users have the $ENROLMENT_KEY enrolment" should {
      "return None" in {
        stubGet(PATH, Status.NO_CONTENT, "")
        whenReady(connector.getUsersWithAssignedEnrolment(ENROLMENT_KEY).value) {
          response =>
            response shouldBe Right(None)
        }
      }
    }

    s"a user exists with the $ENROLMENT_KEY" should {
      "return the users credentialId" in {
        val eacdResponse =
          UsersAssignedEnrolment(List(CREDENTIAL_ID), List.empty)
        stubGet(PATH, Status.OK, Json.toJson(eacdResponse).toString())
        whenReady(connector.getUsersWithAssignedEnrolment(ENROLMENT_KEY).value) {
          response =>
            response shouldBe Right(Some(eacdResponse))
        }
      }
    }
  }
}
