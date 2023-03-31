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

import play.api.Application
import play.api.inject.bind
import play.api.libs.json.{Format, Json}
import play.api.mvc.AnyContent
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.taxenrolmentassignmentfrontend.connectors.UsersGroupsSearchConnector
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.actions.RequestWithUserDetailsFromSession
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.UnexpectedResponseFromUsersGroupsSearch
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.TestData._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.BaseSpec
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.AccountDetails
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.TEASessionCache

import scala.concurrent.{ExecutionContext, Future}

class UsersGroupsSearchServiceSpec extends BaseSpec {

  lazy val mockUsersGroupsSearchConnector = mock[UsersGroupsSearchConnector]
  lazy val mockTeaSessionCache = mock[TEASessionCache]

  override lazy val overrides = Seq(
    bind[TEASessionCache].toInstance(mockTeaSessionCache)
  )

  override implicit lazy val app: Application = localGuiceApplicationBuilder()
    .overrides(
      bind[UsersGroupsSearchConnector].toInstance(mockUsersGroupsSearchConnector)
    )
    .build()

  lazy val service = app.injector.instanceOf[UsersGroupsSearchService]

  "getAccountDetails" when {
    "the account details are already in the cache" should {
      "not call the users-groups-search and return value from cache" in {
        val additionCacheData = Map(
          s"AccountDetailsFor$CREDENTIAL_ID" -> Json.toJson(accountDetails)(AccountDetails.mongoFormats(crypto.crypto))
        )
        val result = service.getAccountDetails(CREDENTIAL_ID)(
          implicitly,
          implicitly,
          requestWithAccountType(additionalCacheData = additionCacheData)
        )
        whenReady(result.value) { res =>
          res shouldBe Right(accountDetails)
        }
      }
    }
    "the account details are not already in the cache" should {
      "call the users-groups-search, save to cache and return the account details" in {
        (mockUsersGroupsSearchConnector
          .getUserDetails(_: String)(_: ExecutionContext, _: HeaderCarrier))
          .expects(CREDENTIAL_ID, *, *)
          .returning(createInboundResult(usersGroupSearchResponse))
        (mockTeaSessionCache
          .save(_: String, _: AccountDetails)(
            _: RequestWithUserDetailsFromSession[AnyContent],
            _: Format[AccountDetails]
          ))
          .expects(s"AccountDetailsFor$CREDENTIAL_ID", accountDetails.copy(userId = "********6037"), *, *)
          .returning(Future(CacheMap(request.sessionID, Map())))
        val result = service.getAccountDetails(CREDENTIAL_ID)(implicitly, implicitly, requestWithAccountType())
        whenReady(result.value) { res =>
          res shouldBe Right(accountDetails.copy(userId = "********6037"))
        }
      }
    }

    "the account details are not already in the cache and users-group-search returns an error" should {
      "return an error" in {
        (mockUsersGroupsSearchConnector
          .getUserDetails(_: String)(_: ExecutionContext, _: HeaderCarrier))
          .expects(CREDENTIAL_ID, *, *)
          .returning(
            createInboundResultError(UnexpectedResponseFromUsersGroupsSearch)
          )
        val result = service.getAccountDetails(CREDENTIAL_ID)(implicitly, implicitly, requestWithAccountType())
        whenReady(result.value) { res =>
          res shouldBe Left(UnexpectedResponseFromUsersGroupsSearch)
        }
      }
    }
  }
}
