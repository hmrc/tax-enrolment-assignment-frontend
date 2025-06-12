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

package uk.gov.hmrc.taxenrolmentassignmentfrontend.services

import uk.gov.hmrc.taxenrolmentassignmentfrontend.config.AppConfig
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.actions.{AccountDetailsFromMongo, RequestWithUserDetailsFromSession}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.{CacheNotCompleteOrNotCorrect, TaxEnrolmentAssignmentErrors}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.TEASessionCache

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class AccountMongoDetailsRetrievalService @Inject() (
  teaSessionCache: TEASessionCache,
  val appConfig: AppConfig
)(implicit val executionContext: ExecutionContext, crypto: TENCrypto) {

  def getAccountDetailsFromMongoFromCache(implicit
    request: RequestWithUserDetailsFromSession[_]
  ): Future[Either[TaxEnrolmentAssignmentErrors, AccountDetailsFromMongo]] =
    teaSessionCache.fetch().map { optCachedMap =>
      optCachedMap
        .fold[Either[TaxEnrolmentAssignmentErrors, AccountDetailsFromMongo]](
          Left(CacheNotCompleteOrNotCorrect(None, None))
        ) { cachedMap =>
          (
            AccountDetailsFromMongo.optAccountType(cachedMap.data),
            AccountDetailsFromMongo.optRedirectUrl(cachedMap.data)
          ) match {
            case (Some(accountType), Some(redirectUrl)) =>
              Right(
                AccountDetailsFromMongo(accountType, redirectUrl, cachedMap.data)(crypto.crypto)
              )
            case (optAccountType, optRedirectUrl) =>
              Left(
                CacheNotCompleteOrNotCorrect(optRedirectUrl, optAccountType)
              )
          }
        }
    }

}
