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
import uk.gov.hmrc.taxenrolmentassignmentfrontend.connectors.TaxEnrolmentsConnector
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.UnexpectedResponseFromTaxEnrolments

class TaxEnrolmentsConnectorISpec extends IntegrationSpecBase {

  lazy val connector: TaxEnrolmentsConnector =
    app.injector.instanceOf[TaxEnrolmentsConnector]

  "assignPTEnrolmentWithKnownFacts" when {

    val PATH = s"/tax-enrolments/service/HMRC-PT/enrolment"

    s"the user is assigned the enrolment" should {
      "return Unit" in {
        stubPutWithAuthorizeHeaders(
          PATH,
          AUTHORIZE_HEADER_VALUE,
          Status.NO_CONTENT
        )
        stubPost(s"/write/.*", OK, """{"x":2}""")
        whenReady(connector.assignPTEnrolmentWithKnownFacts(NINO).value) {
          response =>
            response shouldBe Right((): Unit)
        }
      }
    }

    s"the user has already been assigned the enrolment" should {
      "return Unit" in {
        stubPutWithAuthorizeHeaders(
          PATH,
          AUTHORIZE_HEADER_VALUE,
          Status.CONFLICT
        )
        stubPost(s"/write/.*", OK, """{"x":2}""")
        whenReady(connector.assignPTEnrolmentWithKnownFacts(NINO).value) {
          response =>
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
        stubPost(s"/write/.*", OK, """{"x":2}""")
        whenReady(connector.assignPTEnrolmentWithKnownFacts(NINO).value) {
          response =>
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
        stubPost(s"/write/.*", OK, """{"x":2}""")
        whenReady(connector.assignPTEnrolmentWithKnownFacts(NINO).value) {
          response =>
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
        stubPost(s"/write/.*", OK, """{"x":2}""")
        whenReady(connector.assignPTEnrolmentWithKnownFacts(NINO).value) {
          response =>
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
        stubPost(s"/write/.*", OK, """{"x":2}""")
        whenReady(connector.assignPTEnrolmentWithKnownFacts(NINO).value) {
          response =>
            response shouldBe Left(UnexpectedResponseFromTaxEnrolments)
        }
      }
    }
  }

}
