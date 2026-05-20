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

package uk.gov.hmrc.taxenrolmentassignmentfrontend.connectors

import com.google.inject.Inject
import play.api.Logging
import play.api.libs.json.JsValue
import uk.gov.hmrc.http.HttpReads.Implicits.readFromJson
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, StringContextOps, UpstreamErrorResponse}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.config.AppConfig

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

class ScaWrapperDataConnector @Inject() (
  http: HttpClientV2,
  appConfig: AppConfig
) extends Logging {

  def serviceNavigationToggle()(implicit
    ec: ExecutionContext,
    hc: HeaderCarrier
  ): Future[Boolean] = {

    val url = url"${appConfig.scaWrapperDataUrl}/service-navigation/toggle"

    logger.debug(
      s"[SCA Wrapper Library][ScaWrapperDataConnector][serviceNavigationToggle] Requesting service-nav toggle"
    )

    http
      .get(url)
      .execute[JsValue]
      .map(json =>
        Try((json \ "useNewServiceNavigation").as[Boolean]) match {
          case Success(toggle) => toggle
          case Failure(error)  =>
            logger.error(error.getMessage, error)
            false
        }
      )
      .recover {
        case ex @ UpstreamErrorResponse(_, statusCode, _, _) if statusCode < 499 =>
          logger.error(ex.message, ex)
          false
        case ex @ UpstreamErrorResponse(_, _, _, _)                              =>
          logger.error(ex.message)
          false
        case NonFatal(_)                                                         => false
      }
  }
}
