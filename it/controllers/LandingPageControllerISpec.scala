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

import helpers.IntegrationSpecBase
import helpers.WiremockHelper._
import helpers.TestITData._
import play.api.http.Status
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.testOnly

class LandingPageControllerISpec extends IntegrationSpecBase with Status {

  val teaHost = s"localhost:$port"
  val returnUrl: String = testOnly.routes.TestOnlyController.successfulCall
    .absoluteURL(false, teaHost)
  val urlPath =
    s"/enrol-pt/introduction?redirectUrl=${testOnly.routes.TestOnlyController.successfulCall
      .absoluteURL(false, teaHost)}"

  s"GET $urlPath" when {
    "an authorised user with PT enrolment in session uses the service" should {
      s"redirect to returnUrl" in {
        val authResponse = authoriseResponseJson(enrolments = ptEnrolmentOnly)
        stubAuthorizePost(OK, authResponse.toString())
        stubPost(s"/write/.*", OK, """{"x":2}""")
        val res = buildRequest(urlPath, followRedirects = true)
          .withHttpHeaders(xSessionId, csrfContent)
          .get()

        whenReady(res) { resp =>
          resp.status shouldBe OK
          resp.uri.toString shouldBe returnUrl
        }
      }
    }

    "an authorised user with one credential uses the service" should {
      s"redirect to PTA with the HMRC-PT Enrolment" in {
        val authResponse = authoriseResponseJson()
        stubAuthorizePost(OK, authResponse.toString())
        stubPost(s"/write/.*", OK, """{"x":2}""")
        stubGetWithQueryParam(
          "/identity-verification/nino",
          "nino",
          NINO,
          Status.OK,
          ivResponseSingleCredsJsonString
        )
        stubPost(
          s"/tax-enrolments/groups/$GROUP_ID/enrolments/HMRC-PT~NINO~$NINO",
          CREATED,
          ""
        )
        val res = buildRequest(urlPath, followRedirects = true)
          .withHttpHeaders(xSessionId, csrfContent)
          .get()

        whenReady(res) { resp =>
          resp.status shouldBe OK
        }
      }
    }

    "an authorised user with one credential uses the service" should {
      s"see the error page if they were unable to be enrolled" in {
        val authResponse = authoriseResponseJson()
        stubAuthorizePost(OK, authResponse.toString())
        stubPost(s"/write/.*", OK, """{"x":2}""")
        stubGetWithQueryParam(
          "/identity-verification/nino",
          "nino",
          NINO,
          Status.OK,
          ivResponseSingleCredsJsonString
        )
        stubPost(
          s"/tax-enrolments/groups/$GROUP_ID/enrolments/HMRC-PT~NINO~$NINO",
          INTERNAL_SERVER_ERROR,
          ""
        )

        val res = buildRequest(urlPath, followRedirects = true)
          .withHttpHeaders(xSessionId, csrfContent)
          .get()

        whenReady(res) { resp =>
          resp.status shouldBe OK
          resp.body should include("There was a problem (real content later TODO)")
        }
      }
    }

    "an authorised user with multiple credential uses the service" should {
      s"return $OK with multiple credential message" in {
        val authResponse = authoriseResponseJson()
        stubAuthorizePost(OK, authResponse.toString())
        stubPost(s"/write/.*", OK, """{"x":2}""")
        stubGetWithQueryParam(
          "/identity-verification/nino",
          "nino",
          NINO,
          Status.OK,
          ivResponseMultiCredsJsonString
        )
        val res = buildRequest(urlPath, followRedirects = true)
          .withHttpHeaders(xSessionId, csrfContent)
          .get()

        whenReady(res) { resp =>
          resp.status shouldBe OK
          resp.body should include("We are changing the way you access your personal tax information")
        }
      }
    }

    "an authorised user with no credential uses the service" should {
      s"return $INTERNAL_SERVER_ERROR" in {
        val authResponse = authoriseResponseJson()
        stubAuthorizePost(OK, authResponse.toString())
        stubPost(s"/write/.*", OK, """{"x":2}""")
        stubGetWithQueryParam(
          "/identity-verification/nino",
          "nino",
          NINO,
          Status.NOT_FOUND,
          ""
        )
        val res = buildRequest(urlPath, followRedirects = true)
          .withHttpHeaders(xSessionId, csrfContent)
          .get()

        whenReady(res) { resp =>
          resp.status shouldBe INTERNAL_SERVER_ERROR
        }
      }
    }

    "an authorised user but IV returns internal error" should {
      s"return $INTERNAL_SERVER_ERROR" in {
        val authResponse = authoriseResponseJson()
        stubAuthorizePost(OK, authResponse.toString())
        stubPost(s"/write/.*", OK, """{"x":2}""")
        stubGetWithQueryParam(
          "/identity-verification/nino",
          "nino",
          NINO,
          Status.INTERNAL_SERVER_ERROR,
          ""
        )
        val res = buildRequest(urlPath, followRedirects = true)
          .withHttpHeaders(xSessionId, csrfContent)
          .get()

        whenReady(res) { resp =>
          resp.status shouldBe INTERNAL_SERVER_ERROR
        }
      }
    }

    "the user has a session missing required element NINO" should {
      s"return $UNAUTHORIZED" in {
        val authResponse = authoriseResponseJson(optNino = None)
        stubAuthorizePost(OK, authResponse.toString())
        stubPost(s"/write/.*", OK, """{"x":2}""")

        val res =
          buildRequest(urlPath).withHttpHeaders(xSessionId, csrfContent).get()

        whenReady(res) { resp =>
          resp.status shouldBe UNAUTHORIZED
        }
      }
    }

    "the user has a session missing required element Credentials" should {
      s"return $UNAUTHORIZED" in {
        val authResponse = authoriseResponseJson(optCreds = None)
        stubAuthorizePost(OK, authResponse.toString())
        stubPost(s"/write/.*", OK, """{"x":2}""")

        val res =
          buildRequest(urlPath).withHttpHeaders(xSessionId, csrfContent).get()

        whenReady(res) { resp =>
          resp.status shouldBe UNAUTHORIZED
        }
      }
    }

    List(sessionNotFound, insufficientConfidenceLevel).foreach {
      failureReason =>
        s"the auth returns 401 and the user has an $failureReason" should {
          s"return $UNAUTHORIZED" in {
            stubAuthorizePostUnauthorised(failureReason)
            stubPost(s"/write/.*", OK, """{"x":2}""")

            val res = buildRequest(urlPath)
              .withHttpHeaders(xSessionId, csrfContent)
              .get()

            whenReady(res) { resp =>
              resp.status shouldBe UNAUTHORIZED
            }
          }
        }
    }
  }
}
