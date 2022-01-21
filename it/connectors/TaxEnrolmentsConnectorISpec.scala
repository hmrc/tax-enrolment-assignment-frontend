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
import uk.gov.hmrc.taxenrolmentassignmentfrontend.connectors.{
  EACDConnector,
  TaxEnrolmentsConnector
}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.{
  UnexpectedResponseFromEACD,
  UnexpectedResponseFromTaxEnrolments
}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.UsersAssignedEnrolment

class TaxEnrolmentsConnectorISpec extends IntegrationSpecBase {

  lazy val connector: TaxEnrolmentsConnector =
    app.injector.instanceOf[TaxEnrolmentsConnector]

  "assignPTEnrolment" when {
    val ENROLMENT_KEY = s"HMRC-PT~NINO~$NINO"
    val PATH =
      s"/tax-enrolments/groups/$GROUP_ID/enrolments/$ENROLMENT_KEY"
    s"the user is assigned the enrolment" should {
      "return Unit" in {
        stubPostWithAuthorizeHeaders(
          PATH,
          AUTHORIZE_HEADER_VALUE,
          Status.CREATED
        )
        whenReady(
          connector.assignPTEnrolment(GROUP_ID, CREDENTIAL_ID, NINO).value
        ) { response =>
          response shouldBe Right((): Unit)
        }
      }
    }

    s"the user is not authorized" should {
      "return an UnexpectedResponseFromTaxEnrolments" in {
        stubPostWithAuthorizeHeaders(
          PATH,
          AUTHORIZE_HEADER_VALUE,
          Status.UNAUTHORIZED
        )
        whenReady(
          connector.assignPTEnrolment(GROUP_ID, CREDENTIAL_ID, NINO).value
        ) { response =>
          response shouldBe Left(UnexpectedResponseFromTaxEnrolments)
        }
      }
    }

    "a BAD_REQUEST is returned" should {
      "return an UnexpectedResponseFromTaxEnrolments error" in {
        stubPostWithAuthorizeHeaders(
          PATH,
          AUTHORIZE_HEADER_VALUE,
          Status.BAD_REQUEST
        )
        whenReady(
          connector.assignPTEnrolment(GROUP_ID, CREDENTIAL_ID, NINO).value
        ) { response =>
          response shouldBe Left(UnexpectedResponseFromTaxEnrolments)
        }
      }
    }

    "a NOT_FOUND is returned" should {
      "return an UnexpectedResponseFromTaxEnrolments error" in {
        stubPostWithAuthorizeHeaders(
          PATH,
          AUTHORIZE_HEADER_VALUE,
          Status.NOT_FOUND
        )
        whenReady(
          connector.assignPTEnrolment(GROUP_ID, CREDENTIAL_ID, NINO).value
        ) { response =>
          response shouldBe Left(UnexpectedResponseFromTaxEnrolments)
        }
      }
    }

    "a INTERNAL_SERVER_ERROR is returned" should {
      "return an UnexpectedResponseFromTaxEnrolments error" in {
        stubPostWithAuthorizeHeaders(
          PATH,
          AUTHORIZE_HEADER_VALUE,
          Status.INTERNAL_SERVER_ERROR
        )
        whenReady(
          connector.assignPTEnrolment(GROUP_ID, CREDENTIAL_ID, NINO).value
        ) { response =>
          response shouldBe Left(UnexpectedResponseFromTaxEnrolments)
        }
      }
    }
  }
}
