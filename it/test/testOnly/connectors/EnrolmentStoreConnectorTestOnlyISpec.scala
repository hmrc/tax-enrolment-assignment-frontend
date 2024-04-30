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
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.IdentifiersOrVerifiers
import uk.gov.hmrc.taxenrolmentassignmentfrontend.testOnly.connectors.EnrolmentStoreConnectorTestOnly
import uk.gov.hmrc.taxenrolmentassignmentfrontend.testOnly.models.EnrolmentDetailsTestOnly

class EnrolmentStoreConnectorTestOnlyISpec extends IntegrationSpecBase {

  lazy val connector: EnrolmentStoreConnectorTestOnly = app.injector.instanceOf[EnrolmentStoreConnectorTestOnly]

  "deleteEnrolment" should {

    val enrolmentKey = "SERVICE~KEY~VALUE"
    val apiUrl = s"/enrolment-store-proxy/enrolment-store/enrolments/$enrolmentKey"

    "delete enrolment" when {
      "response is NO_CONTENT" in {
        stubDelete(apiUrl, Status.NO_CONTENT)
        whenReady(connector.deleteEnrolment(enrolmentKey).value) { response =>
          response shouldBe Right(())
        }
      }

      "response is NOT_FOUND" in {
        stubDelete(apiUrl, Status.NOT_FOUND)
        whenReady(connector.deleteEnrolment(enrolmentKey).value) { response =>
          response shouldBe Right(())
        }
      }
    }

    "return an UpstreamUnexpected2XX error" in {
      stubDelete(apiUrl, Status.OK)
      whenReady(connector.deleteEnrolment(enrolmentKey).value) { response =>
        response shouldBe a[Left[UpstreamUnexpected2XX, _]]
      }
    }

    "return an UpstreamError error" in {
      stubDelete(apiUrl, Status.INTERNAL_SERVER_ERROR)
      whenReady(connector.deleteEnrolment(enrolmentKey).value) { response =>
        response shouldBe a[Left[UpstreamError, _]]
      }
    }
  }

  "upsertEnrolment" should {

    val identifier = IdentifiersOrVerifiers("KEY", "VALUE")
    val enrolment = EnrolmentDetailsTestOnly(
      "serviceName",
      identifier,
      List.empty,
      "Friendly Name",
      "State",
      "enrolment type"
    )
    val requestBody =
      """
        |{
        |  "verifiers" : [ ]
        |}
        |""".stripMargin
    val apiUrl =
      s"/enrolment-store-proxy/enrolment-store/enrolments/${enrolment.serviceName}~${identifier.key}~${identifier.value}"

    "insert enrolment" when {
      "response is NO_CONTENT" in {
        stubPutWithRequestBody(apiUrl, Status.NO_CONTENT, requestBody, "")
        whenReady(connector.upsertEnrolment(enrolment).value) { response =>
          response shouldBe Right(())
        }
      }
    }

    "return an UpstreamUnexpected2XX error" in {
      stubPutWithRequestBody(apiUrl, Status.OK, requestBody, "")
      whenReady(connector.upsertEnrolment(enrolment).value) { response =>
        response shouldBe a[Left[UpstreamUnexpected2XX, _]]
      }
    }

    "return an UpstreamError error" in {
      stubPutWithRequestBody(apiUrl, Status.INTERNAL_SERVER_ERROR, requestBody, "")
      whenReady(connector.upsertEnrolment(enrolment).value) { response =>
        response shouldBe a[Left[UpstreamError, _]]
      }
    }
  }

  "getEnrolmentsFromGroup" should {

    case class Enrolment(serviceName: String, key: String, value: String) {
      override def toString = s"$serviceName~$key~$value"
    }

    val enrolment1 = Enrolment("SERVICE1", "IR-SA", "1")
    val enrolment2 = Enrolment("SERVICE2", "IR-SA", "2")
    val groupId = "groupId"
    val responseBody =
      s"""
         |{
         |    "startRecord": 1,
         |    "totalRecords": 2,
         |    "enrolments": [
         |        {
         |           "service": "${enrolment1.serviceName}",
         |           "state": "Activated",
         |           "friendlyName": "My First Client's SA Enrolment",
         |           "enrolmentDate": "2018-10-05T14:48:00.000Z",
         |           "failedActivationCount": 1,
         |           "activationDate": "2018-10-13T17:36:00.000Z",
         |           "identifiers": [
         |              {
         |                 "key": "${enrolment1.key}",
         |                 "value": "${enrolment1.value}"
         |              }
         |           ]
         |        },
         |        {
         |           "service": "${enrolment2.serviceName}",
         |           "state": "Activated",
         |           "friendlyName": "My Second Client's SA Enrolment",
         |           "enrolmentDate": "2017-06-25T12:24:00.000Z",
         |           "failedActivationCount": 1,
         |           "activationDate": "2017-07-01T09:52:00.000Z",
         |           "identifiers": [
         |              {
         |                 "key": "${enrolment2.key}",
         |                 "value": "${enrolment2.value}"
         |              }
         |           ]
         |        }
         |    ]
         |}
         |""".stripMargin
    val apiUrl =
      s"/enrolment-store-proxy/enrolment-store/groups/$groupId/enrolments"

    "get enrolments" when {
      "response is NO_CONTENT" in {
        stubGet(apiUrl, Status.NO_CONTENT, "{}")
        whenReady(connector.getEnrolmentsFromGroup(groupId).value) { response =>
          response shouldBe Right(List.empty)
        }
      }

      "response is OK" in {
        stubGet(apiUrl, Status.OK, responseBody)
        whenReady(connector.getEnrolmentsFromGroup(groupId).value) { response =>
          response shouldBe Right(List(enrolment1.toString, enrolment2.toString))
        }
      }
    }

    "return an UpstreamError error" in {
      stubGet(apiUrl, Status.INTERNAL_SERVER_ERROR, "")
      whenReady(connector.getEnrolmentsFromGroup(groupId).value) { response =>
        response shouldBe a[Left[UpstreamError, _]]
      }
    }
  }

  "getEnrolmentsFromUser" should {

    case class Enrolment(serviceName: String, key: String, value: String) {
      override def toString = s"$serviceName~$key~$value"
    }

    val enrolment1 = Enrolment("SERVICE1", "IR-SA", "1")
    val enrolment2 = Enrolment("SERVICE2", "IR-SA", "2")
    val credId = "credId"
    val responseBody =
      s"""
         |{
         |    "startRecord": 1,
         |    "totalRecords": 2,
         |    "enrolments": [
         |        {
         |           "service": "${enrolment1.serviceName}",
         |           "state": "Activated",
         |           "friendlyName": "My First Client's SA Enrolment",
         |           "enrolmentDate": "2018-10-05T14:48:00.000Z",
         |           "failedActivationCount": 1,
         |           "activationDate": "2018-10-13T17:36:00.000Z",
         |           "identifiers": [
         |              {
         |                 "key": "${enrolment1.key}",
         |                 "value": "${enrolment1.value}"
         |              }
         |           ]
         |        },
         |        {
         |           "service": "${enrolment2.serviceName}",
         |           "state": "Activated",
         |           "friendlyName": "My Second Client's SA Enrolment",
         |           "enrolmentDate": "2017-06-25T12:24:00.000Z",
         |           "failedActivationCount": 1,
         |           "activationDate": "2017-07-01T09:52:00.000Z",
         |           "identifiers": [
         |              {
         |                 "key": "${enrolment2.key}",
         |                 "value": "${enrolment2.value}"
         |              }
         |           ]
         |        }
         |    ]
         |}
         |""".stripMargin
    val apiUrl =
      s"/enrolment-store-proxy/enrolment-store/users/$credId/enrolments"

    "get enrolments" when {
      "response is NO_CONTENT" in {
        stubGet(apiUrl, Status.NO_CONTENT, "{}")
        whenReady(connector.getEnrolmentsFromUser(credId).value) { response =>
          response shouldBe Right(List.empty)
        }
      }

      "response is OK" in {
        stubGet(apiUrl, Status.OK, responseBody)
        whenReady(connector.getEnrolmentsFromUser(credId).value) { response =>
          response shouldBe Right(List(enrolment1.toString, enrolment2.toString))
        }
      }
    }

    "return an UpstreamError error" in {
      stubGet(apiUrl, Status.INTERNAL_SERVER_ERROR, "")
      whenReady(connector.getEnrolmentsFromUser(credId).value) { response =>
        response shouldBe a[Left[UpstreamError, _]]
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
    val apiUrl =
      s"/enrolment-store-proxy/enrolment-store/enrolments/$enrolmentKey/groups"

    "get enrolments" when {
      "response is NO_CONTENT" in {
        stubGet(apiUrl, Status.NO_CONTENT, "{}")
        whenReady(connector.getGroupsFromEnrolment(enrolmentKey).value) { response =>
          response shouldBe Right(List.empty)
        }
      }

      "response is OK" in {
        stubGet(apiUrl, Status.OK, responseBody)
        whenReady(connector.getGroupsFromEnrolment(enrolmentKey).value) { response =>
          response shouldBe Right(
            List(
              "c0506dd9-1feb-400a-bf70-6351e1ff7510",
              "c0506dd9-1feb-400a-bf70-6351e1ff7512",
              "c0506dd9-1feb-400a-bf70-6351e1ff7513"
            )
          )
        }
      }
    }

    "return an UpstreamError error" in {
      stubGet(apiUrl, Status.INTERNAL_SERVER_ERROR, "")
      whenReady(connector.getGroupsFromEnrolment(enrolmentKey).value) { response =>
        response shouldBe a[Left[UpstreamError, _]]
      }
    }
  }

  "getUsersFromEnrolment" should {

    val enrolmentKey = "SERVICE~KEY~VALUE"
    val responseBody =
      s"""
         |{
         |    "principalUserIds": [
         |       "ABCEDEFGI1234567",
         |       "ABCEDEFGI1234568"
         |    ],
         |    "delegatedUserIds": [
         |       "ABCEDEFGI1234567",
         |       "ABCEDEFGI1234568"
         |    ]
         |}
         |""".stripMargin
    val apiUrl =
      s"/enrolment-store-proxy/enrolment-store/enrolments/$enrolmentKey/users"

    "get enrolments" when {
      "response is NO_CONTENT" in {
        stubGet(apiUrl, Status.NO_CONTENT, "{}")
        whenReady(connector.getUsersFromEnrolment(enrolmentKey).value) { response =>
          response shouldBe Right(List.empty)
        }
      }

      "response is OK" in {
        stubGet(apiUrl, Status.OK, responseBody)
        whenReady(connector.getUsersFromEnrolment(enrolmentKey).value) { response =>
          response shouldBe Right(List("ABCEDEFGI1234567", "ABCEDEFGI1234568", "ABCEDEFGI1234567", "ABCEDEFGI1234568"))
        }
      }
    }

    "return an UpstreamError error" in {
      stubGet(apiUrl, Status.INTERNAL_SERVER_ERROR, "")
      whenReady(connector.getUsersFromEnrolment(enrolmentKey).value) { response =>
        response shouldBe a[Left[UpstreamError, _]]
      }
    }
  }

  "addEnrolmentToGroup" should {

    val groupId = "groupId"
    val credId = "credId"
    val enrolment = EnrolmentDetailsTestOnly(
      "SERVICE",
      IdentifiersOrVerifiers("KEY", "VALUE"),
      List(IdentifiersOrVerifiers("KEY2", "VALUE2")),
      "friendly name",
      "state",
      "enrolment type"
    )
    val requestBody =
      s"""
         |{
         |    "userId" : "$credId",
         |    "type":         "principal",
         |    "action" :       "enrolAndActivate"
         |
         |}
         |""".stripMargin
    val apiUrl =
      s"/enrolment-store-proxy/enrolment-store/groups/$groupId/enrolments/${enrolment.serviceName}~${enrolment.identifiers.key}~${enrolment.identifiers.value}"

    "add enrolment to group" when {
      "response is CREATED" in {
        stubPost(apiUrl, requestBody, Status.CREATED, "{}")
        whenReady(connector.addEnrolmentToGroup(groupId, credId, enrolment).value) { response =>
          response shouldBe Right(())
        }
      }
    }

    "return an UpstreamUnexpected2XX error" in {
      stubPost(apiUrl, requestBody, Status.OK, "{}")
      whenReady(connector.addEnrolmentToGroup(groupId, credId, enrolment).value) { response =>
        response shouldBe Left(UpstreamUnexpected2XX("{}", Status.OK))
      }
    }

    "return an UpstreamError error" in {
      stubPost(apiUrl, requestBody, Status.INTERNAL_SERVER_ERROR, "{}")
      whenReady(connector.addEnrolmentToGroup(groupId, credId, enrolment).value) { response =>
        response shouldBe a[Left[UpstreamError, _]]
      }
    }
  }

  "deleteEnrolmentFromGroup" should {

    val groupId = "groupId"
    val enrolmentKey = "SERVICE~KEY~VALUE"
    val apiUrl =
      s"/enrolment-store-proxy/enrolment-store/groups/$groupId/enrolments/$enrolmentKey"

    "delete enrolment from group" when {
      "response is NO_CONTENT" in {
        stubDelete(apiUrl, Status.NO_CONTENT)
        whenReady(connector.deleteEnrolmentFromGroup(enrolmentKey, groupId).value) { response =>
          response shouldBe Right(())
        }
      }

      "response is NOT_FOUND" in {
        stubDelete(apiUrl, Status.NOT_FOUND)
        whenReady(connector.deleteEnrolmentFromGroup(enrolmentKey, groupId).value) { response =>
          response shouldBe Right(())
        }
      }
    }

    "return an UpstreamUnexpected2XX error" in {
      stubDelete(apiUrl, Status.OK)
      whenReady(connector.deleteEnrolmentFromGroup(enrolmentKey, groupId).value) { response =>
        response shouldBe Left(UpstreamUnexpected2XX("", Status.OK))
      }
    }

    "return an UpstreamError error" in {
      stubDelete(apiUrl, Status.INTERNAL_SERVER_ERROR)
      whenReady(connector.deleteEnrolmentFromGroup(enrolmentKey, groupId).value) { response =>
        response shouldBe a[Left[UpstreamError, _]]
      }
    }
  }

  "deleteEnrolmentFromUser" should {

    val credId = "credId"
    val enrolmentKey = "SERVICE~KEY~VALUE"
    val apiUrl =
      s"/enrolment-store-proxy/enrolment-store/users/$credId/enrolments/$enrolmentKey"

    "delete enrolment from user" when {
      "response is NO_CONTENT" in {
        stubDelete(apiUrl, Status.NO_CONTENT)
        whenReady(connector.deleteEnrolmentFromUser(enrolmentKey, credId).value) { response =>
          response shouldBe Right(())
        }
      }

      "response is NOT_FOUND" in {
        stubDelete(apiUrl, Status.NOT_FOUND)
        whenReady(connector.deleteEnrolmentFromUser(enrolmentKey, credId).value) { response =>
          response shouldBe Right(())
        }
      }
    }

    "return an UpstreamUnexpected2XX error" in {
      stubDelete(apiUrl, Status.OK)
      whenReady(connector.deleteEnrolmentFromUser(enrolmentKey, credId).value) { response =>
        response shouldBe Left(UpstreamUnexpected2XX("", Status.OK))
      }
    }

    "return an UpstreamError error" in {
      stubDelete(apiUrl, Status.INTERNAL_SERVER_ERROR)
      whenReady(connector.deleteEnrolmentFromUser(enrolmentKey, credId).value) { response =>
        response shouldBe a[Left[UpstreamError, _]]
      }
    }
  }
}
