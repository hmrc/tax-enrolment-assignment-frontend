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

import helpers.TestHelper
import helpers.TestITData._
import helpers.WiremockHelper._
import helpers.messages._
import play.api.http.Status
import play.api.libs.json.{JsString, Json}
import play.api.libs.ws.DefaultWSCookie
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes.{MULTIPLE_ACCOUNTS, SA_ASSIGNED_TO_CURRENT_USER, SA_ASSIGNED_TO_OTHER_USER, SINGLE_ACCOUNT}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.reporting.AuditEvent
import uk.gov.hmrc.auth.core.{Enrolment, EnrolmentIdentifier}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes.{MULTIPLE_ACCOUNTS, PT_ASSIGNED_TO_CURRENT_USER, PT_ASSIGNED_TO_OTHER_USER, SA_ASSIGNED_TO_CURRENT_USER, SA_ASSIGNED_TO_OTHER_USER, SINGLE_ACCOUNT}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.UsersAssignedEnrolment
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.enums.EnrolmentEnum.hmrcPTKey
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.formats.EnrolmentsFormats
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.SessionKeys.{USER_ASSIGNED_PT_ENROLMENT, USER_ASSIGNED_SA_ENROLMENT}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.services.{ThrottleApplied, ThrottleDoesNotApply}

class AccountCheckControllerISpec extends TestHelper with Status {

  val urlPath: String = UrlPaths.accountCheckPath
  val ninoBelowThreshold = "QQ123400A"
  val newEnrolment = (nino: String) => Enrolment(s"$hmrcPTKey", Seq(EnrolmentIdentifier("NINO", nino)), "Activated", None)

  s"GET $urlPath" when {
      s"$ThrottleApplied" should {
        "call to auth with their current enrolments plus new enrolment and redirect the user to their RedirectURL" when {
          s"the current user has $MULTIPLE_ACCOUNTS for a Nino within threshold" in {
            val authResponse = authoriseResponseJson(optNino = Some(ninoBelowThreshold), enrolments = noEnrolments)
            stubAuthorizePost(OK, authResponse.toString())
            stubPost(s"/write/.*", OK, """{"x":2}""")
            stubGet(
              s"/enrolment-store-proxy/enrolment-store/enrolments/HMRC-PT~NINO~$ninoBelowThreshold/users",
              Status.OK,
              es0ResponseNoRecordCred
            )
            stubGetWithQueryParam(
              "/identity-verification/nino",
              "nino",
              ninoBelowThreshold,
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
            stubPutWithRequestBody(
              url = "/auth/enrolments",
              status = OK,
              requestBody = Json.toJson(Set(newEnrolment(ninoBelowThreshold)))(EnrolmentsFormats.writes).toString,
              responseBody = "")

            val result = buildRequest(urlPath)
              .addCookies(DefaultWSCookie("mdtp", authCookie))
              .addHttpHeaders(xSessionId, csrfContent)
              .get()

            whenReady(result) { res =>
              res.status shouldBe SEE_OTHER
              res.header("Location").get should include(UrlPaths.returnUrl)
            }
          }

          s"the current user has $SA_ASSIGNED_TO_OTHER_USER for a Nino within threshold" in {
            val authResponse = authoriseResponseJson(optNino = Some(ninoBelowThreshold), enrolments = noEnrolments)
            stubAuthorizePost(OK, authResponse.toString())
            stubPost(s"/write/.*", OK, """{"x":2}""")
            stubGet(
              s"/enrolment-store-proxy/enrolment-store/enrolments/HMRC-PT~NINO~$ninoBelowThreshold/users",
              Status.OK,
              es0ResponseNoRecordCred
            )
            stubGetWithQueryParam(
              "/identity-verification/nino",
              "nino",
              ninoBelowThreshold,
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
            stubPutWithRequestBody(
              url = "/auth/enrolments",
              status = OK,
              requestBody = Json.toJson(Set(newEnrolment(ninoBelowThreshold)))(EnrolmentsFormats.writes).toString,
              responseBody = "")

            val result = buildRequest(urlPath)
              .addCookies(DefaultWSCookie("mdtp", authCookie))
              .addHttpHeaders(xSessionId, csrfContent)
              .get()

            whenReady(result) { res =>
              res.status shouldBe SEE_OTHER
              res.header("Location").get should include(UrlPaths.returnUrl)
            }
          }

          s"the current user has $SA_ASSIGNED_TO_CURRENT_USER for a Nino within threshold" in {
            val authResponse = authoriseResponseJson(optNino = Some(ninoBelowThreshold), enrolments = saEnrolmentOnly)
            stubAuthorizePost(OK, authResponse.toString())
            stubPost(s"/write/.*", OK, """{"x":2}""")
            stubGet(
              s"/enrolment-store-proxy/enrolment-store/enrolments/HMRC-PT~NINO~$ninoBelowThreshold/users",
              Status.OK,
              es0ResponseNoRecordCred
            )
            stubGetWithQueryParam(
              "/identity-verification/nino",
              "nino",
              ninoBelowThreshold,
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
            stubPutWithRequestBody(
              url = "/auth/enrolments",
              status = OK,
              requestBody = Json.toJson(Set(saEnrolmentAsCaseClass, newEnrolment(ninoBelowThreshold)))(EnrolmentsFormats.writes).toString,
              responseBody = "")

            val result = buildRequest(urlPath)
              .addCookies(DefaultWSCookie("mdtp", authCookie))
              .addHttpHeaders(xSessionId, csrfContent)
              .get()

            whenReady(result) { res =>
              res.status shouldBe SEE_OTHER
              res.header("Location").get should include(UrlPaths.returnUrl)
            }
          }
        }
      }

    s"$ThrottleDoesNotApply" should {
      "not redirect user to their redirect url and follow standard logic" when {
        s"the current user has $PT_ASSIGNED_TO_CURRENT_USER for a Nino within threshold" in {
          val authResponse = authoriseResponseJson(optNino = Some(ninoBelowThreshold), enrolments = ptEnrolmentOnly)
          stubAuthorizePost(OK, authResponse.toString())
          stubPost(s"/write/.*", OK, """{"x":2}""")

          val res = buildRequest(urlPath)
            .addCookies(DefaultWSCookie("mdtp", authCookie))
            .addHttpHeaders(xSessionId, csrfContent)
            .get()

          whenReady(res) { resp =>
            resp.status shouldBe SEE_OTHER
            resp.header("Location").get should include(UrlPaths.returnUrl)
          }
        }

        s"the current user has $PT_ASSIGNED_TO_OTHER_USER for a Nino within threshold" in {
          val authResponse = authoriseResponseJson(optNino = Some(ninoBelowThreshold), enrolments = noEnrolments)
          stubAuthorizePost(OK, authResponse.toString())
          stubPost(s"/write/.*", OK, """{"x":2}""")
          stubGet(
            s"/enrolment-store-proxy/enrolment-store/enrolments/HMRC-PT~NINO~$ninoBelowThreshold/users",
            Status.OK,
            es0ResponseNotMatchingCred
          )

          val res = buildRequest(urlPath)
            .addCookies(DefaultWSCookie("mdtp", authCookie))
            .addHttpHeaders(xSessionId, csrfContent)
            .get()

          whenReady(res) { resp =>
            resp.status shouldBe SEE_OTHER
            resp.header("Location").get should include(UrlPaths.ptOnOtherAccountPath)
          }
        }

        s"the current user has $SINGLE_ACCOUNT for a Nino within threshold" in {
          val authResponse = authoriseResponseJson(optNino = Some(ninoBelowThreshold), enrolments = noEnrolments)
          stubAuthorizePost(OK, authResponse.toString())
          stubPost(s"/write/.*", OK, """{"x":2}""")
          stubGet(
            s"/enrolment-store-proxy/enrolment-store/enrolments/HMRC-PT~NINO~$ninoBelowThreshold/users",
            Status.OK,
            es0ResponseNoRecordCred
          )
          stubGetWithQueryParam(
            "/identity-verification/nino",
            "nino",
            ninoBelowThreshold,
            Status.OK,
            ivResponseSingleCredsJsonString
          )
          stubPut(
            s"/tax-enrolments/service/HMRC-PT/enrolment",
            Status.NO_CONTENT,
            ""
          )

          val res = buildRequest(urlPath)
            .addCookies(DefaultWSCookie("mdtp", authCookie))
            .addHttpHeaders(xSessionId, csrfContent)
            .get()

          whenReady(res) { resp =>
            resp.status shouldBe SEE_OTHER
            resp.header("Location").get should include(UrlPaths.returnUrl)
          }
        }
      }

      s"redirect to ${UrlPaths.returnUrl}" when {
        "a user has one credential associated with their nino" that {
          "has a PT enrolment in the session" in {
            val authResponse = authoriseResponseJson(enrolments = ptEnrolmentOnly)
            stubAuthorizePost(OK, authResponse.toString())
            stubPost(s"/write/.*", OK, """{"x":2}""")

            val res = buildRequest(urlPath, followRedirects = true)
              .addCookies(DefaultWSCookie("mdtp", authCookie))
              .addHttpHeaders(xSessionId, csrfContent)
              .get()

            whenReady(res) { resp =>
              resp.status shouldBe OK
              resp.uri.toString shouldBe UrlPaths.returnUrl
            }
          }
          "has PT enrolment in EACD but not the session" in {
            val authResponse = authoriseResponseJson()
            stubAuthorizePost(OK, authResponse.toString())
            stubPost(s"/write/.*", OK, """{"x":2}""")
            stubGet(
              s"/enrolment-store-proxy/enrolment-store/enrolments/HMRC-PT~NINO~$NINO/users",
              Status.OK,
              es0ResponseMatchingCred
            )
            val res = buildRequest(urlPath, followRedirects = true)
              .addCookies(DefaultWSCookie("mdtp", authCookie))
              .addHttpHeaders(xSessionId, csrfContent)
              .get()

            whenReady(res) { resp =>
              resp.status shouldBe OK
              resp.uri.toString shouldBe UrlPaths.returnUrl
            }
          }
        }
      }

      s"silently enrol for PT and redirect to users redirect url" when {
      "the user is a single account holder with no PT enrolment in session or EACD" in {
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

          val res = buildRequest(urlPath)
            .addCookies(DefaultWSCookie("mdtp", authCookie))
            .addHttpHeaders(xSessionId, csrfContent)
            .get()

          whenReady(res) { resp =>
            resp.status shouldBe SEE_OTHER
            resp.header("Location").get should include(UrlPaths.returnUrl)

            val expectedAuditEvent = AuditEvent.auditSuccessfullyEnrolledPTWhenSANotOnOtherAccount(
              SINGLE_ACCOUNT
            )(requestWithUserDetails())
            verifyAuditEventSent(expectedAuditEvent)
          }
        }
      }

      s"redirect to ${UrlPaths.ptOnOtherAccountPath}" when {
        "a user has other credentials associated with their NINO" that {
          "includes one with a PT enrolment" in {
              stubAuthorizePost(OK, authoriseResponseJson().toString())
              stubPost(s"/write/.*", OK, """{"x":2}""")
              stubGet(
                s"/enrolment-store-proxy/enrolment-store/enrolments/HMRC-PT~NINO~$NINO/users",
                Status.OK,
                es0ResponseNotMatchingCred
              )

              val res = buildRequest(urlPath)
                .addCookies(DefaultWSCookie("mdtp", authCookie))
                .addHttpHeaders(xSessionId, csrfContent)
                .get()

              whenReady(res) { resp =>
                resp.status shouldBe SEE_OTHER
                resp.header("Location").get should include(
                  UrlPaths.ptOnOtherAccountPath
                )
              }
            }
          }
        }

      s"redirect to ${UrlPaths.saOnOtherAccountInterruptPath}" when {
        "the user has SA enrolment on an other account" in {
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

          val res = buildRequest(urlPath)
            .addCookies(DefaultWSCookie("mdtp", authCookie))
            .addHttpHeaders(xSessionId, csrfContent)
            .get()

          whenReady(res) { resp =>
            resp.status shouldBe SEE_OTHER
            resp.header("Location").get should include(
              UrlPaths.saOnOtherAccountInterruptPath
            )
          }
        }
      }

      s"redirect to ${UrlPaths.enrolledPTNoSAOnAnyAccountPath}" when {
      "the user has no PT enrolments but current credential has SA enrolment in session" in {
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

          val res = buildRequest(urlPath)
            .addCookies(DefaultWSCookie("mdtp", authCookie))
            .addHttpHeaders(xSessionId, csrfContent)
            .get()

          whenReady(res) { resp =>
            resp.status shouldBe SEE_OTHER
            resp.header("Location").get should include(
              UrlPaths.enrolledPTNoSAOnAnyAccountPath
            )

            val expectedAuditEvent = AuditEvent.auditSuccessfullyEnrolledPTWhenSANotOnOtherAccount(
              SA_ASSIGNED_TO_CURRENT_USER
            )(requestWithUserDetails(userDetailsNoEnrolments.copy(hasSAEnrolment = true)))
            verifyAuditEventSent(expectedAuditEvent)
          }
        }
      }

      s"redirect to ${UrlPaths.enrolledPTNoSAOnAnyAccountPath}" when {
        "the user has no PT enrolments but current credential has SA enrolment in EACD" in {
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

          val res = buildRequest(urlPath)
            .addCookies(DefaultWSCookie("mdtp", authCookie))
            .addHttpHeaders(xSessionId, csrfContent)
            .get()

          whenReady(res) { resp =>
            resp.status shouldBe SEE_OTHER
            resp.header("Location").get should include(
              UrlPaths.enrolledPTNoSAOnAnyAccountPath
            )
            val expectedAuditEvent = AuditEvent.auditSuccessfullyEnrolledPTWhenSANotOnOtherAccount(
              SA_ASSIGNED_TO_CURRENT_USER
            )(requestWithUserDetails())
            verifyAuditEventSent(expectedAuditEvent)
          }
        }
      }

      s"redirect to ${UrlPaths.enrolledPTNoSAOnAnyAccountPath}" when {
        "the user has no PT or SA enrolments" in {
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

          val res = buildRequest(urlPath)
            .addCookies(DefaultWSCookie("mdtp", authCookie))
            .addHttpHeaders(xSessionId, csrfContent)
            .get()

          whenReady(res) { resp =>
            resp.status shouldBe SEE_OTHER
            resp.header("Location").get should include(
              UrlPaths.enrolledPTNoSAOnAnyAccountPath
            )
            val expectedAuditEvent = AuditEvent.auditSuccessfullyEnrolledPTWhenSANotOnOtherAccount(
              MULTIPLE_ACCOUNTS
            )(requestWithUserDetails())
            verifyAuditEventSent(expectedAuditEvent)
          }
        }
      }

      s"redirect to ${UrlPaths.enrolledPTSAOnOtherAccountPath}" when {
        s"the user has accountType $SA_ASSIGNED_TO_OTHER_USER in cache and has ptEnrolment in session" in {
          await(save[String](sessionId, "redirectURL", UrlPaths.returnUrl))
          await(
            save[AccountTypes.Value](
              sessionId,
              "ACCOUNT_TYPE",
              SA_ASSIGNED_TO_OTHER_USER
            )
          )
          val authResponse = authoriseResponseJson(enrolments = ptEnrolmentOnly)
          stubAuthorizePost(OK, authResponse.toString())
          stubPost(s"/write/.*", OK, """{"x":2}""")

          val res = buildRequest(urlPath, followRedirects = false)
            .addCookies(DefaultWSCookie("mdtp", authAndSessionCookie))
            .addHttpHeaders(xSessionId, xRequestId, sessionCookie)
            .get()

          whenReady(res) { resp =>
            resp.status shouldBe SEE_OTHER
            resp.header("Location").get should include(
              UrlPaths.enrolledPTSAOnOtherAccountPath
            )
          }
        }
      }
    }

    "an authorised user with no credential uses the service" should {
      s"render the error page" in {
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
          .addCookies(DefaultWSCookie("mdtp", authCookie))
          .addHttpHeaders(xSessionId, csrfContent)
          .get()

        whenReady(res) { resp =>
          resp.status shouldBe INTERNAL_SERVER_ERROR
          resp.body should include(ErrorTemplateMessages.title)
        }
      }
    }

    "an authorised user but IV returns internal error" should {
      s"render the error page" in {
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
          .addCookies(DefaultWSCookie("mdtp", authCookie))
          .addHttpHeaders(xSessionId, csrfContent)
          .get()

        whenReady(res) { resp =>
          resp.status shouldBe INTERNAL_SERVER_ERROR
          resp.body should include(ErrorTemplateMessages.title)
        }
      }
    }

    "the user has a session missing required element NINO" should {
      s"redirect to ${UrlPaths.unauthorizedPath}" in {
        val authResponse = authoriseResponseJson(optNino = None)
        stubAuthorizePost(OK, authResponse.toString())
        stubPost(s"/write/.*", OK, """{"x":2}""")

        val res =
          buildRequest(urlPath).addCookies(DefaultWSCookie("mdtp", authCookie))
            .addHttpHeaders(xSessionId, csrfContent).get()

        whenReady(res) { resp =>
          resp.status shouldBe SEE_OTHER
          resp.header("Location").get should include(UrlPaths.unauthorizedPath)
        }
      }
    }

    "the user has a session missing required element Credentials" should {
      s"redirect to ${UrlPaths.unauthorizedPath}" in {
        val authResponse = authoriseResponseJson(optCreds = None)
        stubAuthorizePost(OK, authResponse.toString())
        stubPost(s"/write/.*", OK, """{"x":2}""")

        val res =
          buildRequest(urlPath).addCookies(DefaultWSCookie("mdtp", authCookie))
            .addHttpHeaders(xSessionId, csrfContent).get()

        whenReady(res) { resp =>
          resp.status shouldBe SEE_OTHER
          resp.header("Location").get should include(UrlPaths.unauthorizedPath)
        }
      }
    }

    "the user has a insufficient confidence level" should {
      s"redirect to ${UrlPaths.unauthorizedPath}" in {
        stubAuthorizePostUnauthorised(insufficientConfidenceLevel)
        stubPost(s"/write/.*", OK, """{"x":2}""")

        val res =
          buildRequest(urlPath)
            .addCookies(DefaultWSCookie("mdtp", authCookie))
            .addHttpHeaders(xSessionId, csrfContent).get()

        whenReady(res) { resp =>
          resp.status shouldBe SEE_OTHER
          resp.header("Location").get should include(UrlPaths.unauthorizedPath)
        }
      }
    }

    "the user has no active session" should {
      s"redirect to login" in {
        stubAuthorizePostUnauthorised(sessionNotFound)
        stubPost(s"/write/.*", OK, """{"x":2}""")

        val res = buildRequest(urlPath)
          .addCookies(DefaultWSCookie("mdtp", authCookie))
          .addHttpHeaders(xSessionId, csrfContent)
          .get()

        whenReady(res) { resp =>
          resp.status shouldBe SEE_OTHER
          resp.header("Location").get should include("/bas-gateway/sign-in")
        }
      }
    }
  }
}
