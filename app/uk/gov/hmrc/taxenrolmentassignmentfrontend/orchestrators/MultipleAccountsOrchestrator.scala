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

package uk.gov.hmrc.taxenrolmentassignmentfrontend.orchestrators

import cats.data.EitherT
import com.google.inject.{Inject, Singleton}
import play.api.mvc.AnyContent
import uk.gov.hmrc.http.HeaderCarrier
import cats.implicits._
import uk.gov.hmrc.service.TEAFResult
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes.MULTIPLE_ACCOUNTS
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.auth.RequestWithUserDetails
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.InvalidUserType
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.AccountDetails
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.SessionKeys.{
  ACCOUNT_TYPE,
  REDIRECT_URL
}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.TEASessionCache
import uk.gov.hmrc.taxenrolmentassignmentfrontend.services.UsersGroupSearchService

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class MultipleAccountsOrchestrator @Inject()(
  sessionCache: TEASessionCache,
  usersGroupSearchService: UsersGroupSearchService
) {

  def getDetailsForLandingPage(
    implicit requestWithUserDetails: RequestWithUserDetails[AnyContent],
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): TEAFResult[AccountDetails] = {
    for {
      _ <- checkAccountTypeAndGetRedirectUrlFromCache
      accountDetails <- usersGroupSearchService.getAccountDetails(
        requestWithUserDetails.userDetails.credId
      )
    } yield accountDetails

  }

  private def checkAccountTypeAndGetRedirectUrlFromCache(
    implicit requestWithUserDetails: RequestWithUserDetails[AnyContent],
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): TEAFResult[Unit] = EitherT {
    val res = for {
      accountType <- sessionCache.getEntry[AccountTypes.Value](ACCOUNT_TYPE)
      redirectUrl <- sessionCache.getEntry[String](REDIRECT_URL)
    } yield (accountType, redirectUrl)

    res.map {
      case (Some(MULTIPLE_ACCOUNTS), Some(redirectUrl)) => Right((): Unit)
      case (_, optRedirectUrl)                          => Left(InvalidUserType(optRedirectUrl))
    }
  }
}
