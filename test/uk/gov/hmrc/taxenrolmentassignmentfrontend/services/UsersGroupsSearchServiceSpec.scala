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

import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import play.api.Application
import play.api.inject.{Binding, bind}
import play.api.libs.json.Json
import uk.gov.hmrc.taxenrolmentassignmentfrontend.connectors.UsersGroupsSearchConnector
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.UnexpectedResponseFromUsersGroupsSearch
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.BaseSpec
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.TestData._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.{AccountDetails, CacheMap}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.TEASessionCache

import scala.concurrent.Future

class UsersGroupsSearchServiceSpec extends BaseSpec {

  lazy val mockUsersGroupsSearchConnector: UsersGroupsSearchConnector = mock[UsersGroupsSearchConnector]
  lazy val mockTeaSessionCache: TEASessionCache                       = mock[TEASessionCache]

  override lazy val overrides: Seq[Binding[TEASessionCache]] = Seq(
    bind[TEASessionCache].toInstance(mockTeaSessionCache)
  )

  override implicit lazy val app: Application = localGuiceApplicationBuilder()
    .overrides(
      bind[UsersGroupsSearchConnector].toInstance(mockUsersGroupsSearchConnector)
    )
    .build()

  lazy val service: UsersGroupsSearchService = app.injector.instanceOf[UsersGroupsSearchService]

  "getAccountDetails" when {
    "the account details are already in the cache"     should {
      "not call the users-groups-search and return value from cache" in { // TODO
        val additionCacheData = Map(
          s"AccountDetailsFor$CREDENTIAL_ID" -> Json.toJson(accountDetails)(AccountDetails.mongoFormats(crypto.crypto))
        )
        val result            =
          service.getAccountDetails(CREDENTIAL_ID, accountDetailsFromMongo(additionalCacheData = additionCacheData))(
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

        when(mockUsersGroupsSearchConnector.getUserDetails(CREDENTIAL_ID))
          .thenReturn(createInboundResult(usersGroupSearchResponse))

        when(
          mockTeaSessionCache
            .save(
              ArgumentMatchers.eq(s"AccountDetailsFor$CREDENTIAL_ID"),
              ArgumentMatchers.eq(accountDetails.copy(userId = "********6037"))
            )(any(), any())
        )
          .thenReturn(Future(CacheMap(request.sessionID, Map())))

        val result = service.getAccountDetails(CREDENTIAL_ID, accountDetailsFromMongo())(
          implicitly,
          implicitly,
          requestWithAccountType()
        )
        whenReady(result.value) { res =>
          res shouldBe Right(accountDetails.copy(userId = "********6037"))
        }
      }
    }

    "the account details are not already in the cache and users-group-search returns an error" should {
      "return an error" in {
        when(mockUsersGroupsSearchConnector.getUserDetails(CREDENTIAL_ID))
          .thenReturn(createInboundResultError(UnexpectedResponseFromUsersGroupsSearch))

        val result = service.getAccountDetails(CREDENTIAL_ID, accountDetailsFromMongo())(
          implicitly,
          implicitly,
          requestWithAccountType()
        )
        whenReady(result.value) { res =>
          res shouldBe Left(UnexpectedResponseFromUsersGroupsSearch)
        }
      }
    }
  }

  "getAllCredIdsByNino" when {
    "a list of creds is successfully retrieved" should {
      "call the users-groups-search return the account details" in {
        val nino = generateNino.nino

        when(mockUsersGroupsSearchConnector.getAllCredIdsByNino(nino))
          .thenReturn(createInboundResult(userGroupSearchCredIds))

        val result = service.getAllCredIdsByNino(nino)(
          implicitly,
          implicitly
        )
        whenReady(result.value) { res =>
          res shouldBe Right(userGroupSearchCredIds)
        }

      }
    }
    "users-group-search returns an error"       should {
      "return an error" in {
        val nino = generateNino.nino

        when(mockUsersGroupsSearchConnector.getAllCredIdsByNino(nino))
          .thenReturn(createInboundResultError(UnexpectedResponseFromUsersGroupsSearch))

        val result = service.getAllCredIdsByNino(nino)(
          implicitly,
          implicitly
        )
        whenReady(result.value) { res =>
          res shouldBe Left(UnexpectedResponseFromUsersGroupsSearch)
        }

      }
    }
  }
}
