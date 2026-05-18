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

package uk.gov.hmrc.taxenrolmentassignmentfrontend.filters

import org.apache.pekko.stream.Materializer
import play.api.Logging
import play.api.mvc.{Filter, RequestHeader, Result}
import uk.gov.hmrc.hmrcfrontend.config.ServiceNavigationCanBeControlledByRequestAttr.UseServiceNavigation
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.http.HeaderCarrierConverter
import uk.gov.hmrc.taxenrolmentassignmentfrontend.connectors.ScaWrapperDataConnector

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class DataAttributesFilter @Inject() (
  scaWrapperDataConnector: ScaWrapperDataConnector
)(implicit
  val executionContext: ExecutionContext,
  val mat: Materializer
) extends Filter
    with Logging {

  private val excludedPaths: Seq[String] = Seq("/assets", "/ping/ping")

  private def checkIsAuthenticated(requestHeader: RequestHeader): Boolean =
    (requestHeader.session.get("authToken").isEmpty, excludedPaths.exists(requestHeader.path.contains(_))) match {
      case (_, true) => false
      case (true, _) =>
        logger.info(s"[SCA Wrapper Data Filter][Auth Token Empty]")
        false
      case _         => true
    }

  private def retrieveToggle(
    isAuthenticated: Boolean
  )(implicit headerCarrier: HeaderCarrier): Future[Boolean] =
    if (isAuthenticated) {
      scaWrapperDataConnector
        .serviceNavigationToggle()
    } else Future.successful(false)

  private def updateRequestHeader(
    requestHeader: RequestHeader,
    useNewServiceNavigation: Boolean
  ): RequestHeader =
    requestHeader
      .addAttr(UseServiceNavigation, useNewServiceNavigation)

  override def apply(f: RequestHeader => Future[Result])(rh: RequestHeader): Future[Result] = {
    implicit val headerCarrier: HeaderCarrier = HeaderCarrierConverter.fromRequestAndSession(rh, rh.session)

    val isAuthenticated = checkIsAuthenticated(rh)

    for {
      useNewServiceNav    <- retrieveToggle(isAuthenticated)
      updatedRequestHeader = updateRequestHeader(rh, useNewServiceNav)
      result              <- f(updatedRequestHeader)
    } yield result
  }
}
