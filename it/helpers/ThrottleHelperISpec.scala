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

package helpers

import helpers.TestITData.{authoriseResponseJson, saEnrolmentAsCaseClass, saEnrolmentOnly, sessionId}
import org.scalatest.concurrent.IntegrationPatience
import play.api.http.Status._
import play.api.libs.json.Json
import play.api.test.Helpers.{redirectLocation, status}
import play.api.mvc.Result
import uk.gov.hmrc.auth.core.{Enrolment, EnrolmentIdentifier}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.enums.EnrolmentEnum.hmrcPTKey
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.formats.EnrolmentsFormats
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.SessionKeys
import uk.gov.hmrc.taxenrolmentassignmentfrontend.services.ThrottleApplied
import akka.util.Timeout
import scala.concurrent.duration._
import scala.concurrent.Future
import scala.language.postfixOps

trait ThrottleHelperISpec {
_: IntegrationSpecBase =>

  val timeout = Timeout(5 seconds)

  def throttleSpecificTests(apiCall: () => Future[Result]) = {
    val newEnrolment = (nino: String) => Enrolment(s"$hmrcPTKey", Seq(EnrolmentIdentifier("NINO", nino)), "Activated", None)
    val ninoBelowThreshold = "QQ123400A"
    List(
      MULTIPLE_ACCOUNTS,
      SA_ASSIGNED_TO_CURRENT_USER,
      SA_ASSIGNED_TO_OTHER_USER
    ).foreach(accountTypeThatFallsIntoThrottle =>
      s"$accountTypeThatFallsIntoThrottle $ThrottleApplied" should {
        "redirect user to their RedirectURL and call to auth with their current enrolments plus new enrolment" in {
          val authResponse = authoriseResponseJson(optNino = Some(ninoBelowThreshold), enrolments = saEnrolmentOnly)
          stubAuthorizePost(OK, authResponse.toString())
          stubPost(s"/write/.*", OK, """{"x":2}""")
          stubPutWithRequestBody(
            url ="/auth/enrolments",
            status = OK,
            requestBody = Json.toJson(Set(saEnrolmentAsCaseClass, newEnrolment(ninoBelowThreshold)))(EnrolmentsFormats.writes).toString,
            responseBody = "")

          save[String](sessionId, SessionKeys.REDIRECT_URL, returnUrl).futureValue
          save[AccountTypes.Value](sessionId, SessionKeys.ACCOUNT_TYPE, accountTypeThatFallsIntoThrottle).futureValue

          val result: Future[Result] = apiCall()

          status(result)(timeout) shouldBe SEE_OTHER
          redirectLocation(result)(timeout).get should include(returnUrl)
        }
        s"return $INTERNAL_SERVER_ERROR if NINO wrong format" in {
          val authResponse = authoriseResponseJson(optNino = Some("foo"), enrolments = saEnrolmentOnly)
          stubAuthorizePost(OK, authResponse.toString())
          stubPost(s"/write/.*", OK, """{"x":2}""")

          save[String](sessionId, SessionKeys.REDIRECT_URL, returnUrl).futureValue
          save[AccountTypes.Value](sessionId, SessionKeys.ACCOUNT_TYPE, accountTypeThatFallsIntoThrottle).futureValue

          val result: Future[Result] = apiCall()
          whenReady(result.failed) { result =>
            result.getMessage shouldBe "nino is incorrect length 3"
          }
        }
        s"return $INTERNAL_SERVER_ERROR if put to auth fails" in {
          val authResponse = authoriseResponseJson(optNino = Some(ninoBelowThreshold), enrolments = saEnrolmentOnly)
          stubAuthorizePost(OK, authResponse.toString())
          stubPost(s"/write/.*", OK, """{"x":2}""")
          stubPutWithRequestBody(
            url ="/auth/enrolments",
            status = INTERNAL_SERVER_ERROR,
            requestBody = Json.toJson(Set(saEnrolmentAsCaseClass, newEnrolment(ninoBelowThreshold)))(EnrolmentsFormats.writes).toString,
            responseBody = "")

          save[String](sessionId, SessionKeys.REDIRECT_URL, returnUrl).futureValue
          save[AccountTypes.Value](sessionId, SessionKeys.ACCOUNT_TYPE, accountTypeThatFallsIntoThrottle).futureValue

          val result: Future[Result] = apiCall()
          status(result)(timeout) shouldBe INTERNAL_SERVER_ERROR
        }
      }
    )
  }
}
