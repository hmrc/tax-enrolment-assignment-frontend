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

import org.scalatest.concurrent.ScalaFutures
import play.api.libs.json.Format
import play.api.mvc.AnyContent
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.auth.RequestWithUserDetails
import uk.gov.hmrc.taxenrolmentassignmentfrontend.connectors.UsersGroupSearchConnector
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.TestData._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.UnexpectedResponseFromUsersGroupSearch
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.TestFixture
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.AccountDetails
import uk.gov.hmrc.taxenrolmentassignmentfrontend.services.UsersGroupSearchService

import scala.concurrent.{ExecutionContext, Future}

class UsersGroupSearchServiceSpec extends TestFixture with ScalaFutures {

  val mockUsersGroupSearchConnector = mock[UsersGroupSearchConnector]

  val service = new UsersGroupSearchService(
    mockUsersGroupSearchConnector,
    mockTeaSessionCache
  )

  "getAccountDetails" when {
    "the account details are already in the cache" should {
      "not call the users-groups-search and return value from cache" in {
        (mockTeaSessionCache
          .getEntry(_: String)(
            _: RequestWithUserDetails[AnyContent],
            _: Format[AccountDetails]
          ))
          .expects(s"AccountDetailsFor$CREDENTIAL_ID", *, *)
          .returning(Future.successful(Some(accountDetails)))
        val result = service.getAccountDetails(CREDENTIAL_ID)
        whenReady(result.value) { res =>
          res shouldBe Right(accountDetails)
        }
      }
    }
    "the account details are not already in the cache" should {
      "call the users-groups-search, save to cache and return the account details" in {
        (mockTeaSessionCache
          .getEntry(_: String)(
            _: RequestWithUserDetails[AnyContent],
            _: Format[AccountDetails]
          ))
          .expects(s"AccountDetailsFor$CREDENTIAL_ID", *, *)
          .returning(Future.successful(None))
        (mockUsersGroupSearchConnector
          .getUserDetails(_: String)(_: ExecutionContext, _: HeaderCarrier))
          .expects(CREDENTIAL_ID, *, *)
          .returning(createInboundResult(usersGroupSearchResponse))
        (mockTeaSessionCache
          .save(_: String, _: AccountDetails)(
            _: RequestWithUserDetails[AnyContent],
            _: Format[AccountDetails]
          ))
          .expects(s"AccountDetailsFor$CREDENTIAL_ID", accountDetails, *, *)
          .returning(Future(CacheMap(request.sessionID, Map())))
        val result = service.getAccountDetails(CREDENTIAL_ID)
        whenReady(result.value) { res =>
          res shouldBe Right(accountDetails)
        }
      }
    }

    "the account details are not already in the cache and users-group-search returns an error" should {
      "return an error" in {
        (mockTeaSessionCache
          .getEntry(_: String)(
            _: RequestWithUserDetails[AnyContent],
            _: Format[AccountDetails]
          ))
          .expects(s"AccountDetailsFor$CREDENTIAL_ID", *, *)
          .returning(Future.successful(None))
        (mockUsersGroupSearchConnector
          .getUserDetails(_: String)(_: ExecutionContext, _: HeaderCarrier))
          .expects(CREDENTIAL_ID, *, *)
          .returning(
            createInboundResultError(UnexpectedResponseFromUsersGroupSearch)
          )
        val result = service.getAccountDetails(CREDENTIAL_ID)
        whenReady(result.value) { res =>
          res shouldBe Left(UnexpectedResponseFromUsersGroupSearch)
        }
      }
    }
  }
}
