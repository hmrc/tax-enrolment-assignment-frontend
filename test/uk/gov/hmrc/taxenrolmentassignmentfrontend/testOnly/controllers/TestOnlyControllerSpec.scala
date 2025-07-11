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

package uk.gov.hmrc.taxenrolmentassignmentfrontend.testOnly.controllers

import cats.data.EitherT
import org.mockito.ArgumentMatchers.{any, eq => ameq}
import org.mockito.Mockito.when
import org.scalatest.matchers.must.Matchers._
import play.api.Application
import play.api.http.Status.{BAD_REQUEST, INTERNAL_SERVER_ERROR, OK}
import play.api.inject.bind
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers.{defaultAwaitTimeout, status}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.{TaxEnrolmentAssignmentErrors, UnexpectedError}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.BaseSpec
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.TestData.{UsersAssignedEnrolmentCurrentCred, buildFakeRequestWithSessionId, predicates, retrievalResponse, retrievals}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.{AdditonalFactors, IdentifiersOrVerifiers}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.services.EACDService
import uk.gov.hmrc.taxenrolmentassignmentfrontend.testOnly.models.{AccountDetailsTestOnly, EnrolmentDetailsTestOnly, UserTestOnly}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.testOnly.services.EnrolmentStoreServiceTestOnly
import uk.gov.hmrc.taxenrolmentassignmentfrontend.testOnly.utils.AccountUtilsTestOnly

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Success

class TestOnlyControllerSpec extends BaseSpec {

  lazy val mockAccountUtilsTestOnly: AccountUtilsTestOnly = mock[AccountUtilsTestOnly]
  lazy val mockFileHelper: FileHelper                     = mock[FileHelper]

  override implicit lazy val app: Application = localGuiceApplicationBuilder()
    .overrides(
      bind[AccountUtilsTestOnly].toInstance(mockAccountUtilsTestOnly),
      bind[FileHelper].toInstance(mockFileHelper)
    )
    .build()

  lazy val sut: TestOnlyController = app.injector.instanceOf[TestOnlyController]

  "create" must {

    "create a single account" in {
      val nino        = generateNino
      val requestBody =
        s"""
           |    {
           |        "groupId": "98ADEA51-C0BA-497D-997E-F585FAADBCEH",
           |        "affinityGroup": "Individual",
           |        "nino": "$nino",
           |        "user": {
           |            "credId": "5217739547427626",
           |            "name": "Firstname Surname",
           |            "email": "email@example.invalid",
           |            "credentialRole": "Admin",
           |            "description": "Description"
           |        },
           |        "enrolments": [
           |            {
           |                "serviceName": "IR-SA",
           |                "assignedUserCreds": [
           |                    "1"
           |                ],
           |                "identifiers":
           |                    {
           |                        "key": "UTR",
           |                        "value": "123456"
           |                    }
           |                ,
           |                "verifiers": [
           |                    {
           |                        "key": "Postcode",
           |                        "value": "postcode"
           |                    },
           |                    {
           |                        "key": "NINO",
           |                        "value": "$nino"
           |                    }
           |                ],
           |                "enrolmentFriendlyName": "IR-SA Enrolment",
           |                "state": "Activated",
           |                "enrolmentType": "principal",
           |                "assignedToAll": false
           |            }
           |        ],
           |        "additionalFactors": [
           |            {
           |                "factorType": "factorType",
           |                "phoneNumber": "Phone number",
           |                "name": "name"
           |            }
           |        ]
           |    }
           |""".stripMargin
      val account     = Json.parse(requestBody).as[AccountDetailsTestOnly]

      when(mockAccountUtilsTestOnly.deleteAccountDetails(ameq(account))(any()))
        .thenReturn(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](()))

      when(mockAccountUtilsTestOnly.insertAccountDetails(ameq(account))(any()))
        .thenReturn(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](()))

      val request = FakeRequest()
        .withMethod("POST")
        .withJsonBody(Json.parse(requestBody))

      val result = sut.create.apply(request)

      status(result) mustBe OK
    }

    "create multiple accounts" in {
      val nino        = generateNino
      val requestBody =
        s"""
           |[
           |    {
           |        "groupId": "98ADEA51-C0BA-497D-997E-F585FAADBCEH",
           |        "affinityGroup": "Individual",
           |        "nino": "$nino",
           |        "user": {
           |            "credId": "5217739547427626",
           |            "name": "Firstname Surname",
           |            "email": "email@example.invalid",
           |            "credentialRole": "Admin",
           |            "description": "Description"
           |        },
           |        "enrolments": [
           |            {
           |                "serviceName": "IR-SA",
           |                "assignedUserCreds": [
           |                    "1"
           |                ],
           |                "identifiers":
           |                    {
           |                        "key": "UTR",
           |                        "value": "123456"
           |                    }
           |                ,
           |                "verifiers": [
           |                    {
           |                        "key": "Postcode",
           |                        "value": "postcode"
           |                    },
           |                    {
           |                        "key": "NINO",
           |                        "value": "$nino"
           |                    }
           |                ],
           |                "enrolmentFriendlyName": "IR-SA Enrolment",
           |                "state": "Activated",
           |                "enrolmentType": "principal",
           |                "assignedToAll": false
           |            }
           |        ]
           |    },
           |    {
           |        "groupId": "98ADEA51-C0BA-497D-997E-F585FAADBCEI",
           |        "affinityGroup": "Individual",
           |        "nino": "$nino",
           |        "user": {
           |            "credId": "5217739547427627",
           |            "name": "Firstname Surname",
           |            "email": "email@example.invalid",
           |            "credentialRole": "Admin",
           |            "description": "Description"
           |        },
           |        "enrolments": []
           |    }
           |]
           |""".stripMargin
      val accounts    = Json.parse(requestBody).as[List[AccountDetailsTestOnly]]

      accounts.foreach { account =>
        when(mockAccountUtilsTestOnly.deleteAccountDetails(ameq(account))(any()))
          .thenReturn(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](()))

        when(mockAccountUtilsTestOnly.insertAccountDetails(ameq(account))(any()))
          .thenReturn(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](()))
      }

      val request = FakeRequest()
        .withMethod("POST")
        .withJsonBody(Json.parse(requestBody))

      val result = sut.create.apply(request)

      status(result) mustBe OK
    }
    "throw an error" in {
      val nino        = generateNino
      val requestBody =
        s"""
           |[
           |    {
           |        "groupId": "98ADEA51-C0BA-497D-997E-F585FAADBCEH",
           |        "affinityGroup": "Individual",
           |        "nino": "$nino",
           |        "user": {
           |            "credId": "5217739547427626",
           |            "name": "Firstname Surname",
           |            "email": "email@example.invalid",
           |            "credentialRole": "Admin",
           |            "description": "Description"
           |        },
           |        "enrolments": [
           |            {
           |                "serviceName": "IR-SA",
           |                "assignedUserCreds": [
           |                    "1"
           |                ],
           |                "identifiers":
           |                    {
           |                        "key": "UTR",
           |                        "value": "123456"
           |                    }
           |                ,
           |                "verifiers": [
           |                    {
           |                        "key": "Postcode",
           |                        "value": "postcode"
           |                    },
           |                    {
           |                        "key": "NINO",
           |                        "value": "$nino"
           |                    }
           |                ],
           |                "enrolmentFriendlyName": "IR-SA Enrolment",
           |                "state": "Activated",
           |                "enrolmentType": "principal",
           |                "assignedToAll": false
           |            }
           |        ]
           |    },
           |    {
           |        "groupId": "98ADEA51-C0BA-497D-997E-F585FAADBCEI",
           |        "affinityGroup": "Individual",
           |        "nino": "$nino",
           |        "user": {
           |            "credId": "5217739547427627",
           |            "name": "Firstname Surname",
           |            "email": "email@example.invalid",
           |            "credentialRole": "Admin",
           |            "description": "Description"
           |        },
           |        "enrolments": []
           |    }
           |]
           |""".stripMargin

      val accounts = Json.parse(requestBody).as[List[AccountDetailsTestOnly]]
      accounts.foreach { account =>
        when(mockAccountUtilsTestOnly.deleteAccountDetails(ameq(account))(any()))
          .thenReturn(EitherT.leftT[Future, Unit].apply(UnexpectedError))
      }

      val request = FakeRequest()
        .withMethod("POST")
        .withJsonBody(Json.parse(requestBody))

      val result = sut.create.apply(request)

      status(result) mustBe INTERNAL_SERVER_ERROR
    }

  }

  "insertCustomTestData" must {
    "create an account with enrolmemts" in {
      val nino                 = generateNino
      val identityProviderType = "SCP"

      val account = AccountDetailsTestOnly(
        identityProviderType,
        "98ADEA51-C0BA-497D-997E-F585FAADBCEH",
        nino,
        "Individual",
        UserTestOnly("5217739547427626", "Firstname Surname", "email@example.com"),
        List(
          EnrolmentDetailsTestOnly(
            "IR-SA",
            IdentifiersOrVerifiers("UTR", "AA543004E"),
            List(IdentifiersOrVerifiers("postcode", "postcode")),
            "IR-SA Enrolment",
            "Activated",
            "principal"
          )
        ),
        List(AdditonalFactors("totp", None, Some("HMRC-APP")))
      )

      val request = FakeRequest()
        .withMethod("POST")
        .withFormUrlEncodedBody(
          "user-data" -> Json.toJson(List(account)).toString()
        )

      when(mockFileHelper.loadFile(s"${nino.nino}.json"))
        .thenReturn(Success(Json.toJson(account)))

      when(mockAccountUtilsTestOnly.deleteAccountDetails(ameq(account))(any()))
        .thenReturn(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](()))

      when(mockAccountUtilsTestOnly.insertAccountDetails(ameq(account))(any()))
        .thenReturn(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](()))

      val result = sut.insertCustomTestData.apply(request)

      status(result) mustBe OK
    }
    "create an account with no enrolments" in {
      val nino                 = generateNino
      val identityProviderType = "SCP"

      val account = AccountDetailsTestOnly(
        identityProviderType,
        "98ADEA51-C0BA-497D-997E-F585FAADBCEH",
        nino,
        "Individual",
        UserTestOnly("5217739547427626", "Firstname Surname", "email@example.com"),
        List(),
        List(AdditonalFactors("totp", None, Some("HMRC-APP")))
      )

      val request = FakeRequest()
        .withMethod("POST")
        .withFormUrlEncodedBody(
          "user-data" -> Json.toJson(List(account)).toString()
        )

      when(mockFileHelper.loadFile(s"${nino.nino}.json"))
        .thenReturn(Success(Json.toJson(account)))

      when(mockAccountUtilsTestOnly.deleteAccountDetails(ameq(account))(any()))
        .thenReturn(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](()))

      when(mockAccountUtilsTestOnly.insertAccountDetails(ameq(account))(any()))
        .thenReturn(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](()))

      val result = sut.insertCustomTestData.apply(request)

      status(result) mustBe OK
    }
    "create bad request when given no form data" in {

      val request = FakeRequest()
        .withMethod("POST")
        .withFormUrlEncodedBody("user-data" -> "")

      val result = sut.insertCustomTestData.apply(request)

      status(result) mustBe BAD_REQUEST
    }
    "give 500 response when API returns an error" in {
      val nino                 = generateNino
      val identityProviderType = "SCP"

      val account = AccountDetailsTestOnly(
        identityProviderType,
        "98ADEA51-C0BA-497D-997E-F585FAADBCEH",
        nino,
        "Individual",
        UserTestOnly("5217739547427626", "Firstname Surname", "email@example.com"),
        List(),
        List(AdditonalFactors("totp", None, Some("HMRC-APP")))
      )

      val request = FakeRequest()
        .withMethod("POST")
        .withFormUrlEncodedBody(
          "user-data" -> Json.toJson(List(account)).toString()
        )

      when(mockFileHelper.loadFile(s"${nino.nino}.json"))
        .thenReturn(Success(Json.toJson(account)))

      when(mockAccountUtilsTestOnly.deleteAccountDetails(ameq(account))(any()))
        .thenReturn(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](()))

      when(mockAccountUtilsTestOnly.insertAccountDetails(ameq(account))(any()))
        .thenReturn(EitherT.leftT[Future, Unit].apply(UnexpectedError))

      val result = sut.insertCustomTestData.apply(request)

      status(result) mustBe INTERNAL_SERVER_ERROR
    }
  }

  "insertTestData" must {
    "create an account with enrolmemts" in {
      val nino                 = generateNino
      val identityProviderType = "SCP"

      val account = AccountDetailsTestOnly(
        identityProviderType,
        "98ADEA51-C0BA-497D-997E-F585FAADBCEH",
        nino,
        "Individual",
        UserTestOnly("5217739547427626", "Firstname Surname", "email@example.com"),
        List(
          EnrolmentDetailsTestOnly(
            "IR-SA",
            IdentifiersOrVerifiers("UTR", "AA543004E"),
            List(IdentifiersOrVerifiers("postcode", "postcode")),
            "IR-SA Enrolment",
            "Activated",
            "principal"
          )
        ),
        List(AdditonalFactors("totp", None, Some("HMRC-APP")))
      )

      val request = FakeRequest()
        .withMethod("POST")
        .withFormUrlEncodedBody(
          "selectUser" -> nino.nino
        )

      when(mockFileHelper.loadFile(s"${nino.nino}.json"))
        .thenReturn(Success(Json.toJson(account)))

      when(mockAccountUtilsTestOnly.deleteAccountDetails(ameq(account))(any()))
        .thenReturn(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](()))

      when(mockAccountUtilsTestOnly.insertAccountDetails(ameq(account))(any()))
        .thenReturn(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](()))

      val result = sut.insertTestData.apply(request)

      status(result) mustBe OK
    }
    "create an account with no enrolments" in {
      val nino                 = generateNino
      val identityProviderType = "SCP"

      val account = AccountDetailsTestOnly(
        identityProviderType,
        "98ADEA51-C0BA-497D-997E-F585FAADBCEH",
        nino,
        "Individual",
        UserTestOnly("5217739547427626", "Firstname Surname", "email@example.com"),
        List(),
        List(AdditonalFactors("totp", None, Some("HMRC-APP")))
      )

      val request = FakeRequest()
        .withMethod("POST")
        .withFormUrlEncodedBody(
          "selectUser" -> nino.nino
        )

      when(mockFileHelper.loadFile(s"${nino.nino}.json"))
        .thenReturn(Success(Json.toJson(account)))

      when(mockAccountUtilsTestOnly.deleteAccountDetails(ameq(account))(any()))
        .thenReturn(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](()))

      when(mockAccountUtilsTestOnly.insertAccountDetails(ameq(account))(any()))
        .thenReturn(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](()))

      val result = sut.insertTestData.apply(request)

      status(result) mustBe OK
    }
    "create bad request when given no form data" in {

      val request = FakeRequest()
        .withMethod("POST")

      val result = sut.insertTestData.apply(request)

      status(result) mustBe BAD_REQUEST
    }
    "give 500 response when API returns an error" in {
      val nino                 = generateNino
      val identityProviderType = "SCP"

      val account = AccountDetailsTestOnly(
        identityProviderType,
        "98ADEA51-C0BA-497D-997E-F585FAADBCEH",
        nino,
        "Individual",
        UserTestOnly("5217739547427626", "Firstname Surname", "email@example.com"),
        List(),
        List(AdditonalFactors("totp", None, Some("HMRC-APP")))
      )

      val request = FakeRequest()
        .withMethod("POST")
        .withFormUrlEncodedBody(
          "selectUser" -> nino.nino
        )

      when(mockFileHelper.loadFile(s"${nino.nino}.json"))
        .thenReturn(Success(Json.toJson(account)))

      when(mockAccountUtilsTestOnly.deleteAccountDetails(ameq(account))(any()))
        .thenReturn(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](()))

      when(mockAccountUtilsTestOnly.insertAccountDetails(ameq(account))(any()))
        .thenReturn(EitherT.leftT[Future, Unit].apply(UnexpectedError))

      val result = sut.insertTestData.apply(request)

      status(result) mustBe INTERNAL_SERVER_ERROR
    }
  }
  "extractData"    must {
    "return List of AccountDetailsTestOnly when given valid json" in {
      val nino                 = generateNino
      val identityProviderType = "SCP"

      val account = AccountDetailsTestOnly(
        identityProviderType,
        "98ADEA51-C0BA-497D-997E-F585FAADBCEH",
        nino,
        "Individual",
        UserTestOnly("5217739547427626", "Firstname Surname", "email@example.com"),
        List(),
        List(AdditonalFactors("totp", None, Some("HMRC-APP")))
      )

      val json = Json.toJson(account)

      when(mockFileHelper.loadFile(s"${nino.nino}.json"))
        .thenReturn(Success(json))

      sut.extractData(nino.nino) mustBe List(account)
    }
  }

  "delete" must {

    "delete all data related to accounts" in {
      val nino        = generateNino
      val requestBody =
        s"""
           |    {
           |        "groupId": "98ADEA51-C0BA-497D-997E-F585FAADBCEH",
           |        "affinityGroup": "Individual",
           |        "nino": "$nino",
           |        "user": {
           |            "credId": "5217739547427626",
           |            "name": "Firstname Surname",
           |            "email": "email@example.invalid",
           |            "credentialRole": "Admin",
           |            "description": "Description"
           |        },
           |        "enrolments": [
           |            {
           |                "serviceName": "IR-SA",
           |                "assignedUserCreds": [
           |                    "1"
           |                ],
           |                "identifiers":
           |                    {
           |                        "key": "UTR",
           |                        "value": "123456"
           |                    }
           |                ,
           |                "verifiers": [
           |                    {
           |                        "key": "Postcode",
           |                        "value": "postcode"
           |                    },
           |                    {
           |                        "key": "NINO",
           |                        "value": "$nino"
           |                    }
           |                ],
           |                "enrolmentFriendlyName": "IR-SA Enrolment",
           |                "state": "Activated",
           |                "enrolmentType": "principal",
           |                "assignedToAll": false
           |            }
           |        ],
           |        "additionalFactors": [
           |            {
           |                "factorType": "factorType",
           |                "phoneNumber": "Phone number",
           |                "name": "name"
           |            }
           |        ]
           |    }
           |""".stripMargin
      val account     = Json.parse(requestBody).as[AccountDetailsTestOnly]

      when(mockAccountUtilsTestOnly.deleteAccountDetails(ameq(account))(any()))
        .thenReturn(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](()))

      val request = FakeRequest()
        .withMethod("POST")
        .withJsonBody(Json.parse(requestBody))

      val result = sut.delete.apply(request)

      status(result) mustBe OK
    }

    "create multiple accounts" in {
      val nino        = generateNino
      val requestBody =
        s"""
           |[
           |    {
           |        "groupId": "98ADEA51-C0BA-497D-997E-F585FAADBCEH",
           |        "affinityGroup": "Individual",
           |        "nino": "$nino",
           |        "user": {
           |            "credId": "5217739547427626",
           |            "name": "Firstname Surname",
           |            "email": "email@example.invalid",
           |            "credentialRole": "Admin",
           |            "description": "Description"
           |        },
           |        "enrolments": [
           |            {
           |                "serviceName": "IR-SA",
           |                "assignedUserCreds": [
           |                    "1"
           |                ],
           |                "identifiers":
           |                    {
           |                        "key": "UTR",
           |                        "value": "123456"
           |                    }
           |                ,
           |                "verifiers": [
           |                    {
           |                        "key": "Postcode",
           |                        "value": "postcode"
           |                    },
           |                    {
           |                        "key": "NINO",
           |                        "value": "$nino"
           |                    }
           |                ],
           |                "enrolmentFriendlyName": "IR-SA Enrolment",
           |                "state": "Activated",
           |                "enrolmentType": "principal",
           |                "assignedToAll": false
           |            }
           |        ]
           |    },
           |    {
           |        "groupId": "98ADEA51-C0BA-497D-997E-F585FAADBCEI",
           |        "affinityGroup": "Individual",
           |        "nino": "$nino",
           |        "user": {
           |            "credId": "5217739547427627",
           |            "name": "Firstname Surname",
           |            "email": "email@example.invalid",
           |            "credentialRole": "Admin",
           |            "description": "Description"
           |        },
           |        "enrolments": []
           |    }
           |]
           |""".stripMargin
      val accounts    = Json.parse(requestBody).as[List[AccountDetailsTestOnly]]

      accounts.foreach { account =>
        when(mockAccountUtilsTestOnly.deleteAccountDetails(ameq(account))(any()))
          .thenReturn(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](()))

        when(mockAccountUtilsTestOnly.insertAccountDetails(ameq(account))(any()))
          .thenReturn(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](()))
      }

      val request = FakeRequest()
        .withMethod("POST")
        .withJsonBody(Json.parse(requestBody))

      val result = sut.create.apply(request)

      status(result) mustBe OK
    }

    "thrown an error" in {
      val nino        = generateNino
      val requestBody =
        s"""
           |[
           |    {
           |        "groupId": "98ADEA51-C0BA-497D-997E-F585FAADBCEH",
           |        "affinityGroup": "Individual",
           |        "nino": "$nino",
           |        "user": {
           |            "credId": "5217739547427626",
           |            "name": "Firstname Surname",
           |            "email": "email@example.invalid",
           |            "credentialRole": "Admin",
           |            "description": "Description"
           |        },
           |        "enrolments": [
           |            {
           |                "serviceName": "IR-SA",
           |                "assignedUserCreds": [
           |                    "1"
           |                ],
           |                "identifiers":
           |                    {
           |                        "key": "UTR",
           |                        "value": "123456"
           |                    }
           |                ,
           |                "verifiers": [
           |                    {
           |                        "key": "Postcode",
           |                        "value": "postcode"
           |                    },
           |                    {
           |                        "key": "NINO",
           |                        "value": "$nino"
           |                    }
           |                ],
           |                "enrolmentFriendlyName": "IR-SA Enrolment",
           |                "state": "Activated",
           |                "enrolmentType": "principal",
           |                "assignedToAll": false
           |            }
           |        ]
           |    },
           |    {
           |        "groupId": "98ADEA51-C0BA-497D-997E-F585FAADBCEI",
           |        "affinityGroup": "Individual",
           |        "nino": "$nino",
           |        "user": {
           |            "credId": "5217739547427627",
           |            "name": "Firstname Surname",
           |            "email": "email@example.invalid",
           |            "credentialRole": "Admin",
           |            "description": "Description"
           |        },
           |        "enrolments": []
           |    }
           |]
           |""".stripMargin

      val accounts = Json.parse(requestBody).as[List[AccountDetailsTestOnly]]
      accounts.foreach { account =>
        when(mockAccountUtilsTestOnly.deleteAccountDetails(ameq(account))(any()))
          .thenReturn(EitherT.leftT[Future, Unit].apply(UnexpectedError))
      }

      val request = FakeRequest()
        .withMethod("POST")
        .withJsonBody(Json.parse(requestBody))

      val result = sut.delete.apply(request)

      status(result) mustBe INTERNAL_SERVER_ERROR
    }

  }

  "getTestData" must {
    "return ok response" in {
      val request = FakeRequest()
        .withMethod("GET")

      val result = sut.getTestDataInfo.apply(request)

      status(result) mustBe OK
    }
  }

  "getCustomTestData" must {
    "return ok response" in {
      val request = FakeRequest()
        .withMethod("GET")

      val nino                 = generateNino
      val identityProviderType = "SCP"

      val account = AccountDetailsTestOnly(
        identityProviderType,
        "98ADEA51-C0BA-497D-997E-F585FAADBCEH",
        nino,
        "Individual",
        UserTestOnly("5217739547427626", "Firstname Surname", "email@example.com"),
        List(),
        List(AdditonalFactors("totp", None, Some("HMRC-APP")))
      )

      val json = Json.toJson(account)

      when(mockFileHelper.loadFile(s"singleUserWithSAEnrolment.json"))
        .thenReturn(Success(json))

      val result = sut.getCustomTestData.apply(request)

      status(result) mustBe OK
    }
  }

  "successfulCall" must {
    val mockEacdService           = mock[EACDService]
    val mockEnrolmentStoreService = mock[EnrolmentStoreServiceTestOnly]
    val mockAuthConnector         = mock[AuthConnector]

    "call EACDService in non staging environments" in {
      implicit lazy val app: Application = localGuiceApplicationBuilder()
        .overrides(
          bind[EACDService].toInstance(mockEacdService),
          bind[EnrolmentStoreServiceTestOnly].toInstance(mockEnrolmentStoreService),
          bind[AuthConnector].toInstance(mockAuthConnector)
        )
        .build()

      when(mockAuthConnector.authorise(ameq(predicates), ameq(retrievals))(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future.successful(retrievalResponse()))

      when(mockEacdService.getUsersAssignedPTEnrolment(any(), any(), any()))
        .thenReturn(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](UsersAssignedEnrolmentCurrentCred))

      lazy val controller: TestOnlyController = app.injector.instanceOf[TestOnlyController]

      val request = buildFakeRequestWithSessionId("GET", "Not Used")

      status(controller.successfulCall.apply(request)) mustBe OK
    }
    "call enrolmentStoreService in staging" in {
      implicit lazy val app: Application = localGuiceApplicationBuilder()
        .overrides(
          bind[EACDService].toInstance(mockEacdService),
          bind[EnrolmentStoreServiceTestOnly].toInstance(mockEnrolmentStoreService),
          bind[AuthConnector].toInstance(mockAuthConnector)
        )
        .configure(
          "testOnly.environment" -> "Staging"
        )
        .build()

      when(mockAuthConnector.authorise(ameq(predicates), ameq(retrievals))(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future.successful(retrievalResponse()))

      when(mockEnrolmentStoreService.getUsersAssignedPTEnrolmentFromStub(any())(any()))
        .thenReturn(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](UsersAssignedEnrolmentCurrentCred))

      lazy val controller: TestOnlyController = app.injector.instanceOf[TestOnlyController]

      val request = buildFakeRequestWithSessionId("GET", "Not Used")

      status(controller.successfulCall.apply(request)) mustBe OK
    }
  }
}
