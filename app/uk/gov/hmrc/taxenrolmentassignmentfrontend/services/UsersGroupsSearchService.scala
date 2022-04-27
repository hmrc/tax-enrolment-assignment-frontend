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

package uk.gov.hmrc.taxenrolmentassignmentfrontend.services

import cats.data.EitherT
import com.google.inject.{Inject, Singleton}
import play.api.mvc.AnyContent
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.service.TEAFResult
import uk.gov.hmrc.taxenrolmentassignmentfrontend.connectors.UsersGroupsSearchConnector
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.actions.RequestWithUserDetailsFromSession
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.AccountDetails
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.SessionKeys.accountDetailsForCredential
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.TEASessionCache

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class UsersGroupsSearchService @Inject()(
  usersGroupsSearchConnector: UsersGroupsSearchConnector,
  sessionCache: TEASessionCache
) {

  def getAccountDetails(credId: String)(
    implicit ec: ExecutionContext,
    hc: HeaderCarrier,
    request: RequestWithUserDetailsFromSession[AnyContent]
  ): TEAFResult[AccountDetails] = EitherT {
    val key = accountDetailsForCredential(credId)
    sessionCache.getEntry[AccountDetails](key).flatMap {
      case Some(entry) => Future.successful(Right(entry))
      case None =>
        getAccountDetailsFromUsersGroupSearch(credId, key).value
    }
  }

  private def getAccountDetailsFromUsersGroupSearch(credId: String,
                                                    key: String)(
    implicit ec: ExecutionContext,
    hc: HeaderCarrier,
    request: RequestWithUserDetailsFromSession[AnyContent]
  ): TEAFResult[AccountDetails] = EitherT {
    usersGroupsSearchConnector
      .getUserDetails(credId)
      .value
      .map {
        case Right(userDetails) =>
          val accountDetails = new AccountDetails(userDetails)
          sessionCache.save[AccountDetails](key, accountDetails)
          Right(accountDetails)
        case Left(error) => Left(error)
      }
  }
}
