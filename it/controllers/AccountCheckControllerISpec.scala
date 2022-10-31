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

import java.net.URLEncoder

import helpers.TestHelper
import helpers.TestITData._
import helpers.WiremockHelper._
import helpers.messages._
import play.api.http.Status
import play.api.libs.json.Json
import play.api.libs.ws.DefaultWSCookie
import uk.gov.hmrc.auth.core.{Enrolment, EnrolmentIdentifier}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes.{MULTIPLE_ACCOUNTS, PT_ASSIGNED_TO_CURRENT_USER, PT_ASSIGNED_TO_OTHER_USER, SA_ASSIGNED_TO_CURRENT_USER, SA_ASSIGNED_TO_OTHER_USER, SINGLE_ACCOUNT}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.testOnly
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.enums.EnrolmentEnum.hmrcPTKey
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.formats.EnrolmentsFormats
import uk.gov.hmrc.taxenrolmentassignmentfrontend.reporting.AuditEvent
import uk.gov.hmrc.taxenrolmentassignmentfrontend.services.{ThrottleApplied, ThrottleDoesNotApply}

class AccountCheckControllerISpec extends TestHelper with Status {

  val urlPath: String = UrlPaths.accountCheckPath
  val ninoBelowThreshold = "QQ123400A"
  val ninoSameAsThrottlePercentage = "QQ123402A"
  val ninoAboveThrottlePercentage = "QQ123403A"
  val throttlePercentage = config.get("throttle.percentage")

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
              recordExistsInMongo shouldBe false
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
              recordExistsInMongo shouldBe false
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
              recordExistsInMongo shouldBe false
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
            recordExistsInMongo shouldBe false
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
            recordExistsInMongo shouldBe true
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
            resp.header("Location").get should include(UrlPaths.returnUrl)
            recordExistsInMongo shouldBe false
          }
        }


        s"the current user has $MULTIPLE_ACCOUNTS where Nino's last 2 digits are above throttle" in {
          val authResponse = authoriseResponseJson(optNino = Some(ninoAboveThrottlePercentage), enrolments = noEnrolments)
          stubAuthorizePost(OK, authResponse.toString())
          stubPost(s"/write/.*", OK, """{"x":2}""")
          stubGet(
            s"/enrolment-store-proxy/enrolment-store/enrolments/HMRC-PT~NINO~$ninoAboveThrottlePercentage/users",
            Status.OK,
            es0ResponseNoRecordCred
          )
          stubGetWithQueryParam(
            "/identity-verification/nino",
            "nino",
            ninoAboveThrottlePercentage,
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

          val result = buildRequest(urlPath)
            .addCookies(DefaultWSCookie("mdtp", authCookie))
            .addHttpHeaders(xSessionId, csrfContent)
            .get()

          whenReady(result) { resp =>
            resp.status shouldBe SEE_OTHER
            resp.header("Location").get should include(
              UrlPaths.enrolledPTNoSAOnAnyAccountPath)
            recordExistsInMongo shouldBe true
          }
        }

        s"the current user has $SA_ASSIGNED_TO_OTHER_USER where Nino's last 2 digits are above throttle" in {
          val authResponse = authoriseResponseJson(optNino = Some(ninoAboveThrottlePercentage), enrolments = noEnrolments)
          stubAuthorizePost(OK, authResponse.toString())
          stubPost(s"/write/.*", OK, """{"x":2}""")
          stubGet(
            s"/enrolment-store-proxy/enrolment-store/enrolments/HMRC-PT~NINO~$ninoAboveThrottlePercentage/users",
            Status.OK,
            es0ResponseNoRecordCred
          )
          stubGetWithQueryParam(
            "/identity-verification/nino",
            "nino",
            ninoAboveThrottlePercentage,
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

          val result = buildRequest(urlPath)
            .addCookies(DefaultWSCookie("mdtp", authCookie))
            .addHttpHeaders(xSessionId, csrfContent)
            .get()

          whenReady(result) { resp =>
            resp.status shouldBe SEE_OTHER
            resp.header("Location").get should include(
              UrlPaths.saOnOtherAccountInterruptPath)
            recordExistsInMongo shouldBe true
          }
        }

        s"the current user has $SA_ASSIGNED_TO_CURRENT_USER where Nino's last 2 digits are above throttle" in {
          val authResponse = authoriseResponseJson(optNino = Some(ninoAboveThrottlePercentage), enrolments = saEnrolmentOnly)
          stubAuthorizePost(OK, authResponse.toString())
          stubPost(s"/write/.*", OK, """{"x":2}""")
          stubGet(
            s"/enrolment-store-proxy/enrolment-store/enrolments/HMRC-PT~NINO~$ninoAboveThrottlePercentage/users",
            Status.OK,
            es0ResponseNoRecordCred
          )
          stubGetWithQueryParam(
            "/identity-verification/nino",
            "nino",
            ninoAboveThrottlePercentage,
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

          val result = buildRequest(urlPath)
            .addCookies(DefaultWSCookie("mdtp", authCookie))
            .addHttpHeaders(xSessionId, csrfContent)
            .get()

          whenReady(result) { res =>
            res.status shouldBe SEE_OTHER
            res.header("Location").get should include(UrlPaths.enrolledPTWithSAOnAnyAccountPath)
            recordExistsInMongo shouldBe true
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
              recordExistsInMongo shouldBe false
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
            resp.header("Location").get should include(UrlPaths.returnUrl)
            recordExistsInMongo shouldBe false

            val expectedAuditEvent = AuditEvent.auditSuccessfullyEnrolledPTWhenSANotOnOtherAccount(
              SINGLE_ACCOUNT
            )(requestWithUserDetails(), messagesApi)
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
                recordExistsInMongo shouldBe true
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
            recordExistsInMongo shouldBe true
          }
        }
      }

      s"redirect to ${UrlPaths.enrolledPTWithSAOnAnyAccountPath}" when {
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
              UrlPaths.enrolledPTWithSAOnAnyAccountPath
            )
            recordExistsInMongo shouldBe true

            val expectedAuditEvent = AuditEvent.auditSuccessfullyEnrolledPTWhenSANotOnOtherAccount(
              SA_ASSIGNED_TO_CURRENT_USER
            )(requestWithUserDetails(userDetailsNoEnrolments.copy(hasSAEnrolment = true)), messagesApi)
            verifyAuditEventSent(expectedAuditEvent)
          }
        }
      }

      s"redirect to ${UrlPaths.enrolledPTWithSAOnAnyAccountPath}" when {
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
              UrlPaths.enrolledPTWithSAOnAnyAccountPath
            )
            recordExistsInMongo shouldBe true

            val expectedAuditEvent = AuditEvent.auditSuccessfullyEnrolledPTWhenSANotOnOtherAccount(
              SA_ASSIGNED_TO_CURRENT_USER
            )(requestWithUserDetails(), messagesApi)
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
            recordExistsInMongo shouldBe true

            val expectedAuditEvent = AuditEvent.auditSuccessfullyEnrolledPTWhenSANotOnOtherAccount(
              MULTIPLE_ACCOUNTS
            )(requestWithUserDetails(), messagesApi)
            verifyAuditEventSent(expectedAuditEvent)
          }
        }
      }

      "redirect to the return url" when {
        s"the user has pt enrolment in session" should {
          "redirect to the return url" when {
            "the redirectUrl is a valid encoded relative url" in {
              val relativeUrl = testOnly.routes.TestOnlyController.successfulCall.url
              val encodedUrl = URLEncoder.encode(relativeUrl, "UTF-8")
              val urlPath = s"/protect-tax-info?redirectUrl=$encodedUrl"

              val authResponse = authoriseResponseJson(enrolments = ptEnrolmentOnly)
              stubAuthorizePost(OK, authResponse.toString())
              stubPost(s"/write/.*", OK, """{"x":2}""")

              val res = buildRequest(urlPath, followRedirects = false)
                .addCookies(DefaultWSCookie("mdtp", authCookie))
                .addHttpHeaders(xSessionId, csrfContent)
                .get()

              whenReady(res) { resp =>
                resp.status shouldBe SEE_OTHER
                resp.header("Location").get should include(
                  relativeUrl
                )
                recordExistsInMongo shouldBe false
              }
            }

            "the redirectUrl is a valid relative url" in {
              val relativeUrl = testOnly.routes.TestOnlyController.successfulCall.url
              val urlPath = s"/protect-tax-info?redirectUrl=$relativeUrl"

              val authResponse = authoriseResponseJson(enrolments = ptEnrolmentOnly)
              stubAuthorizePost(OK, authResponse.toString())
              stubPost(s"/write/.*", OK, """{"x":2}""")

              val res = buildRequest(urlPath, followRedirects = false)
                .addCookies(DefaultWSCookie("mdtp", authCookie))
                .addHttpHeaders(xSessionId, csrfContent)
                .get()

              whenReady(res) { resp =>
                resp.status shouldBe SEE_OTHER
                resp.header("Location").get should include(
                  relativeUrl
                )
                recordExistsInMongo shouldBe false
              }
            }

            "the redirectUrl is a valid encoded absolute localhost url" in {
              val absoluteUrl = testOnly.routes.TestOnlyController.successfulCall
                .absoluteURL(false, teaHost)
              val encodedUrl = URLEncoder.encode(absoluteUrl, "UTF-8")
              val urlPath = s"/protect-tax-info?redirectUrl=$encodedUrl"

              val authResponse = authoriseResponseJson(enrolments = ptEnrolmentOnly)
              stubAuthorizePost(OK, authResponse.toString())
              stubPost(s"/write/.*", OK, """{"x":2}""")

              val res = buildRequest(urlPath, followRedirects = false)
                .addCookies(DefaultWSCookie("mdtp", authCookie))
                .addHttpHeaders(xSessionId, csrfContent)
                .get()

              whenReady(res) { resp =>
                resp.status shouldBe SEE_OTHER
                resp.header("Location").get should include(
                  absoluteUrl
                )
                recordExistsInMongo shouldBe false
              }
            }

            "the redirectUrl is a valid absolute localhost url" in {
              val absoluteUrl = testOnly.routes.TestOnlyController.successfulCall
                .absoluteURL(false, teaHost)
              val urlPath = s"/protect-tax-info?redirectUrl=$absoluteUrl"

              val authResponse = authoriseResponseJson(enrolments = ptEnrolmentOnly)
              stubAuthorizePost(OK, authResponse.toString())
              stubPost(s"/write/.*", OK, """{"x":2}""")

              val res = buildRequest(urlPath, followRedirects = false)
                .addCookies(DefaultWSCookie("mdtp", authCookie))
                .addHttpHeaders(xSessionId, csrfContent)
                .get()

              whenReady(res) { resp =>
                resp.status shouldBe SEE_OTHER
                resp.header("Location").get should include(
                  absoluteUrl
                )
                recordExistsInMongo shouldBe false
              }
            }



            "the redirectUrl is a valid encoded absolute url with hostname www.tax.service.gov.uk" in {
              val absoluteUrl = testOnly.routes.TestOnlyController.successfulCall
                .absoluteURL(true, "www.tax.service.gov.uk")
              val encodedUrl = URLEncoder.encode(absoluteUrl, "UTF-8")
              val urlPath = s"/protect-tax-info?redirectUrl=$encodedUrl"

              val authResponse = authoriseResponseJson(enrolments = ptEnrolmentOnly)
              stubAuthorizePost(OK, authResponse.toString())
              stubPost(s"/write/.*", OK, """{"x":2}""")

              val res = buildRequest(urlPath, followRedirects = false)
                .addCookies(DefaultWSCookie("mdtp", authCookie))
                .addHttpHeaders(xSessionId, csrfContent)
                .get()

              whenReady(res) { resp =>
                resp.status shouldBe SEE_OTHER
                resp.header("Location").get should include(
                  absoluteUrl
                )
                recordExistsInMongo shouldBe false
              }
            }

            "the redirectUrl is a valid absolute url with hostname www.tax.service.gov.uk" in {
              val absoluteUrl = testOnly.routes.TestOnlyController.successfulCall
                .absoluteURL(true, "www.tax.service.gov.uk")
              val urlPath = s"/protect-tax-info?redirectUrl=$absoluteUrl"

              val authResponse = authoriseResponseJson(enrolments = ptEnrolmentOnly)
              stubAuthorizePost(OK, authResponse.toString())
              stubPost(s"/write/.*", OK, """{"x":2}""")

              val res = buildRequest(urlPath, followRedirects = false)
                .addCookies(DefaultWSCookie("mdtp", authCookie))
                .addHttpHeaders(xSessionId, csrfContent)
                .get()

              whenReady(res) { resp =>
                resp.status shouldBe SEE_OTHER
                resp.header("Location").get should include(
                  absoluteUrl
                )
                recordExistsInMongo shouldBe false
              }
            }
          }

          "render the error page" when {
            "an invalid redirectUrl supplied" in {
              val urlPath = s"/protect-tax-info?redirectUrl=not-a-url"

              val authResponse = authoriseResponseJson(enrolments = ptEnrolmentOnly)
              stubAuthorizePost(OK, authResponse.toString())
              stubPost(s"/write/.*", OK, """{"x":2}""")

              val res = buildRequest(urlPath, followRedirects = false)
                .addCookies(DefaultWSCookie("mdtp", authCookie))
                .addHttpHeaders(xSessionId, csrfContent)
                .get()

              whenReady(res) { resp =>
                resp.status shouldBe BAD_REQUEST
                resp.body should include(ErrorTemplateMessages.title)
                recordExistsInMongo shouldBe false
              }
            }

            "a non supported redirect host is supplied" in {
              val urlPath = s"/protect-tax-info?redirectUrl=https://notSupportedHost.com/test"

              val authResponse = authoriseResponseJson(enrolments = ptEnrolmentOnly)
              stubAuthorizePost(OK, authResponse.toString())
              stubPost(s"/write/.*", OK, """{"x":2}""")

              val res = buildRequest(urlPath, followRedirects = false)
                .addCookies(DefaultWSCookie("mdtp", authCookie))
                .addHttpHeaders(xSessionId, csrfContent)
                .get()

              whenReady(res) { resp =>
                resp.status shouldBe BAD_REQUEST
                resp.body should include(ErrorTemplateMessages.title)
                recordExistsInMongo shouldBe false
              }
            }
          }
        }
      }
    }

    s"$ThrottleDoesNotApply and user has already been through account check and enrolled for PT" should {
      val accountTypeWithExpectedRedirectUrl = Map(
        SINGLE_ACCOUNT -> UrlPaths.returnUrl,
        MULTIPLE_ACCOUNTS -> UrlPaths.enrolledPTNoSAOnAnyAccountPath,
        SA_ASSIGNED_TO_CURRENT_USER -> UrlPaths.enrolledPTWithSAOnAnyAccountPath,
        PT_ASSIGNED_TO_CURRENT_USER -> UrlPaths.returnUrl,
        SA_ASSIGNED_TO_OTHER_USER -> UrlPaths.enrolledPTSAOnOtherAccountPath,
        PT_ASSIGNED_TO_OTHER_USER -> UrlPaths.ptOnOtherAccountPath
      )
      val accountTypesThatSilentlyEnrol = List(SINGLE_ACCOUNT, MULTIPLE_ACCOUNTS, SA_ASSIGNED_TO_CURRENT_USER)

      accountTypeWithExpectedRedirectUrl.foreach { case ((accountType, redirectUrl)) =>
        if (accountTypesThatSilentlyEnrol.contains(accountType)) {
          s"not enrol for PT and redirect to $redirectUrl" when {
            s"the session cache has accountType $accountType" in {
              await(save[String](sessionId, "redirectURL", UrlPaths.returnUrl))
              await(save[AccountTypes.Value](sessionId, "ACCOUNT_TYPE", accountType))
              stubAuthorizePost(OK, authoriseResponseWithPTEnrolment().toString())
              stubPost(s"/write/.*", OK, """{"x":2}""")

              val res = buildRequest(urlPath, followRedirects = false)
                .addCookies(DefaultWSCookie("mdtp", authAndSessionCookie))
                .addHttpHeaders(xSessionId, xRequestId, sessionCookie)
                .get()

              whenReady(res) { resp =>
                resp.status shouldBe SEE_OTHER
                resp.header("Location").get should include(
                  redirectUrl
                )
                recordExistsInMongo shouldBe false
              }
            }
          }
        } else {
          s"redirect to $redirectUrl" when {
            s"the session cache has accountType $accountType" in {
              await(save[String](sessionId, "redirectURL", UrlPaths.returnUrl))
              await(save[AccountTypes.Value](sessionId, "ACCOUNT_TYPE", accountType))
              stubAuthorizePost(OK, authoriseResponseWithPTEnrolment().toString())
              stubPost(s"/write/.*", OK, """{"x":2}""")

              val res = buildRequest(urlPath, followRedirects = false)
                .addCookies(DefaultWSCookie("mdtp", authAndSessionCookie))
                .addHttpHeaders(xSessionId, xRequestId, sessionCookie)
                .get()

              whenReady(res) { resp =>
                resp.status shouldBe SEE_OTHER
                resp.header("Location").get should include(
                  redirectUrl
                )
                recordExistsInMongo shouldBe false
              }
            }
          }
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
          recordExistsInMongo shouldBe false
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
          recordExistsInMongo shouldBe false
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
          recordExistsInMongo shouldBe false
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
          recordExistsInMongo shouldBe false
        }
      }
    }
  }
}
