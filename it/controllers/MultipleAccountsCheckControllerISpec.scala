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

package controllers

import helpers.TestITData._
import helpers.WiremockHelper._
import helpers.{IntegrationSpecBase, TestITData}
import org.jsoup.Jsoup
import play.api.http.Status
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.testOnly

class MultipleAccountsCheckControllerISpec extends IntegrationSpecBase with Status {

  val teaHost = s"localhost:$port"
  val returnUrl: String = testOnly.routes.TestOnlyController.successfulCall
    .absoluteURL(false, teaHost)
  val urlPathEC =
    s"/multiple-accounts-check?redirectUrl=${testOnly.routes.TestOnlyController.successfulCall
      .absoluteURL(false, teaHost)}"


  s"GET $urlPathEC" when {
    "a user's credentials are in the list of principalIds returned from EACD" should {
      s"redirect to returnUrl" in {
        val authResponse = authoriseResponseJson(enrolments = ptEnrolmentOnly)
        stubAuthorizePost(OK, authResponse.toString())
        stubPost(s"/write/.*", OK, """{"x":2}""")
        stubGet(
          s"/enrolment-store-proxy/enrolment-store/enrolments/HMRC-PT~NINO~$NINO/users",
          Status.OK,
          es0ResponseMatchingCred
        )
        val res = buildRequest(urlPathEC, followRedirects = true)
          .withHttpHeaders(xSessionId, csrfContent)
          .get()

        whenReady(res) { resp =>
          resp.status shouldBe OK
          resp.uri.toString shouldBe returnUrl
        }
      }
    }

    "a user's credentials are not in the list of principalIds returned from EACD" should {
        s"return $OK with the underConstruction page" in {

          val authResponse = authoriseResponseJson()
        stubAuthorizePost(OK, authResponse.toString())
        stubPost(s"/write/.*", OK, """{"x":2}""")
        stubGet(
          s"/enrolment-store-proxy/enrolment-store/enrolments/HMRC-PT~NINO~$NINO/users",
          Status.OK,
          es0ResponseNotMatchingCred
        )
        val res = buildRequest(urlPathEC, followRedirects = true)
          .withHttpHeaders(xSessionId, csrfContent)
          .get()

        whenReady(res) { result =>
          val page = Jsoup.parse(result.body)

          result.status shouldBe OK
          page.title    should include(TestITData.underConstructionTruePageTitle)
        }
      }
    }

    "no users have an enrolment for the particular enrolment key " should {
        s"return $OK with the underConstruction page" in {

          val authResponse = authoriseResponseJson()
        stubAuthorizePost(OK, authResponse.toString())
        stubPost(s"/write/.*", OK, """{"x":2}""")
        stubGet(
          s"/enrolment-store-proxy/enrolment-store/enrolments/HMRC-PT~NINO~$NINO/users",
          Status.NO_CONTENT,
          es0ResponseNoRecordCred
        )
        val res = buildRequest(urlPathEC, followRedirects = true)
          .withHttpHeaders(xSessionId, csrfContent)
          .get()

        whenReady(res) { result =>
          val page = Jsoup.parse(result.body)

          result.status shouldBe OK
          page.title    should include(TestITData.underConstructionFalsePageTitle)
        }
      }
    }

    "an authorised user with no credential uses the service" should {
      s"return $INTERNAL_SERVER_ERROR" in {
        val authResponse = authoriseResponseJson()
        stubAuthorizePost(OK, authResponse.toString())
        stubPost(s"/write/.*", OK, """{"x":2}""")
        stubGet(
          s"/enrolment-store-proxy/enrolment-store/enrolments/HMRC-PT~NINO~$NINO/users",
          Status.NOT_FOUND,
          ""
        )
        val res = buildRequest(urlPathEC, followRedirects = true)
          .withHttpHeaders(xSessionId, csrfContent)
          .get()

        whenReady(res) { resp =>
          resp.status shouldBe INTERNAL_SERVER_ERROR
        }
      }
    }

    "an authorised user but EACD returns internal error" should {
      s"return $INTERNAL_SERVER_ERROR" in {
        val authResponse = authoriseResponseJson()
        stubAuthorizePost(OK, authResponse.toString())
        stubPost(s"/write/.*", OK, """{"x":2}""")
        stubGet(
          s"/enrolment-store-proxy/enrolment-store/enrolments/HMRC-PT~NINO~$NINO/users",
          Status.INTERNAL_SERVER_ERROR,
          ""
        )
        val res = buildRequest(urlPathEC, followRedirects = true)
          .withHttpHeaders(xSessionId, csrfContent)
          .get()

        whenReady(res) { resp =>
          resp.status shouldBe INTERNAL_SERVER_ERROR
        }
      }
    }
  }
}
