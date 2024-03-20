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
import org.scalatest.matchers.must.Matchers._
import play.api.Application
import play.api.http.Status.{BAD_REQUEST, INTERNAL_SERVER_ERROR, OK}
import play.api.inject.bind
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers.{defaultAwaitTimeout, status}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.{TaxEnrolmentAssignmentErrors, UnexpectedError}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.BaseSpec
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.{AdditonalFactors, IdentifiersOrVerifiers}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.testOnly.models.{AccountDetailsTestOnly, EnrolmentDetailsTestOnly, UserTestOnly}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.testOnly.utils.AccountUtilsTestOnly

import scala.concurrent.Future

class TestOnlyControllerSpec extends BaseSpec {

  lazy val mockAccountUtilsTestOnly: AccountUtilsTestOnly = mock[AccountUtilsTestOnly]
  lazy val mockFileHelper: FileHelper = mock[FileHelper]

  override implicit lazy val app: Application = localGuiceApplicationBuilder()
    .overrides(
      bind[AccountUtilsTestOnly].toInstance(mockAccountUtilsTestOnly),
      bind[FileHelper].toInstance(mockFileHelper)
    )
    .build()

  lazy val sut: TestOnlyController = app.injector.instanceOf[TestOnlyController]

  "create" must {

    "create a single account" in {
      val nino = generateNino
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
      val account = Json.parse(requestBody).as[AccountDetailsTestOnly]

      (mockAccountUtilsTestOnly
        .deleteAccountDetails(_: AccountDetailsTestOnly)(_: HeaderCarrier))
        .expects(account, *)
        .returning(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](()))

      (mockAccountUtilsTestOnly
        .insertAccountDetails(_: AccountDetailsTestOnly)(_: HeaderCarrier))
        .expects(account, *)
        .returning(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](()))

      val request = FakeRequest()
        .withMethod("POST")
        .withJsonBody(Json.parse(requestBody))

      val result = sut.create.apply(request)

      status(result) mustBe OK
    }

    "create multiple accounts" in {
      val nino = generateNino
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
        (mockAccountUtilsTestOnly
          .deleteAccountDetails(_: AccountDetailsTestOnly)(_: HeaderCarrier))
          .expects(account, *)
          .returning(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](()))

        (mockAccountUtilsTestOnly
          .insertAccountDetails(_: AccountDetailsTestOnly)(_: HeaderCarrier))
          .expects(account, *)
          .returning(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](()))
      }

      val request = FakeRequest()
        .withMethod("POST")
        .withJsonBody(Json.parse(requestBody))

      val result = sut.create.apply(request)

      status(result) mustBe OK
    }

  }

  "insertTestData" must {
    "create an account with enrolmemts" in {
      val nino = generateNino

      val account = AccountDetailsTestOnly(
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

      (mockFileHelper
        .loadFile(_: String))
        .expects(s"${nino.nino}.json")
        .returning(Json.toJson(account).toString())

      (mockAccountUtilsTestOnly
        .deleteAccountDetails(_: AccountDetailsTestOnly)(_: HeaderCarrier))
        .expects(account, *)
        .returning(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](()))

      (mockAccountUtilsTestOnly
        .insertAccountDetails(_: AccountDetailsTestOnly)(_: HeaderCarrier))
        .expects(account, *)
        .returning(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](()))

      val result = sut.insertTestData.apply(request)

      status(result) mustBe OK
    }
    "create an account with no enrolments" in {
      val nino = generateNino

      val account = AccountDetailsTestOnly(
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

      (mockFileHelper
        .loadFile(_: String))
        .expects(s"${nino.nino}.json")
        .returning(Json.toJson(account).toString())

      (mockAccountUtilsTestOnly
        .deleteAccountDetails(_: AccountDetailsTestOnly)(_: HeaderCarrier))
        .expects(account, *)
        .returning(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](()))

      (mockAccountUtilsTestOnly
        .insertAccountDetails(_: AccountDetailsTestOnly)(_: HeaderCarrier))
        .expects(account, *)
        .returning(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](()))

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
      val nino = generateNino

      val account = AccountDetailsTestOnly(
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

      (mockFileHelper
        .loadFile(_: String))
        .expects(s"${nino.nino}.json")
        .returning(Json.toJson(account).toString())

      (mockAccountUtilsTestOnly
        .deleteAccountDetails(_: AccountDetailsTestOnly)(_: HeaderCarrier))
        .expects(account, *)
        .returning(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](()))

      (mockAccountUtilsTestOnly
        .insertAccountDetails(_: AccountDetailsTestOnly)(_: HeaderCarrier))
        .expects(account, *)
        .returning(EitherT.leftT[Future, Unit].apply(UnexpectedError))

      val result = sut.insertTestData.apply(request)

      status(result) mustBe INTERNAL_SERVER_ERROR
    }
  }
  "extractData" must {
    "return List of AccountDetailsTestOnly when given valid json" in {
      val nino = generateNino

      val account = AccountDetailsTestOnly(
        "98ADEA51-C0BA-497D-997E-F585FAADBCEH",
        nino,
        "Individual",
        UserTestOnly("5217739547427626", "Firstname Surname", "email@example.com"),
        List(),
        List(AdditonalFactors("totp", None, Some("HMRC-APP")))
      )

      val json = Json.toJson(account)

      (mockFileHelper
        .loadFile(_: String))
        .expects(s"${nino.nino}.json")
        .returning(json.toString())

      sut.extractData(nino.nino) mustBe List(account)
    }
  }

  "delete" must {

    "delete all data related to accounts" in {
      val nino = generateNino
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
      val account = Json.parse(requestBody).as[AccountDetailsTestOnly]

      (mockAccountUtilsTestOnly
        .deleteAccountDetails(_: AccountDetailsTestOnly)(_: HeaderCarrier))
        .expects(account, *)
        .returning(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](()))

      val request = FakeRequest()
        .withMethod("POST")
        .withJsonBody(Json.parse(requestBody))

      val result = sut.delete.apply(request)

      status(result) mustBe OK
    }

    "create multiple accounts" in {
      val nino = generateNino
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
        (mockAccountUtilsTestOnly
          .deleteAccountDetails(_: AccountDetailsTestOnly)(_: HeaderCarrier))
          .expects(account, *)
          .returning(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](()))

        (mockAccountUtilsTestOnly
          .insertAccountDetails(_: AccountDetailsTestOnly)(_: HeaderCarrier))
          .expects(account, *)
          .returning(EitherT.rightT[Future, TaxEnrolmentAssignmentErrors](()))
      }

      val request = FakeRequest()
        .withMethod("POST")
        .withJsonBody(Json.parse(requestBody))

      val result = sut.create.apply(request)

      status(result) mustBe OK
    }

  }
}
