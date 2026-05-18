/*
 * Copyright 2025 HM Revenue & Customs
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

package uk.gov.hmrc.taxenrolmentassignmentfrontend.filters

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.*
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AsyncWordSpec
import org.scalatestplus.mockito.MockitoSugar
import play.api.http.Status.OK
import play.api.inject.guice.{GuiceApplicationBuilder, GuiceableModule}
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.Results.Ok
import play.api.mvc.{AnyContentAsEmpty, RequestHeader, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers.{contentAsJson, defaultAwaitTimeout, status}
import play.api.{Application, inject}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.connectors.ScaWrapperDataConnector
import uk.gov.hmrc.hmrcfrontend.config.ServiceNavigationCanBeControlledByRequestAttr.UseServiceNavigation

import scala.concurrent.Future

class DataAttributesFilterSpec extends AsyncWordSpec with Matchers with MockitoSugar with BeforeAndAfterEach {

  private val mockScaWrapperDataConnector = mock[ScaWrapperDataConnector]

  val modules: Seq[GuiceableModule] =
    Seq(inject.bind[ScaWrapperDataConnector].toInstance(mockScaWrapperDataConnector))

  val application: Application = new GuiceApplicationBuilder()
    .configure(conf = "auditing.enabled" -> false, "metrics.enabled" -> false, "metrics.jvm" -> false)
    .overrides(modules: _*)
    .build()

  override def beforeEach(): Unit = {
    reset(mockScaWrapperDataConnector)

    when(mockScaWrapperDataConnector.serviceNavigationToggle()(any(), any()))
      .thenReturn(Future.successful(true))
  }

  val dataAttributesFilter: DataAttributesFilter =
    application.injector.instanceOf[DataAttributesFilter]

  "dataAttributesFilter" must {

    "attach useNewServiceNavigation when request is authenticated toggle is returned as true" in {
      implicit val request: FakeRequest[AnyContentAsEmpty.type] =
        FakeRequest("GET", "/not-excluded").withSession("authToken" -> "valid-token")

      val f: RequestHeader => Future[Result] =
        r =>
          Future.successful(
            Ok(
              Json.obj(
                "useNewServiceNavigation" -> r.attrs.get(UseServiceNavigation)
              )
            )
          )

      val result = dataAttributesFilter.apply(f)(request)

      status(result) mustBe OK

      verify(mockScaWrapperDataConnector, times(1)).serviceNavigationToggle()(any(), any())

      contentAsJson(result) mustBe Json.obj(
        "useNewServiceNavigation" -> Some(true)
      )
    }

    Seq("/assets", "/ping/ping").foreach { path =>
      s"not attach wrapperData when request is authenticated but path is excluded: $path" in {
        implicit val request: FakeRequest[AnyContentAsEmpty.type] =
          FakeRequest("GET", path).withSession("authToken" -> "valid-token")

        val f: RequestHeader => Future[Result] =
          r => Future.successful(Ok(Json.obj("useNewServiceNavigation" -> r.attrs.get(UseServiceNavigation))))

        val result = dataAttributesFilter.apply(f)(request)

        status(result) mustBe OK

        verify(mockScaWrapperDataConnector, never()).serviceNavigationToggle()(any(), any())

        contentAsJson(result) mustBe Json.obj(
          "useNewServiceNavigation" -> Json.toJson(false)
        )
      }
    }

    "not attach wrapperData when request is unauthenticated" in {
      implicit val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest("GET", "/not-excluded")

      val f: RequestHeader => Future[Result] =
        r => Future.successful(Ok(Json.obj("useNewServiceNavigation" -> r.attrs.get(UseServiceNavigation))))

      val result = dataAttributesFilter.apply(f)(request)

      status(result) mustBe OK

      verify(mockScaWrapperDataConnector, never()).serviceNavigationToggle()(any(), any())

      contentAsJson(result) mustBe Json.obj(
        "useNewServiceNavigation" -> Json.toJson(false)
      )
    }

  }
}
