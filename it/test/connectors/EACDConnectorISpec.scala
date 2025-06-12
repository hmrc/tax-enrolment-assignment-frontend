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

import helpers.IntegrationSpecBase
import helpers.TestITData._
import play.api.http.Status
import play.api.http.Status.OK
import play.api.libs.json.{JsString, Json}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.connectors.EACDConnector
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.{UnexpectedResponseFromEACD, UpstreamError}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.{UserEnrolmentsListResponse, UsersAssignedEnrolment}

class EACDConnectorISpec extends IntegrationSpecBase {

  lazy val connector: EACDConnector = app.injector.instanceOf[EACDConnector]

  "getUsersWithAssignedEnrolment" when {
    val ENROLMENT_KEY = s"HMRC-PT~NINO~$NINO"
    val PATH          =
      s"/enrolment-store-proxy/enrolment-store/enrolments/$ENROLMENT_KEY/users"

    s"no users have the $ENROLMENT_KEY enrolment" should {
      "return None" in {
        stubPost(s"/write/.*", OK, """{"x":2}""")
        stubGet(PATH, Status.NO_CONTENT, "")
        whenReady(connector.getUsersWithAssignedEnrolment(ENROLMENT_KEY).value) { response =>
          response shouldBe Right(UsersAssignedEnrolment(None))
        }
      }
    }

    s"a user exists with the $ENROLMENT_KEY"      should {
      "return the users credentialId" in {
        val eacdResponse = Json.obj(
          ("principalUserIds", Json.arr(JsString(CREDENTIAL_ID))),
          ("delegatedUserIds", Json.arr())
        )
        stubPost(s"/write/.*", OK, """{"x":2}""")
        stubGet(PATH, Status.OK, eacdResponse.toString())
        whenReady(connector.getUsersWithAssignedEnrolment(ENROLMENT_KEY).value) { response =>
          response shouldBe Right(UsersAssignedEnrolment(Some(CREDENTIAL_ID)))
        }
      }
    }

    "a BAD_REQUEST is returned" should {
      "return an UnexpectedResponseFromIV error" in {
        stubGet(PATH, Status.BAD_REQUEST, "")
        stubPost(s"/write/.*", OK, """{"x":2}""")
        whenReady(connector.getUsersWithAssignedEnrolment(ENROLMENT_KEY).value) { response =>
          response shouldBe Left(UnexpectedResponseFromEACD)
        }
      }
    }

    "a 5xx is returned" should {
      "return an UnexpectedResponseFromIV error" in {
        stubGet(PATH, Status.INTERNAL_SERVER_ERROR, "")
        stubPost(s"/write/.*", OK, """{"x":2}""")
        whenReady(connector.getUsersWithAssignedEnrolment(ENROLMENT_KEY).value) { response =>
          response shouldBe Left(UnexpectedResponseFromEACD)
        }
      }
    }
  }

  "getUsersWithPTEnrolment" when {
    val ENROLMENT_KEY = s"HMRC-PT~NINO~$NINO"
    val PATH          =
      s"/enrolment-store-proxy/enrolment-store/enrolments/$ENROLMENT_KEY/users"

    s"no users have the $ENROLMENT_KEY enrolment" should {
      "return None" in {
        stubGet(PATH, Status.NO_CONTENT, "")
        stubPost(s"/write/.*", OK, """{"x":2}""")
        whenReady(connector.getUsersWithAssignedEnrolment(ENROLMENT_KEY).value) { response =>
          response shouldBe Right(UsersAssignedEnrolment(None))
        }
      }
    }

    s"a user exists with the $ENROLMENT_KEY"      should {
      "return the users credentialId" in {
        val eacdResponse = Json.obj(
          ("principalUserIds", Json.arr(JsString(CREDENTIAL_ID))),
          ("delegatedUserIds", Json.arr())
        )
        stubGet(PATH, Status.OK, eacdResponse.toString())
        stubPost(s"/write/.*", OK, """{"x":2}""")
        whenReady(connector.getUsersWithAssignedEnrolment(ENROLMENT_KEY).value) { response =>
          response shouldBe Right(UsersAssignedEnrolment(Some(CREDENTIAL_ID)))
        }
      }
    }
  }

  "getUsersWithSAEnrolment" when {
    val ENROLMENT_KEY = s"IR-SA~UTR~$UTR"
    val PATH          =
      s"/enrolment-store-proxy/enrolment-store/enrolments/$ENROLMENT_KEY/users"

    s"no users have the $ENROLMENT_KEY enrolment" should {
      "return None" in {
        stubGet(PATH, Status.NO_CONTENT, "")
        stubPost(s"/write/.*", OK, """{"x":2}""")
        whenReady(connector.getUsersWithAssignedEnrolment(ENROLMENT_KEY).value) { response =>
          response shouldBe Right(UsersAssignedEnrolment(None))
        }
      }
    }

    s"a user exists with the $ENROLMENT_KEY"      should {
      "return the users credentialId" in {
        val eacdResponse = Json.obj(
          ("principalUserIds", Json.arr(JsString(CREDENTIAL_ID))),
          ("delegatedUserIds", Json.arr())
        )
        stubGet(PATH, Status.OK, eacdResponse.toString())
        stubPost(s"/write/.*", OK, """{"x":2}""")
        whenReady(connector.getUsersWithAssignedEnrolment(ENROLMENT_KEY).value) { response =>
          response shouldBe Right(UsersAssignedEnrolment(Some(CREDENTIAL_ID)))
        }
      }
    }
  }

  "queryKnownFactsByNinoVerifier" when {
    val PATH =
      s"/enrolment-store-proxy/enrolment-store/enrolments"

    s"no users have the IR-SA enrolment"      should {
      "return None" in {
        stubPost(PATH, Status.NO_CONTENT, "")
        stubPost(s"/write/.*", OK, """{"x":2}""")
        whenReady(connector.queryKnownFactsByNinoVerifier(NINO).value) { response =>
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
        whenReady(connector.queryKnownFactsByNinoVerifier(NINO).value) { response =>
          response shouldBe Right(Some("1234567890"))
        }
      }
    }

    "a NOT_FOUND is returned" should {
      "return an UnexpectedResponseFromIV error" in {
        stubPost(PATH, Status.NOT_FOUND, "")
        stubPost(s"/write/.*", OK, """{"x":2}""")
        whenReady(connector.queryKnownFactsByNinoVerifier(NINO).value) { response =>
          response shouldBe Left(UnexpectedResponseFromEACD)
        }
      }
    }

    "a BAD_REQUEST is returned" should {
      "return an UnexpectedResponseFromIV error" in {
        stubPost(PATH, Status.BAD_REQUEST, "")
        stubPost(s"/write/.*", OK, """{"x":2}""")
        whenReady(connector.queryKnownFactsByNinoVerifier(NINO).value) { response =>
          response shouldBe Left(UnexpectedResponseFromEACD)
        }
      }
    }

    "a 5xx is returned" should {
      "return an UnexpectedResponseFromIV error" in {
        stubPost(PATH, Status.INTERNAL_SERVER_ERROR, "")
        stubPost(s"/write/.*", OK, """{"x":2}""")
        whenReady(connector.queryKnownFactsByNinoVerifier(NINO).value) { response =>
          response shouldBe Left(UnexpectedResponseFromEACD)
        }
      }
    }
  }

  "queryEnrolmentsAssignedToUser" when {
    val USER_ID = "123456"
    val PATH    =
      s"/enrolment-store-proxy/enrolment-store/users/$USER_ID/enrolments"

    s"the user has no enrolments"       should {
      "return None" in {
        stubPost(s"/write/.*", OK, """{"x":2}""")
        stubGet(PATH, Status.NO_CONTENT, "")
        whenReady(connector.queryEnrolmentsAssignedToUser(USER_ID).value) { response =>
          response shouldBe Right(None)
        }
      }
    }

    s"the user has multiple enrolments" should {
      "return a list of enrolments" in {
        val eacdResponse = UserEnrolmentsListResponse(
          Seq(userEnrolmentIRPAYE, userEnrolmentIRSA)
        )

        stubPost(s"/write/.*", OK, """{"x":2}""")
        stubGet(PATH, Status.OK, Json.toJson(eacdResponse).toString())
        whenReady(connector.queryEnrolmentsAssignedToUser(USER_ID).value) { response =>
          response shouldBe Right(Some(eacdResponse))
        }
      }
    }

    "a BAD_REQUEST is returned" should {
      "return an UnexpectedResponseFromEACD error" in {
        stubGet(PATH, Status.BAD_REQUEST, "")
        stubPost(s"/write/.*", OK, """{"x":2}""")
        whenReady(connector.queryEnrolmentsAssignedToUser(USER_ID).value) { response =>
          response shouldBe Left(UnexpectedResponseFromEACD)
        }
      }
    }

    "a 5xx is returned" should {
      "return an UnexpectedResponseFromEACD error" in {
        stubGet(PATH, Status.INTERNAL_SERVER_ERROR, "")
        stubPost(s"/write/.*", OK, """{"x":2}""")
        whenReady(connector.queryEnrolmentsAssignedToUser(USER_ID).value) { response =>
          response shouldBe Left(UnexpectedResponseFromEACD)
        }
      }
    }
  }

  "getGroupsFromEnrolment" should {

    val enrolmentKey = "SERVICE~KEY~VALUE"
    val responseBody =
      s"""
         |{
         |    "principalGroupIds": [
         |       "c0506dd9-1feb-400a-bf70-6351e1ff7510"
         |    ],
         |    "delegatedGroupIds": [
         |       "c0506dd9-1feb-400a-bf70-6351e1ff7512",
         |       "c0506dd9-1feb-400a-bf70-6351e1ff7513"
         |    ]
         |}
         |""".stripMargin
    val apiUrl       =
      s"/enrolment-store-proxy/enrolment-store/enrolments/$enrolmentKey/groups"

    "get enrolments" when {
      "response is NO_CONTENT" in {
        stubGetWithQueryParam(apiUrl, "ignore-assignments", "true", Status.NO_CONTENT, "{}")
        whenReady(connector.getGroupsFromEnrolment(enrolmentKey).value) { response =>
          response.map(_.status) shouldBe Right(Status.NO_CONTENT)
          response.map(_.body)   shouldBe Right("")
        }
      }

      "response is OK" in {
        stubGetWithQueryParam(apiUrl, "ignore-assignments", "true", Status.OK, responseBody)
        whenReady(connector.getGroupsFromEnrolment(enrolmentKey).value) { response =>
          response.map(_.status) shouldBe Right(Status.OK)
          response.map(_.body)   shouldBe Right(responseBody)
        }
      }
    }

    "return an UpstreamError error" in {
      stubGet(apiUrl, Status.INTERNAL_SERVER_ERROR, "")
      whenReady(connector.getGroupsFromEnrolment(enrolmentKey).value) { response =>
        response shouldBe a[Left[UpstreamError, _]]
      }
    }

    "return an UpstreamError error when timing out" in {
      stubGet(apiUrl, Status.OK, responseBody, delay = 10000)
      whenReady(connector.getGroupsFromEnrolment(enrolmentKey).value) { response =>
        response shouldBe a[Left[UpstreamError, _]]
      }
    }
  }

}
