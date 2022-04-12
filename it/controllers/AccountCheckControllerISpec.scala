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

import helpers.{IntegrationSpecBase, TestITData}
import helpers.WiremockHelper._
import helpers.TestITData._
import org.jsoup.Jsoup
import play.api.http.Status
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.testOnly
import play.api.test.Helpers.await
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes.PT_ASSIGNED_TO_CURRENT_USER

import scala.concurrent.Await

class AccountCheckControllerISpec extends IntegrationSpecBase with Status {

  val teaHost = s"localhost:$port"
  val returnUrl: String = testOnly.routes.TestOnlyController.successfulCall
    .absoluteURL(false, teaHost)
  val urlPath =
    s"?redirectUrl=${testOnly.routes.TestOnlyController.successfulCall
      .absoluteURL(false, teaHost)}"

  s"GET $urlPath" when {
    "a user has one credential associated with their nino" that {
      "has a PT enrolment in the session" should {
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

      "has PT enrolment in EACD but not the session" should {
        s"redirect to returnUrl" in {
          val authResponse = authoriseResponseJson()
          stubAuthorizePost(OK, authResponse.toString())
          stubPost(s"/write/.*", OK, """{"x":2}""")
          stubGet(
            s"/enrolment-store-proxy/enrolment-store/enrolments/HMRC-PT~NINO~$NINO/users",
            Status.OK,
            es0ResponseMatchingCred
          )
          val res = buildRequest(urlPath, followRedirects = true)
            .withHttpHeaders(xSessionId, csrfContent)
            .get()

          whenReady(res) { resp =>
            resp.status shouldBe OK
            resp.uri.toString shouldBe returnUrl
          }
        }
      }

      "has no PT enrolment in session or EACD" should {
        s"silently enrol for PT and redirect to returnUrl" in {
          val authResponse = authoriseResponseJson()
          stubAuthorizePost(OK, authResponse.toString())
          stubPost(s"/write/.*", OK, """{"x":2}""")
          stubGet(
            s"/enrolment-store-proxy/enrolment-store/enrolments/HMRC-PT~NINO~$NINO/users",
            Status.OK,
            es0ResponseNoRecordCred
          )
          stubGetWithQueryParam(
            "/identity-verification/nino",
            "nino",
            NINO,
            Status.OK,
            ivResponseSingleCredsJsonString
          )

          stubPut(
            s"/tax-enrolments/service/HMRC-PT/enrolment",
            Status.NO_CONTENT,
            ""
          )

          stubGet(s"/personal-account", OK, "Government Gateway")

          val res = buildRequest(urlPath, followRedirects = true)
            .withHttpHeaders(xSessionId, csrfContent)
            .get()

          whenReady(res) { resp =>
            resp.status shouldBe OK
            resp.body should include("Government Gateway")
          }
        }
      }
    }

    "a user has other credentials associated with their NINO" that {
      "includes one with a PT enrolment" should {
        "redirect to /no-pt-enrolment" in {
          stubAuthorizePost(OK, authoriseResponseJson().toString())
          stubPost(s"/write/.*", OK, """{"x":2}""")
          stubGet(
            s"/enrolment-store-proxy/enrolment-store/enrolments/HMRC-PT~NINO~$NINO/users",
            Status.OK,
            es0ResponseNotMatchingCred
          )

          val res = buildRequest(urlPath, followRedirects = false)
            .withHttpHeaders(xSessionId, csrfContent)
            .get()

          whenReady(res) { resp =>
            val page = Jsoup.parse(resp.body)

            resp.status shouldBe SEE_OTHER
            resp.header("Location").get should include("/no-pt-enrolment")
          }
        }
      }

      "has SA enrolment on an other account" should {
        s"return OK" in {
          val authResponse = authoriseResponseJson()
          stubAuthorizePost(OK, authResponse.toString())
          stubPost(s"/write/.*", OK, """{"x":2}""")
          stubGet(
            s"/enrolment-store-proxy/enrolment-store/enrolments/HMRC-PT~NINO~$NINO/users",
            Status.OK,
            es0ResponseNoRecordCred
          )
          stubGetWithQueryParam(
            "/identity-verification/nino",
            "nino",
            NINO,
            Status.OK,
            ivResponseMultiCredsJsonString
          )
          stubGetMatching(
            s"/enrolment-store-proxy/enrolment-store/users/$CREDENTIAL_ID_3/enrolments?type=principal",
            Status.NO_CONTENT,
            ""
          )
          stubGetMatching(
            s"/enrolment-store-proxy/enrolment-store/users/$CREDENTIAL_ID_4/enrolments?type=principal",
            Status.NO_CONTENT,
            ""
          )

          stubPost(
            s"/enrolment-store-proxy/enrolment-store/enrolments",
            Status.OK,
            eacdResponse
          )
          stubGet(
            s"/enrolment-store-proxy/enrolment-store/enrolments/IR-SA~UTR~$UTR/users",
            Status.OK,
            es0ResponseNotMatchingCred
          )

          val res = buildRequest(urlPath, followRedirects = false)
            .withHttpHeaders(xSessionId, csrfContent)
            .get()

          whenReady(res) { resp =>
            val page = Jsoup.parse(resp.body)

            resp.status shouldBe OK
          }
        }
      }

      "have no enrolments but current credential has SA enrolment in session" should {
        s"redirect to enrol-pt/enrolment-success-no-sa" in {
          val authResponse = authoriseResponseJson(enrolments = saEnrolmentOnly)
          stubAuthorizePost(OK, authResponse.toString())
          stubPost(s"/write/.*", OK, """{"x":2}""")
          stubGet(
            s"/enrolment-store-proxy/enrolment-store/enrolments/HMRC-PT~NINO~$NINO/users",
            Status.OK,
            es0ResponseNoRecordCred
          )
          stubGetWithQueryParam(
            "/identity-verification/nino",
            "nino",
            NINO,
            Status.OK,
            ivResponseMultiCredsJsonString
          )
          stubGetMatching(
            s"/enrolment-store-proxy/enrolment-store/users/$CREDENTIAL_ID_3/enrolments?type=principal",
            Status.NO_CONTENT,
            ""
          )
          stubGetMatching(
            s"/enrolment-store-proxy/enrolment-store/users/$CREDENTIAL_ID_4/enrolments?type=principal",
            Status.NO_CONTENT,
            ""
          )

          stubPut(
            s"/tax-enrolments/service/HMRC-PT/enrolment",
            Status.NO_CONTENT,
            ""
          )

          val res = buildRequest(urlPath, followRedirects = false)
            .withHttpHeaders(xSessionId, csrfContent)
            .get()

          whenReady(res) { resp =>
            val page = Jsoup.parse(resp.body)

            resp.status shouldBe SEE_OTHER
            resp.header("Location").get should include(
              "/enrol-pt/enrolment-success-no-sa"
            )
          }
        }
      }

      "have no enrolments but current credential has SA enrolment in EACD" should {
        s"redirect to enrol-pt/enrolment-success-no-sa" in {
          val authResponse = authoriseResponseJson(enrolments = saEnrolmentOnly)
          stubAuthorizePost(OK, authResponse.toString())
          stubPost(s"/write/.*", OK, """{"x":2}""")
          stubGet(
            s"/enrolment-store-proxy/enrolment-store/enrolments/HMRC-PT~NINO~$NINO/users",
            Status.OK,
            es0ResponseNoRecordCred
          )
          stubGetWithQueryParam(
            "/identity-verification/nino",
            "nino",
            NINO,
            Status.OK,
            ivResponseMultiCredsJsonString
          )
          stubGetMatching(
            s"/enrolment-store-proxy/enrolment-store/users/$CREDENTIAL_ID_3/enrolments?type=principal",
            Status.NO_CONTENT,
            ""
          )
          stubGetMatching(
            s"/enrolment-store-proxy/enrolment-store/users/$CREDENTIAL_ID_4/enrolments?type=principal",
            Status.NO_CONTENT,
            ""
          )

          stubPost(
            s"/enrolment-store-proxy/enrolment-store/enrolments",
            Status.OK,
            eacdResponse
          )
          stubGet(
            s"/enrolment-store-proxy/enrolment-store/enrolments/IR-SA~UTR~$UTR/users",
            Status.OK,
            es0ResponseMatchingCred
          )

          stubPut(
            s"/tax-enrolments/service/HMRC-PT/enrolment",
            Status.NO_CONTENT,
            ""
          )

          val res = buildRequest(urlPath, followRedirects = false)
            .withHttpHeaders(xSessionId, csrfContent)
            .get()

          whenReady(res) { resp =>
            val page = Jsoup.parse(resp.body)

            resp.status shouldBe SEE_OTHER
            resp.header("Location").get should include(
              "/enrol-pt/enrolment-success-no-sa"
            )
          }
        }
      }

      "have no enrolments" should {
        s"redirect to enrol-pt/enrolment-success-no-sa" in {
          val authResponse = authoriseResponseJson()
          stubAuthorizePost(OK, authResponse.toString())
          stubPost(s"/write/.*", OK, """{"x":2}""")
          stubGet(
            s"/enrolment-store-proxy/enrolment-store/enrolments/HMRC-PT~NINO~$NINO/users",
            Status.OK,
            es0ResponseNoRecordCred
          )
          stubGetWithQueryParam(
            "/identity-verification/nino",
            "nino",
            NINO,
            Status.OK,
            ivResponseMultiCredsJsonString
          )
          stubGetMatching(
            s"/enrolment-store-proxy/enrolment-store/users/$CREDENTIAL_ID_3/enrolments?type=principal",
            Status.NO_CONTENT,
            ""
          )
          stubGetMatching(
            s"/enrolment-store-proxy/enrolment-store/users/$CREDENTIAL_ID_4/enrolments?type=principal",
            Status.NO_CONTENT,
            ""
          )

          stubPost(
            s"/enrolment-store-proxy/enrolment-store/enrolments",
            Status.NO_CONTENT,
            ""
          )
          stubPut(
            s"/tax-enrolments/service/HMRC-PT/enrolment",
            Status.NO_CONTENT,
            ""
          )

          val res = buildRequest(urlPath, followRedirects = false)
            .withHttpHeaders(xSessionId, csrfContent)
            .get()

          whenReady(res) { resp =>
            val page = Jsoup.parse(resp.body)

            resp.status shouldBe SEE_OTHER
            resp.header("Location").get should include(
              "/enrol-pt/enrolment-success-no-sa"
            )
          }
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

    "the user has a insufficient confidence level" should {
      s"return $UNAUTHORIZED" in {
        stubAuthorizePostUnauthorised(insufficientConfidenceLevel)
        stubPost(s"/write/.*", OK, """{"x":2}""")

        val res =
          buildRequest(urlPath).withHttpHeaders(xSessionId, csrfContent).get()

        whenReady(res) { resp =>
          resp.status shouldBe UNAUTHORIZED
        }
      }
    }

    "the user has no active session" should {
      s"redirect to login" in {
        stubAuthorizePostUnauthorised(sessionNotFound)
        stubPost(s"/write/.*", OK, """{"x":2}""")

        val res = buildRequest(urlPath)
          .withHttpHeaders(xSessionId, csrfContent)
          .get()

        whenReady(res) { resp =>
          resp.status shouldBe SEE_OTHER
          resp.header("Location").get should include("/bas-gateway/sign-in")
        }
      }
    }
  }
}
