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

    s"the user is assigned the enrolment"               should {
      "return Unit" in {
        stubPutWithAuthorizeHeaders(
          PATH,
          AUTHORIZE_HEADER_VALUE,
          Status.NO_CONTENT
        )
        stubPost(s"/write/.*", OK, """{"x":2}""")
        whenReady(connector.assignPTEnrolmentWithKnownFacts(NINO).value) { response =>
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
        whenReady(connector.assignPTEnrolmentWithKnownFacts(NINO).value) { response =>
          response shouldBe Right((): Unit)
        }
      }
    }

    s"the user is not authorized"                       should {
      "return an UnexpectedResponseFromTaxEnrolments" in {
        stubPutWithAuthorizeHeaders(
          PATH,
          AUTHORIZE_HEADER_VALUE,
          Status.UNAUTHORIZED
        )
        stubPost(s"/write/.*", OK, """{"x":2}""")
        whenReady(connector.assignPTEnrolmentWithKnownFacts(NINO).value) { response =>
          response shouldBe Left(UnexpectedResponseFromTaxEnrolments)
        }
      }
    }

    "a BAD_REQUEST is returned" should {
      "return an UnexpectedResponseFromTaxEnrolments error" in {
        stubPutWithAuthorizeHeaders(
          PATH,
          AUTHORIZE_HEADER_VALUE,
          Status.BAD_REQUEST
        )
        stubPost(s"/write/.*", OK, """{"x":2}""")
        whenReady(connector.assignPTEnrolmentWithKnownFacts(NINO).value) { response =>
          response shouldBe Left(UnexpectedResponseFromTaxEnrolments)
        }
      }
    }

    "a NOT_FOUND is returned" should {
      "return an UnexpectedResponseFromTaxEnrolments error" in {
        stubPutWithAuthorizeHeaders(
          PATH,
          AUTHORIZE_HEADER_VALUE,
          Status.NOT_FOUND
        )
        stubPost(s"/write/.*", OK, """{"x":2}""")
        whenReady(connector.assignPTEnrolmentWithKnownFacts(NINO).value) { response =>
          response shouldBe Left(UnexpectedResponseFromTaxEnrolments)
        }
      }
    }

    "a INTERNAL_SERVER_ERROR is returned" should {
      "return an UnexpectedResponseFromTaxEnrolments error" in {
        stubPutWithAuthorizeHeaders(
          PATH,
          AUTHORIZE_HEADER_VALUE,
          Status.INTERNAL_SERVER_ERROR
        )
        stubPost(s"/write/.*", OK, """{"x":2}""")
        whenReady(connector.assignPTEnrolmentWithKnownFacts(NINO).value) { response =>
          response shouldBe Left(UnexpectedResponseFromTaxEnrolments)
        }
      }
    }

    "an unexpected response is returned" should {
      "return an UnexpectedResponseFromTaxEnrolments error" in {
        stubPutWithAuthorizeHeaders(
          PATH,
          AUTHORIZE_HEADER_VALUE,
          Status.IM_A_TEAPOT
        )
        stubPost(s"/write/.*", OK, """{"x":2}""")
        whenReady(connector.assignPTEnrolmentWithKnownFacts(NINO).value) { response =>
          response shouldBe Left(UnexpectedResponseFromTaxEnrolments)
        }
      }
    }
  }

  "deallocateEnrolment" should {
    val groupId      = "fakeId"
    val enrolmentKey = s"HMRC-PT~NINO~$NINO"
    val url          =
      s"/tax-enrolments/groups/$groupId/enrolments/$enrolmentKey"

    "return Right if the delete request is successful" in {
      stubPost("/write/audit/merged", Status.NO_CONTENT, "")
      stubDelete(url, Status.CREATED)
      whenReady(connector.deallocateEnrolment(groupId, enrolmentKey).value) { response =>
        response shouldBe a[Right[_, _]]
      }
    }
    List(
      Status.BAD_REQUEST,
      Status.NOT_FOUND,
      Status.INTERNAL_SERVER_ERROR,
      Status.BAD_GATEWAY,
      Status.SERVICE_UNAVAILABLE
    ).foreach { errorStatus =>
      s"return Left if the delete request fails with $errorStatus" in {
        stubPost("/write/audit/merged", Status.NO_CONTENT, "")
        stubDelete(url, errorStatus)
        whenReady(connector.deallocateEnrolment(groupId, enrolmentKey).value) { response =>
          response shouldBe a[Left[_, _]]
        }
      }
    }
  }

}
