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
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.{AdditonalFactors, IdentifiersOrVerifiers}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.testOnly.connectors.EnrolmentStoreStubConnectorTestOnly
import uk.gov.hmrc.taxenrolmentassignmentfrontend.testOnly.models.{AccountDetailsTestOnly, EnrolmentDetailsTestOnly, UserTestOnly}

class EnrolmentStoreStubConnectorTestOnlyISpec extends IntegrationSpecBase {

  lazy val connector: EnrolmentStoreStubConnectorTestOnly = app.injector.instanceOf[EnrolmentStoreStubConnectorTestOnly]

  "addStubAccount" should {

    val account = AccountDetailsTestOnly(
      "groupId",
      nino,
      "Individual",
      UserTestOnly("credId", "name", "email@example.invalid"),
      List(
        EnrolmentDetailsTestOnly(
          "serviceName",
          IdentifiersOrVerifiers("KEY", "VALUE"),
          List(IdentifiersOrVerifiers("KEY1", "VALUE1")),
          "enrolmentFriendlyName",
          "state",
          "type"
        )
      ),
      List(AdditonalFactors("factorType", None, None))
    )
    val requestBody =
      s"""
         |{
         |  "groupId": "${account.groupId}",
         |  "nino": "$nino",
         |  "affinityGroup": "${account.affinityGroup}",
         |  "users": [
         |    {
         |      "credId": "${account.user.credId}",
         |      "name": "${account.user.name}",
         |      "email": "${account.user.email}",
         |      "description": "Description",
         |      "credentialRole": "Admin"
         |    }
         |  ],
         |  "enrolments": [
         |    {
         |      "serviceName": "${account.enrolments.head.serviceName}",
         |      "identifiers": [
         |        {
         |          "key": "${account.enrolments.head.identifiers.key}",
         |          "value": "${account.enrolments.head.identifiers.value}"
         |        }
         |      ],
         |      "verifiers": [
         |        {
         |          "key": "${account.enrolments.head.verifiers.head.key}",
         |          "value": "${account.enrolments.head.verifiers.head.value}"
         |        }
         |      ],
         |      "assignedUserCreds": [
         |        "${account.user.credId}"
         |      ],
         |      "assignedToAll": false,
         |      "enrolmentFriendlyName": "${account.enrolments.head.enrolmentFriendlyName}",
         |      "state": "state",
         |      "enrolmentType": "type"
         |    }
         |  ]
         |}
         |""".stripMargin
    val apiUrl = "/enrolment-store-stub/data"

    "add account details" when {
      "response is OK" in {
        stubPost(apiUrl, requestBody, Status.NO_CONTENT, "")
        whenReady(connector.addStubAccount(account).value) { response =>
          response shouldBe a[Right[_, AccountDetailsTestOnly]]
        }
      }
    }

    "return an UpstreamUnexpected2XX error" in {
      stubPost(apiUrl, requestBody, Status.OK, "")
      whenReady(connector.addStubAccount(account).value) { response =>
        response shouldBe Left(UpstreamUnexpected2XX("", Status.OK))
      }
    }

    "return an UpstreamError error" in {
      stubGet(apiUrl, Status.INTERNAL_SERVER_ERROR, "Ooops")
      whenReady(connector.addStubAccount(account).value) { response =>
        response shouldBe a[Left[UpstreamError, _]]
      }
    }
  }

  "deleteStubAccount" should {

    val groupId = "groupId"
    val apiUrl = s"/data/group/$groupId"

    "delete account details" when {
      "response is NO_CONTENT" in {
        stubDelete(apiUrl, Status.NO_CONTENT)
        whenReady(connector.deleteStubAccount(groupId).value) { response =>
          response shouldBe Right(())
        }
      }
    }

    "return an UpstreamUnexpected2XX error" in {
      stubDelete(apiUrl, Status.OK)
      whenReady(connector.deleteStubAccount(groupId).value) { response =>
        response shouldBe Left(UpstreamUnexpected2XX("", Status.OK))
      }
    }

    "return an UpstreamError error" in {
      stubDelete(apiUrl, Status.INTERNAL_SERVER_ERROR)
      whenReady(connector.deleteStubAccount(groupId).value) { response =>
        response shouldBe a[Left[UpstreamError, _]]
      }
    }
  }

}
