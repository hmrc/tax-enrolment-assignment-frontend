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

package controllers.testOnly

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

import helpers.{IntegrationSpecBase, ItUrlPaths}
import helpers.TestITData._
import play.api.http.Status.{FORBIDDEN, OK}
import play.api.libs.json.{JsArray, JsObject, JsString, Json}
import play.api.test.Helpers.{GET, contentAsString, defaultAwaitTimeout, route, status, writeableOf_AnyContentAsEmpty}
import play.api.test.FakeRequest

class TestOnlyControllerITSpec extends IntegrationSpecBase {

  val urlPath: String = ItUrlPaths.testOnlySuccessfulPath

  s"GET $urlPath" when {

    s"HMRC-PT is present and nino matches" should {
      s"return OK" in {
        val nino = NINO
        val authJsonRequest: JsObject = Json.obj(
          "authorise" -> JsArray(),
          "retrieve" -> Json.arr(
            JsString("nino"),
            JsString("allEnrolments")
          )
        )
        val enrolments =
          Json.arr(createEnrolmentJson("HMRC-PT", "NINO", nino))

        stubAuthorizePost(
          OK,
          authoriseResponseWithPTEnrolment(optNino = Some(nino), optEnrolments = Some(enrolments)).toString(),
          authJsonRequest
        )
        stubPost(s"/write/.*", OK, """{"x":2}""")

        val request = FakeRequest(GET, urlPath)
          .withSession(xSessionId, xAuthToken)
        val result = route(app, request).get

        status(result) shouldBe OK
      }
    }

    s"HMRC-PT is present and nino does not match" should {
      s"return Forbidden" in {
        val nino = NINO
        val authJsonRequest: JsObject = Json.obj(
          "authorise" -> JsArray(),
          "retrieve" -> Json.arr(
            JsString("nino"),
            JsString("allEnrolments")
          )
        )
        val enrolments =
          Json.arr(createEnrolmentJson("HMRC-PT", "NINO", mismatchNino))

        stubAuthorizePost(
          OK,
          authoriseResponseWithPTEnrolment(optNino = Some(nino), optEnrolments = Some(enrolments)).toString(),
          authJsonRequest
        )
        stubPost(s"/write/.*", OK, """{"x":2}""")

        val request = FakeRequest(GET, urlPath)
          .withSession(xSessionId, xAuthToken)
        val result = route(app, request).get

        status(result) shouldBe FORBIDDEN
        contentAsString(result) shouldBe "HMRC-PT enrolment present with wrong nino"
      }
    }

    s"HMRC-PT is not present" should {
      s"return Forbidden" in {
        val nino = NINO
        val authJsonRequest: JsObject = Json.obj(
          "authorise" -> JsArray(),
          "retrieve" -> Json.arr(
            JsString("nino"),
            JsString("allEnrolments")
          )
        )
        val enrolments = Json.arr()

        stubAuthorizePost(
          OK,
          authoriseResponseWithPTEnrolment(optNino = Some(nino), optEnrolments = Some(enrolments)).toString(),
          authJsonRequest
        )
        stubPost(s"/write/.*", OK, """{"x":2}""")

        val request = FakeRequest(GET, urlPath)
          .withSession(xSessionId, xAuthToken)
        val result = route(app, request).get

        status(result) shouldBe FORBIDDEN
        contentAsString(result) shouldBe "No HMRC-PT enrolment present"
      }
    }
  }
}
