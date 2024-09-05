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

package uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers

import org.mockito.ArgumentMatchers.{any, anyString}
import org.mockito.MockitoSugar.{mock, times, verify, when}
import org.mockito.stubbing.ScalaOngoingStubbing
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.TestData._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.UserAnswers
import uk.gov.hmrc.taxenrolmentassignmentfrontend.pages.AccountTypePage
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.JourneyCacheRepository

import scala.concurrent.Future

trait ControllersBaseSpec extends BaseSpec {

  lazy val mockAuthConnector: AuthConnector = mock[AuthConnector]
  override val mockJourneyCacheRepository: JourneyCacheRepository = mock[JourneyCacheRepository]

  def mockGetDataFromCacheForActionNoRedirectUrl: ScalaOngoingStubbing[Future[Boolean]] = {
    val mockUserAnswers = UserAnswers("id", generateNino.nino)
      .setOrException(AccountTypePage, randomAccountType.toString)

    when(mockJourneyCacheRepository.get(any(), any()))
      .thenReturn(Future.successful(Some(mockUserAnswers)))
    when(mockJourneyCacheRepository.set(any[UserAnswers])) thenReturn Future.successful(true)
  }

  def mockGetDataFromCacheForActionSuccess(
    userAnswers: UserAnswers
  ): ScalaOngoingStubbing[Future[Boolean]] = {
    when(mockJourneyCacheRepository.get(any(), any()))
      .thenReturn(Future.successful(Some(userAnswers)))
    when(mockJourneyCacheRepository.set(any[UserAnswers])) thenReturn Future.successful(true)
  }

  def mockDeleteDataFromCacheWhen: ScalaOngoingStubbing[Future[Boolean]] =
    when(mockJourneyCacheRepository.clear(anyString(), anyString()))
      .thenReturn(Future.successful(true))

  def mockDeleteDataFromCacheVerify: Future[Boolean] =
    verify(mockJourneyCacheRepository, times(1)).clear(anyString(), anyString())
}
