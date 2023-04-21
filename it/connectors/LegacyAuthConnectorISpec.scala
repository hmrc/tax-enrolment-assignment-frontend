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
import helpers.WiremockHelper.{stubPost, stubPutWithRequestBody}
import play.api.http.Status.{INTERNAL_SERVER_ERROR, NO_CONTENT, OK}
import play.api.libs.json.Json
import uk.gov.hmrc.auth.core.Enrolment
import uk.gov.hmrc.taxenrolmentassignmentfrontend.connectors.LegacyAuthConnector
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.UnexpectedResponseAssigningTemporaryPTAEnrolment
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.formats.EnrolmentsFormats

class LegacyAuthConnectorISpec  extends IntegrationSpecBase {

  lazy val connector: LegacyAuthConnector = app.injector.instanceOf[LegacyAuthConnector]

  "updateEnrolments" should {
    "return Right Unit" when {
      s"auth returns $OK" in {
        val enrolments = Set(Enrolment("foo"),Enrolment("bar"))
        stubPost(url = s"/write/.*", status = NO_CONTENT, responseBody = """{"x":2}""")
        stubPutWithRequestBody(
          url ="/auth/enrolments",
          status = OK,
          requestBody = Json.toJson(enrolments)(EnrolmentsFormats.writes).toString,  responseBody = "")

        await(connector.updateEnrolments(enrolments).value) shouldBe Right(())
      }
    }
    s"return Left $UnexpectedResponseAssigningTemporaryPTAEnrolment" when {
      s"auth returns another status code" in {
        val enrolments = Set(Enrolment("foo"),Enrolment("bar"))
        stubPost(url = s"/write/.*", status = NO_CONTENT, responseBody = """{"x":2}""")
        stubPutWithRequestBody(
          url ="/auth/enrolments",
          status = INTERNAL_SERVER_ERROR,
          requestBody = Json.toJson(enrolments)(EnrolmentsFormats.writes).toString,  responseBody = "")

        await(connector.updateEnrolments(enrolments).value) shouldBe Left(UnexpectedResponseAssigningTemporaryPTAEnrolment)
      }
    }
  }
}
