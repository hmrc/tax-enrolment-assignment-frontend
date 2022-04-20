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
import play.api.libs.json.Json
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes.{
  MULTIPLE_ACCOUNTS,
  PT_ASSIGNED_TO_CURRENT_USER,
  PT_ASSIGNED_TO_OTHER_USER,
  SA_ASSIGNED_TO_CURRENT_USER,
  SA_ASSIGNED_TO_OTHER_USER,
  SINGLE_ACCOUNT
}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.testOnly
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.forms.KeepAccessToSAThroughPTA
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.SessionKeys.{
  KEEP_ACCESS_TO_SA_THROUGH_PTA_FORM,
  USER_ASSIGNED_SA_ENROLMENT
}

class KeepAccessToSAControllerISpec extends IntegrationSpecBase with Status {

  val teaHost = s"localhost:$port"
  val returnUrl: String = testOnly.routes.TestOnlyController.successfulCall
    .absoluteURL(false, teaHost)
  val urlPath =
    s"/enrol-pt/other-user-id-has-sa/keep-access-to-sa-from-pta"

  val sessionCookie
    : (String, String) = ("COOKIE" -> createSessionCookieAsString(sessionData))

  s"GET $urlPath" when {
    "the session cache contains Account type of SA_ASSIGNED_TO_OTHER_USER and no page data" should {
      s"render the KeepAccessToSA page with radio buttons unchecked" in {
        await(save[String](sessionId, "redirectURL", returnUrl))
        await(
          save[AccountTypes.Value](
            sessionId,
            "ACCOUNT_TYPE",
            SA_ASSIGNED_TO_OTHER_USER
          )
        )
        val authResponse = authoriseResponseJson()
        stubAuthorizePost(OK, authResponse.toString())
        stubPost(s"/write/.*", OK, """{"x":2}""")

        val res = buildRequest(urlPath, followRedirects = true)
          .withHttpHeaders(xSessionId, xRequestId, sessionCookie)
          .get()

        whenReady(res) { resp =>
          val page = Jsoup.parse(resp.body)

          resp.status shouldBe OK
          page.title should include(TestITData.keepAccessToSAPageTitle)
        }
      }

      "the session cache contains Account type of SA_ASSIGNED_TO_OTHER_USER and no page data" should {
        s"render the KeepAccessToSA page with radio buttons unchecked" in {
          await(save[String](sessionId, "redirectURL", returnUrl))
          await(
            save[AccountTypes.Value](
              sessionId,
              "ACCOUNT_TYPE",
              SA_ASSIGNED_TO_OTHER_USER
            )
          )
          val authResponse = authoriseResponseJson()
          stubAuthorizePost(OK, authResponse.toString())
          stubPost(s"/write/.*", OK, """{"x":2}""")

          val res = buildRequest(urlPath, followRedirects = true)
            .withHttpHeaders(xSessionId, xRequestId, sessionCookie)
            .get()

          whenReady(res) { resp =>
            val page = Jsoup.parse(resp.body)
            resp.status shouldBe OK
            page.title should include(TestITData.keepAccessToSAPageTitle)
            val radioInputs = page.getElementsByClass("govuk-radios__input")
            radioInputs.size() shouldBe 2
            radioInputs.get(0).attr("value") shouldBe "yes"
            radioInputs.get(0).hasAttr("checked") shouldBe false
            radioInputs.get(1).attr("value") shouldBe "no"
            radioInputs.get(1).hasAttr("checked") shouldBe false
          }
        }
      }

      "the session cache contains Account type of SA_ASSIGNED_TO_OTHER_USER and user has previously selected yes" should {
        s"render the KeepAccessToSA page with radio buttons unchecked" in {
          await(save[String](sessionId, "redirectURL", returnUrl))
          await(
            save[AccountTypes.Value](
              sessionId,
              "ACCOUNT_TYPE",
              SA_ASSIGNED_TO_OTHER_USER
            )
          )
          await(
            save[KeepAccessToSAThroughPTA](
              sessionId,
              KEEP_ACCESS_TO_SA_THROUGH_PTA_FORM,
              KeepAccessToSAThroughPTA(true)
            )
          )
          val authResponse = authoriseResponseJson()
          stubAuthorizePost(OK, authResponse.toString())
          stubPost(s"/write/.*", OK, """{"x":2}""")

          val res = buildRequest(urlPath, followRedirects = true)
            .withHttpHeaders(xSessionId, xRequestId, sessionCookie)
            .get()

          whenReady(res) { resp =>
            val page = Jsoup.parse(resp.body)

            resp.status shouldBe OK
            page.title should include(TestITData.keepAccessToSAPageTitle)
            val radioInputs = page.getElementsByClass("govuk-radios__input")
            radioInputs.size() shouldBe 2
            radioInputs.get(0).attr("value") shouldBe "yes"
            radioInputs.get(0).hasAttr("checked") shouldBe true
            radioInputs.get(1).attr("value") shouldBe "no"
            radioInputs.get(1).hasAttr("checked") shouldBe false
          }
        }
      }
      "the session cache contains Account type of SA_ASSIGNED_TO_OTHER_USER and user has previously selected no" should {
        s"render the KeepAccessToSA page with radio buttons unchecked" in {
          await(save[String](sessionId, "redirectURL", returnUrl))
          await(
            save[AccountTypes.Value](
              sessionId,
              "ACCOUNT_TYPE",
              SA_ASSIGNED_TO_OTHER_USER
            )
          )
          await(
            save[KeepAccessToSAThroughPTA](
              sessionId,
              KEEP_ACCESS_TO_SA_THROUGH_PTA_FORM,
              KeepAccessToSAThroughPTA(false)
            )
          )
          val authResponse = authoriseResponseJson()
          stubAuthorizePost(OK, authResponse.toString())
          stubPost(s"/write/.*", OK, """{"x":2}""")

          val res = buildRequest(urlPath, followRedirects = true)
            .withHttpHeaders(xSessionId, xRequestId, sessionCookie)
            .get()

          whenReady(res) { resp =>
            val page = Jsoup.parse(resp.body)

            resp.status shouldBe OK
            page.title should include(TestITData.keepAccessToSAPageTitle)
            val radioInputs = page.getElementsByClass("govuk-radios__input")
            radioInputs.size() shouldBe 2
            radioInputs.get(0).attr("value") shouldBe "yes"
            radioInputs.get(0).hasAttr("checked") shouldBe false
            radioInputs.get(1).attr("value") shouldBe "no"
            radioInputs.get(1).hasAttr("checked") shouldBe true
          }
        }
      }

      List(
        SINGLE_ACCOUNT,
        PT_ASSIGNED_TO_OTHER_USER,
        PT_ASSIGNED_TO_CURRENT_USER,
        MULTIPLE_ACCOUNTS,
        SA_ASSIGNED_TO_CURRENT_USER
      ).foreach { accountType =>
        s"the session cache has Account type of $accountType" should {
          s"redirect to accountCheck" in {
            await(save[String](sessionId, "redirectURL", returnUrl))
            await(
              save[AccountTypes.Value](sessionId, "ACCOUNT_TYPE", accountType)
            )
            val authResponse = authoriseResponseJson()
            stubAuthorizePost(OK, authResponse.toString())
            stubPost(s"/write/.*", OK, """{"x":2}""")
            val res = buildRequest(urlPath, followRedirects = false)
              .withHttpHeaders(xSessionId, xRequestId, sessionCookie)
              .get()

            whenReady(res) { resp =>
              val page = Jsoup.parse(resp.body)

              resp.status shouldBe SEE_OTHER
              resp.header("Location").get should include(
                s"/tax-enrolment-assignment-frontend"
              )
            }
          }
        }
      }

      "the session cache is empty" should {
        "render the error page" in {
          val authResponse = authoriseResponseJson()
          stubAuthorizePost(OK, authResponse.toString())
          stubPost(s"/write/.*", OK, """{"x":2}""")
          val res = buildRequest(urlPath, followRedirects = true)
            .withHttpHeaders(xSessionId, xRequestId, sessionCookie)
            .get()

          whenReady(res) { resp =>
            resp.status shouldBe OK
            resp.body should include("There was a problem")
          }
        }
      }

      "the user has a session missing required element NINO" should {
        s"return $UNAUTHORIZED" in {
          val authResponse = authoriseResponseJson(optNino = None)
          stubAuthorizePost(OK, authResponse.toString())
          stubPost(s"/write/.*", OK, """{"x":2}""")

          val res =
            buildRequest(urlPath)
              .withHttpHeaders(
                xSessionId,
                xRequestId,
                csrfContent,
                sessionCookie
              )
              .get()

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
            buildRequest(urlPath)
              .withHttpHeaders(
                xSessionId,
                xRequestId,
                csrfContent,
                sessionCookie
              )
              .get()

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
            buildRequest(urlPath)
              .withHttpHeaders(
                xSessionId,
                xRequestId,
                csrfContent,
                sessionCookie
              )
              .get()

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
            .withHttpHeaders(xSessionId, xRequestId, csrfContent)
            .get()

          whenReady(res) { resp =>
            resp.status shouldBe SEE_OTHER
            resp.header("Location").get should include("/bas-gateway/sign-in")
          }
        }
      }
    }
  }

  s"POST $urlPath" when {
    "the session cache contains Account type of SA_ASSIGNED_TO_OTHER_USER" should {
      s"redirect to the signinAgain url" when {
        "the user selects yes" in {
          await(save[String](sessionId, "redirectURL", returnUrl))
          await(
            save[AccountTypes.Value](
              sessionId,
              "ACCOUNT_TYPE",
              SA_ASSIGNED_TO_OTHER_USER
            )
          )

          val authResponse = authoriseResponseJson()
          stubAuthorizePost(OK, authResponse.toString())
          stubPost(s"/write/.*", OK, """{"x":2}""")

          val res = buildRequest(urlPath, followRedirects = false)
            .withHttpHeaders(csrfContent, xSessionId, xRequestId, sessionCookie)
            .post(Json.obj("select-continue" -> "yes"))

          whenReady(res) { resp =>
            resp.status shouldBe SEE_OTHER
            resp.header("Location").get should include(
              "/enrol-pt/sign-in-again"
            )
          }
        }
      }

      s"enrol the user for PT and redirect to the EnrolledPTWithSAOnOtherAccount url" when {
        "the user selects no" in {
          await(save[String](sessionId, "redirectURL", returnUrl))
          await(
            save[AccountTypes.Value](
              sessionId,
              "ACCOUNT_TYPE",
              SA_ASSIGNED_TO_OTHER_USER
            )
          )

          val authResponse = authoriseResponseJson()
          stubAuthorizePost(OK, authResponse.toString())
          stubPost(s"/write/.*", OK, """{"x":2}""")
          stubPut(
            s"/tax-enrolments/service/HMRC-PT/enrolment",
            Status.NO_CONTENT,
            ""
          )

          val res = buildRequest(urlPath, followRedirects = false)
            .withHttpHeaders(csrfContent, xSessionId, xRequestId, sessionCookie)
            .post(Json.obj("select-continue" -> "no"))

          whenReady(res) { resp =>
            resp.status shouldBe SEE_OTHER
            resp.header("Location").get should include(
              "/enrol-pt/enrolment-success-sa-access-not-wanted"
            )
          }
        }
      }

      "render the error page if enrolment fails" when {
        "the use selected no" in {
          await(save[String](sessionId, "redirectURL", returnUrl))
          await(
            save[AccountTypes.Value](
              sessionId,
              "ACCOUNT_TYPE",
              SA_ASSIGNED_TO_OTHER_USER
            )
          )

          val authResponse = authoriseResponseJson()
          stubAuthorizePost(OK, authResponse.toString())
          stubPost(s"/write/.*", OK, """{"x":2}""")
          stubPut(
            s"/tax-enrolments/service/HMRC-PT/enrolment",
            Status.INTERNAL_SERVER_ERROR,
            ""
          )

          val res = buildRequest(urlPath, followRedirects = false)
            .withHttpHeaders(csrfContent, xSessionId, xRequestId, sessionCookie)
            .post(Json.obj("select-continue" -> "no"))

          whenReady(res) { resp =>
            resp.status shouldBe OK
            resp.body should include("There was a problem")
          }
        }
      }
    }

    List(
      SINGLE_ACCOUNT,
      PT_ASSIGNED_TO_OTHER_USER,
      PT_ASSIGNED_TO_CURRENT_USER,
      MULTIPLE_ACCOUNTS,
      SA_ASSIGNED_TO_CURRENT_USER
    ).foreach { accountType =>
      s"the session cache has Account type of $accountType" should {
        s"redirect to accountCheck" when {
          "yes is selected" in {
            await(save[String](sessionId, "redirectURL", returnUrl))
            await(
              save[AccountTypes.Value](sessionId, "ACCOUNT_TYPE", accountType)
            )
            val authResponse = authoriseResponseJson()
            stubAuthorizePost(OK, authResponse.toString())
            stubPost(s"/write/.*", OK, """{"x":2}""")

            val res = buildRequest(urlPath, followRedirects = false)
              .withHttpHeaders(
                csrfContent,
                xSessionId,
                xRequestId,
                sessionCookie
              )
              .post(Json.obj("select-continue" -> "yes"))

            whenReady(res) { resp =>
              val page = Jsoup.parse(resp.body)

              resp.status shouldBe SEE_OTHER
              resp.header("Location").get should include(
                s"/tax-enrolment-assignment-frontend"
              )
            }
          }
          "no is selected" in {
            await(save[String](sessionId, "redirectURL", returnUrl))
            await(
              save[AccountTypes.Value](sessionId, "ACCOUNT_TYPE", accountType)
            )
            val authResponse = authoriseResponseJson()
            stubAuthorizePost(OK, authResponse.toString())
            stubPost(s"/write/.*", OK, """{"x":2}""")

            val res = buildRequest(urlPath, followRedirects = false)
              .withHttpHeaders(
                csrfContent,
                xSessionId,
                xRequestId,
                sessionCookie
              )
              .post(Json.obj("select-continue" -> "no"))

            whenReady(res) { resp =>
              val page = Jsoup.parse(resp.body)

              resp.status shouldBe SEE_OTHER
              resp.header("Location").get should include(
                s"/tax-enrolment-assignment-frontend"
              )
            }
          }
        }
      }
    }

    "the session cache does not contain the redirect url" should {
      s"render the error page" when {
        "yes is selected" in {
          val authResponse = authoriseResponseJson()
          stubAuthorizePost(OK, authResponse.toString())
          stubPost(s"/write/.*", OK, """{"x":2}""")

          val res = buildRequest(urlPath, followRedirects = true)
            .withHttpHeaders(xSessionId, xRequestId, sessionCookie, csrfContent)
            .post(Json.obj("select-continue" -> "yes"))

          whenReady(res) { resp =>
            resp.status shouldBe OK
            resp.body should include("There was a problem")
          }
        }

        "no is selected" in {
          val authResponse = authoriseResponseJson()
          stubAuthorizePost(OK, authResponse.toString())
          stubPost(s"/write/.*", OK, """{"x":2}""")

          val res = buildRequest(urlPath, followRedirects = true)
            .withHttpHeaders(xSessionId, xRequestId, sessionCookie, csrfContent)
            .post(Json.obj("select-continue" -> "no"))

          whenReady(res) { resp =>
            resp.status shouldBe OK
            resp.body should include("There was a problem")
          }
        }
      }
    }

    "an invalid form is supplied" should {
      "render the keepAccessToSA page with errors" in {
        val authResponse = authoriseResponseJson()
        stubAuthorizePost(OK, authResponse.toString())
        stubPost(s"/write/.*", OK, """{"x":2}""")

        val res = buildRequest(urlPath, followRedirects = true)
          .withHttpHeaders(xSessionId, xRequestId, sessionCookie, csrfContent)
          .post(Json.obj("select-continue" -> "error"))

        whenReady(res) { resp =>
          resp.status shouldBe BAD_REQUEST
          val page = Jsoup.parse(resp.body)
          page
            .getElementsByClass("govuk-error-summary__title")
            .text() shouldBe "There is a problem"
          page
            .getElementsByClass("govuk-list govuk-error-summary__list")
            .first()
            .text() shouldBe "Select option to continue"
        }
      }
    }
  }
}
