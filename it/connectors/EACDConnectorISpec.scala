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
import play.api.http.Status.OK
import play.api.libs.json.{JsString, Json}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.connectors.EACDConnector
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.UnexpectedResponseFromEACD
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.UsersAssignedEnrolment

class EACDConnectorISpec extends IntegrationSpecBase {

  lazy val connector: EACDConnector = app.injector.instanceOf[EACDConnector]

  "assignPTEnrolmentToUser" should {
    val userId = "fakeId"
    val ENROLMENT_KEY = s"HMRC-PT~NINO~$NINO"
    val url =
      s"/enrolment-store-proxy/enrolment-store/users/$userId/enrolments/$ENROLMENT_KEY"

    "successfully assign the HMRC-PT Enrolment" in {
      stubPost("/write/audit/merged", Status.NO_CONTENT, "")
      stubPost(url, Status.CREATED, "")
      whenReady(connector.assignPTEnrolmentToUser(userId, NINO).value) {
        response =>
          response shouldBe Right("Success")
      }
    }

    "return a success when the user already has the enrolment" in {
      stubPost("/write/audit/merged", Status.NO_CONTENT, "")
      stubPost(url, Status.CONFLICT, eacdExampleError)
      whenReady(connector.assignPTEnrolmentToUser(userId, NINO).value) {
        response =>
          response shouldBe Right("Duplicate Enrolment Request")
      }
    }

    "return an UnexpectedResponseFromEACD when receiving any 4XX/5XX response" in {
      stubPost("/write/audit/merged", Status.NO_CONTENT, "")
      stubPost(url, Status.BAD_REQUEST, eacdExampleError)
      whenReady(connector.assignPTEnrolmentToUser(userId, NINO).value) {
        response =>
          response shouldBe Left(UnexpectedResponseFromEACD)
      }
    }
  }

  "getUsersWithAssignedEnrolment" when {
    val ENROLMENT_KEY = s"HMRC-PT~NINO~$NINO"
    val PATH =
      s"/enrolment-store-proxy/enrolment-store/enrolments/$ENROLMENT_KEY/users"

    s"no users have the $ENROLMENT_KEY enrolment" should {
      "return None" in {
        stubPost(s"/write/.*", OK, """{"x":2}""")
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
        stubPost(s"/write/.*", OK, """{"x":2}""")
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
        stubPost(s"/write/.*", OK, """{"x":2}""")
        whenReady(connector.getUsersWithAssignedEnrolment(ENROLMENT_KEY).value) {
          response =>
            response shouldBe Left(UnexpectedResponseFromEACD)
        }
      }
    }

    "a 5xx is returned" should {
      "return an UnexpectedResponseFromIV error" in {
        stubGet(PATH, Status.INTERNAL_SERVER_ERROR, "")
        stubPost(s"/write/.*", OK, """{"x":2}""")
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
        stubPost(s"/write/.*", OK, """{"x":2}""")
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
        stubPost(s"/write/.*", OK, """{"x":2}""")
        whenReady(connector.getUsersWithAssignedEnrolment(ENROLMENT_KEY).value) {
          response =>
            response shouldBe Right(Some(eacdResponse))
        }
      }
    }
  }

  "queryKnownFactsByNinoVerifier" when {
    val PATH =
      s"/enrolment-store-proxy/enrolment-store/enrolments"

    s"no users have the IR-SA enrolment" should {
      "return None" in {
        stubPost(PATH, Status.NO_CONTENT, "")
        stubPost(s"/write/.*", OK, """{"x":2}""")
        whenReady(connector.queryKnownFactsByNinoVerifier(NINO).value) {
          response =>
            response shouldBe Right(None)
        }
      }
    }

    s"a user exists with the IR-SA enrolment" should {
      "return the users credentialId" in {
        val eacdResponse = s"""{
                             |    "service": "IR-SA",
                             |    "enrolments": [{
                             |        "identifiers": [{
                             |            "key": "UTR",
                             |            "value": "1234567890"
                             |        }],
                             |        "verifiers": [{
                             |            "key": "NINO",
                             |            "value": "AB112233D"
                             |        },
                             |        {
                             |            "key": "Postcode",
                             |            "value": "SW1A 2AA"
                             |        }]
                             |    }]
                             |}""".stripMargin
        stubPost(PATH, Status.OK, eacdResponse)
        stubPost(s"/write/.*", OK, """{"x":2}""")
        whenReady(connector.queryKnownFactsByNinoVerifier(NINO).value) {
          response =>
            response shouldBe Right(Some("1234567890"))
        }
      }
    }

    "a NOT_FOUND is returned" should {
      "return an UnexpectedResponseFromIV error" in {
        stubPost(PATH, Status.NOT_FOUND, "")
        stubPost(s"/write/.*", OK, """{"x":2}""")
        whenReady(connector.queryKnownFactsByNinoVerifier(NINO).value) {
          response =>
            response shouldBe Left(UnexpectedResponseFromEACD)
        }
      }
    }

    "a BAD_REQUEST is returned" should {
      "return an UnexpectedResponseFromIV error" in {
        stubPost(PATH, Status.BAD_REQUEST, "")
        stubPost(s"/write/.*", OK, """{"x":2}""")
        whenReady(connector.queryKnownFactsByNinoVerifier(NINO).value) {
          response =>
            response shouldBe Left(UnexpectedResponseFromEACD)
        }
      }
    }

    "a 5xx is returned" should {
      "return an UnexpectedResponseFromIV error" in {
        stubPost(PATH, Status.INTERNAL_SERVER_ERROR, "")
        stubPost(s"/write/.*", OK, """{"x":2}""")
        whenReady(connector.queryKnownFactsByNinoVerifier(NINO).value) {
          response =>
            response shouldBe Left(UnexpectedResponseFromEACD)
        }
      }
    }
  }

}
