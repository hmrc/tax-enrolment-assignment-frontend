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

import play.api.mvc.*

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
class AuthJourney @Inject() (
  authAction: AuthAction,
  accountMongoDetailsAction: AccountMongoDetailsAction,
  val parser: BodyParsers.Default
) {
  val authJourney: ActionBuilder[RequestWithUserDetailsFromSession, AnyContent] = authAction

  def accountDetailsFromMongo(requestWithUserDetailsFromSession: RequestWithUserDetailsFromSession[AnyContent])(implicit
    ec: ExecutionContext
  ): Future[Either[Result, RequestWithUserDetailsFromSessionAndMongo[AnyContent]]] =
    accountMongoDetailsAction.accountDetailsFromMongo(requestWithUserDetailsFromSession)
}
