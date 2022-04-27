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

package uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.filters

import play.api.Logger
import play.api.mvc.{ActionFilter, Result}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.config.AppConfig
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.actions.RequestWithUserDetailsFromSession

import scala.concurrent.{ExecutionContext, Future}

trait ThrottleFilter {

  implicit val baseLogger: Logger = Logger(this.getClass.getName)
  val appConfig: AppConfig

  def throttleUsers()(implicit ec: ExecutionContext) = new ActionFilter[RequestWithUserDetailsFromSession] {
    override protected val executionContext: ExecutionContext = ec

    def filter[A](input: RequestWithUserDetailsFromSession[A]): Future[Option[Result]] = Future.successful {
      None
      }
    }
  }
