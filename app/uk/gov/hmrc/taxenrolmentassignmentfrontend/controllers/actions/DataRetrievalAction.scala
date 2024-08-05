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

package uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.actions

import play.api.mvc.ActionTransformer
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.UserAnswers
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.JourneyCacheRepository

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class DataRetrievalActionImpl @Inject() (
  val journeyCacheRepository: JourneyCacheRepository
)(implicit val executionContext: ExecutionContext)
    extends DataRetrievalAction {

  override protected def transform[A](request: IdentifierRequest[A]): Future[DataRequest[A]] =
    journeyCacheRepository
      .get(request.userId, request.request.userDetails.nino.nino)
      .map {
        _.fold(
          DataRequest(
            request.request,
            request.request.userDetails,
            UserAnswers(request.userId, request.request.userDetails.nino.nino),
            None
          )
        )(
          DataRequest(request.request, request.request.userDetails, _, None)
        )
      }
}

trait DataRetrievalAction extends ActionTransformer[IdentifierRequest, DataRequest]
