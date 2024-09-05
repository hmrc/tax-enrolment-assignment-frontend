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

import org.mockito.ArgumentMatchers.any
import org.mockito.MockitoSugar.{mock, when}
import play.api.Application
import play.api.inject.{Binding, bind}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.taxenrolmentassignmentfrontend.connectors.UsersGroupsSearchConnector
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.UnexpectedResponseFromUsersGroupsSearch
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.BaseSpec
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.TestData._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.{AccountDetails, UserAnswers}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.pages.AccountDetailsForCredentialPage
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.JourneyCacheRepository

import scala.concurrent.Future

class UsersGroupsSearchServiceSpec extends BaseSpec {

  lazy val mockUsersGroupsSearchConnector: UsersGroupsSearchConnector = mock[UsersGroupsSearchConnector]
  override val mockJourneyCacheRepository: JourneyCacheRepository = mock[JourneyCacheRepository]
  val nino: Nino = generateNino

  override lazy val overrides: Seq[Binding[JourneyCacheRepository]] = Seq(
    bind[JourneyCacheRepository].toInstance(mockJourneyCacheRepository)
  )

  override implicit lazy val app: Application = localGuiceApplicationBuilder()
    .overrides(
      bind[UsersGroupsSearchConnector].toInstance(mockUsersGroupsSearchConnector)
    )
    .build()

  lazy val service: UsersGroupsSearchService = app.injector.instanceOf[UsersGroupsSearchService]

  "getAccountDetails" when {
    "the account details are already in the cache" should {
      "not call the users-groups-search and return value from cache" in {

        val mockUserAnswers: UserAnswers = UserAnswers("FAKE_SESSION_ID", nino.nino)
          .setOrException(AccountDetailsForCredentialPage(CREDENTIAL_ID), accountDetails)(
            AccountDetails.mongoFormats(crypto.crypto)
          )

        when(mockJourneyCacheRepository.get(any(), any()))
          .thenReturn(Future.successful(Some(mockUserAnswers)))

        val result = service.getAccountDetails(CREDENTIAL_ID)(
          implicitly,
          implicitly,
          requestWithGivenMongoDataAndUserAnswers(requestWithAccountType(), mockUserAnswers)
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

        when(mockJourneyCacheRepository.set(any[UserAnswers])) thenReturn Future.successful(true)

        val result = service.getAccountDetails(CREDENTIAL_ID)(
          implicitly,
          implicitly,
          requestWithGivenMongoData(requestWithAccountType())
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

        val result = service.getAccountDetails(CREDENTIAL_ID)(
          implicitly,
          implicitly,
          requestWithGivenMongoData(requestWithAccountType())
        )
        whenReady(result.value) { res =>
          res shouldBe Left(UnexpectedResponseFromUsersGroupsSearch)
        }
      }
    }
  }
}
