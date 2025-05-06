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

package uk.gov.hmrc.taxenrolmentassignmentfrontend.services

import cats.data.EitherT

import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.crypto.Sensitive.SensitiveString
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.service.TEAFResult
import uk.gov.hmrc.taxenrolmentassignmentfrontend.connectors.UsersGroupsSearchConnector
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.actions.RequestWithUserDetailsFromSessionAndMongo
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.{AccountDetails, IdentityProviderWithCredId}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.SessionKeys.{LIST_OF_CRED_IDS, accountDetailsForCredential}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.TEASessionCache

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class UsersGroupsSearchService @Inject() (
  usersGroupsSearchConnector: UsersGroupsSearchConnector,
  sessionCache: TEASessionCache
)(implicit crypto: TENCrypto) {

  def getAccountDetails(credId: String)(implicit
    ec: ExecutionContext,
    hc: HeaderCarrier,
    request: RequestWithUserDetailsFromSessionAndMongo[_]
  ): TEAFResult[AccountDetails] = EitherT {
    request.accountDetailsFromMongo.optAccountDetails(credId) match {
      case Some(entry) =>
        Future.successful(Right(entry))
      case None =>
        getAccountDetailsFromUsersGroupSearch(credId, accountDetailsForCredential(credId)).value
    }
  }

  private def getAccountDetailsFromUsersGroupSearch(credId: String, key: String)(implicit
    ec: ExecutionContext,
    hc: HeaderCarrier,
    request: RequestWithUserDetailsFromSessionAndMongo[_]
  ): TEAFResult[AccountDetails] = EitherT {
    usersGroupsSearchConnector
      .getUserDetails(credId)
      .value
      .flatMap {
        case Right(userDetails) =>
          val accountDetails: AccountDetails = AccountDetails(
            userDetails.identityProviderType,
            credId,
            userDetails.obfuscatedUserId.getOrElse(""),
            userDetails.email.map(SensitiveString),
            userDetails.lastAccessedTimestamp,
            AccountDetails.additionalFactorsToMFADetails(userDetails.additionalFactors),
            None
          )
          sessionCache
            .save[AccountDetails](key, accountDetails)(request, AccountDetails.mongoFormats(crypto.crypto))
            .map(_ => Right(accountDetails))
        case Left(error) => Future.successful(Left(error))
      }
  }

  def getAllCredIdsByNino(nino: String)(implicit
    ec: ExecutionContext,
    hc: HeaderCarrier,
    request: RequestWithUserDetailsFromSessionAndMongo[_]
  ): TEAFResult[Seq[IdentityProviderWithCredId]] = EitherT {
    request.accountDetailsFromMongo.listOfCredIds match {
      case Some(list) => Future.successful(Right(list))
      case None =>
        usersGroupsSearchConnector.getAllCredIdsByNino(nino).value.flatMap {
          case Right(list) =>
            sessionCache
              .save[Seq[IdentityProviderWithCredId]](LIST_OF_CRED_IDS, list)(
                request,
                IdentityProviderWithCredId.formatList
              )
              .map(_ => Right(list))
          case Left(error) => Future.successful(Left(error))
        }
    }
  }
}
