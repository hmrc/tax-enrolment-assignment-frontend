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

package controllers.actions

import helpers.WiremockHelper.{stubPost, stubPutWithRequestBody}
import helpers.messages.ErrorTemplateMessages
import helpers.TestHelper
import play.api.http.Status
import play.api.libs.json.Json
import play.api.mvc.Results.Ok
import play.api.test.FakeRequest
import uk.gov.hmrc.auth.core.{Enrolment, EnrolmentIdentifier, Enrolments}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes.SA_ASSIGNED_TO_OTHER_USER
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.actions.{
  AccountDetailsFromMongo,
  RequestWithUserDetailsFromSessionAndMongo,
  ThrottleAction,
  UserDetailsFromSession
}
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.AffinityGroup.Individual
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.enums.EnrolmentEnum.hmrcPTKey
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.formats.EnrolmentsFormats

import scala.concurrent.Future

class ThrottleActionISpec extends TestHelper with Status {

  val throttleAction = app.injector.instanceOf[ThrottleAction]
  val exampleRequestBelowThreshold = RequestWithUserDetailsFromSessionAndMongo(
    FakeRequest(),
    UserDetailsFromSession(
      "123",
      "QQ123456A",
      "gID",
      Individual,
      Enrolments(Set(Enrolment("foo"))),
      false,
      false
    ),
    "sesh",
    AccountDetailsFromMongo(SA_ASSIGNED_TO_OTHER_USER, "redirectURL")
  )
  val exampleRequestAboveThreshold = RequestWithUserDetailsFromSessionAndMongo(
    FakeRequest(),
    UserDetailsFromSession(
      "123",
      "QQ123499A",
      "gID",
      Individual,
      Enrolments(Set(Enrolment("foo"))),
      false,
      false
    ),
    "sesh",
    AccountDetailsFromMongo(SA_ASSIGNED_TO_OTHER_USER, "redirectURL")
  )
  val newEnrolment = (nino: String) =>
    Enrolment(
      s"$hmrcPTKey",
      Seq(EnrolmentIdentifier("NINO", nino)),
      "Activated",
      None
  )
  val exampleControllerFunction =
    (r: RequestWithUserDetailsFromSessionAndMongo[_]) =>
      Future.successful(Ok("no throttle"))

  "invokeBlock" should {
    "call to auth for a valid throttle scenario and redirect the user to their redirect url" in {
      stubPost(
        url = s"/write/.*",
        status = NO_CONTENT,
        responseBody = """{"x":2}"""
      )
      stubPutWithRequestBody(
        url = "/auth/enrolments",
        status = OK,
        requestBody = Json
          .toJson(
            exampleRequestBelowThreshold.userDetails.enrolments.enrolments + newEnrolment(
              exampleRequestBelowThreshold.userDetails.nino
            )
          )(EnrolmentsFormats.writes)
          .toString,
        responseBody = ""
      )

      val res = throttleAction.invokeBlock(
        exampleRequestBelowThreshold,
        exampleControllerFunction
      )
      redirectLocation(res).get shouldBe exampleRequestBelowThreshold.accountDetailsFromMongo.redirectUrl
      status(res) shouldBe SEE_OTHER
    }

    "NOT call to auth for a invalid valid throttle scenario (above threshold) and not redirect to users redirect url " in {
      val res = throttleAction.invokeBlock(
        exampleRequestAboveThreshold,
        exampleControllerFunction
      )
      status(res) shouldBe OK
      contentAsString(res) shouldBe "no throttle"
    }

    s"call to auth for a valid throttle but call to auth fails, return $INTERNAL_SERVER_ERROR" in {
      stubPost(
        url = s"/write/.*",
        status = NO_CONTENT,
        responseBody = """{"x":2}"""
      )
      stubPutWithRequestBody(
        url = "/auth/enrolments",
        status = INTERNAL_SERVER_ERROR,
        requestBody = Json
          .toJson(
            exampleRequestBelowThreshold.userDetails.enrolments.enrolments + newEnrolment(
              exampleRequestBelowThreshold.userDetails.nino
            )
          )(EnrolmentsFormats.writes)
          .toString,
        responseBody = ""
      )
      val res = throttleAction.invokeBlock(
        exampleRequestBelowThreshold,
        exampleControllerFunction
      )
      status(res) shouldBe INTERNAL_SERVER_ERROR
      contentAsString(res) should include(ErrorTemplateMessages.title)
    }
  }
}
