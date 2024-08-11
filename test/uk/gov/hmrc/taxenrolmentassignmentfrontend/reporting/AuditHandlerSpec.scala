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

import org.mockito.ArgumentCaptor
import org.mockito.MockitoSugar.{mock, when}
import play.api.Application
import play.api.inject.bind
import play.api.libs.json.{JsObject, JsString}
import uk.gov.hmrc.play.audit.http.connector.{AuditConnector, AuditResult}
import uk.gov.hmrc.play.audit.model.ExtendedDataEvent
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.BaseSpec

import scala.concurrent.Future

class AuditHandlerSpec extends BaseSpec {

  val appName = "appname"
  val auditType = "type"
  val transactionName = "txName"

  val mockAuditConnector: AuditConnector = mock[AuditConnector]

  override implicit lazy val app: Application = localGuiceApplicationBuilder()
    .overrides(
      bind[AuditConnector].toInstance(mockAuditConnector)
    )
    .configure(
      "appName" -> appName
    )
    .build()

  val auditHandler: AuditHandler = app.injector.instanceOf[AuditHandler]

  val detail: JsObject = JsObject(Seq("detail1" -> JsString("detailValue1")))

  val event: AuditEvent = AuditEvent(
    auditType = auditType,
    transactionName = transactionName,
    detail = detail
  )

  "AuditHandler" should {
    "audit with the correct audit event" in {

      val eventCapture: ArgumentCaptor[ExtendedDataEvent] = ArgumentCaptor.forClass(classOf[ExtendedDataEvent])
      when(mockAuditConnector.sendExtendedEvent(eventCapture.capture()))
        .thenReturn(Future.successful(AuditResult.Success))

      auditHandler.audit(event)

      val e = eventCapture.getAllValues.get(0)

      e.auditSource shouldBe appName
      e.auditType shouldBe auditType
      e.detail shouldBe detail

      e.tags("transactionName") shouldBe transactionName
      e.tags.get("clientIP") should not be empty
    }
  }
}
