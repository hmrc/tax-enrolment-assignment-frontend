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

package uk.gov.hmrc.taxenrolmentassignmentfrontend.reporting

import org.scalamock.matchers.ArgCapture.CaptureOne
import play.api.Application
import play.api.inject.bind
import play.api.libs.json.{JsObject, JsString}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.{AuditConnector, AuditResult}
import uk.gov.hmrc.play.audit.model.ExtendedDataEvent
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.BaseSpec

import scala.concurrent.{ExecutionContext, Future}

class AuditHandlerSpec extends BaseSpec {

  val appname = "appname"
  val auditType = "type"
  val transactionName = "txName"

  val mockAuditConnector: AuditConnector = mock[AuditConnector]

  override implicit lazy val app: Application = localGuiceApplicationBuilder()
    .overrides(
      bind[AuditConnector].toInstance(mockAuditConnector)
    )
    .configure(
      "appName" -> appname
    )
    .build()

  val auditHandler = app.injector.instanceOf[AuditHandler]

  val detail = JsObject(Seq("detail1" -> JsString("detailValue1")))

  val event = AuditEvent(
    auditType = auditType,
    transactionName = transactionName,
    detail = detail
  )

  "AuditHandler" should {
    "audit with the correct audit event" in {

      val eventCapture: CaptureOne[ExtendedDataEvent] = CaptureOne[ExtendedDataEvent]()
      (mockAuditConnector
        .sendExtendedEvent(_: ExtendedDataEvent)(_: HeaderCarrier, _: ExecutionContext))
        .expects(capture(eventCapture), hc, *)
        .returns(Future.successful(AuditResult.Success))

      auditHandler.audit(event)

      val e = eventCapture.value

      e.auditSource shouldBe appname
      e.auditType shouldBe auditType
      e.detail shouldBe detail

      e.tags("transactionName") shouldBe transactionName
      e.tags.get("clientIP") should not be empty
    }
  }
}
