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
import uk.gov.hmrc.taxenrolmentassignmentfrontend.connectors.EACDConnector
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.UnexpectedResponseFromEACD
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.BaseSpec
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.TestData._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.UserAnswers
import uk.gov.hmrc.taxenrolmentassignmentfrontend.repository.JourneyCacheRepository

import scala.concurrent.Future

class EACDServiceSpec extends BaseSpec {

  lazy val mockEacdConnector: EACDConnector = mock[EACDConnector]
  override val mockJourneyCacheRepository: JourneyCacheRepository = mock[JourneyCacheRepository]

  val service = new EACDService(mockEacdConnector, mockJourneyCacheRepository)

  "getUsersAssignedPTEnrolment" when {
    "the a PT enrolment has been assigned for the nino" should {
      "call the EACD, save to cache and return the account details" in {

        when(mockEacdConnector.getUsersWithPTEnrolment(NINO))
          .thenReturn(createInboundResult(UsersAssignedEnrolment1))

        when(mockEacdConnector.getUsersWithPTEnrolment(NINO))
          .thenReturn(createInboundResult(UsersAssignedEnrolment1))

        when(mockJourneyCacheRepository.set(any[UserAnswers])) thenReturn Future.successful(true)

        val result = service.getUsersAssignedPTEnrolment
        whenReady(result.value) { res =>
          res shouldBe Right(UsersAssignedEnrolment1)
        }
      }
    }

    "EACD returns an error" should {
      "return an error" in {
        when(mockEacdConnector.getUsersWithPTEnrolment(NINO))
          .thenReturn(createInboundResultError(UnexpectedResponseFromEACD))

        val result = service.getUsersAssignedPTEnrolment
        whenReady(result.value) { res =>
          res shouldBe Left(UnexpectedResponseFromEACD)
        }
      }
    }
  }

  "getUsersAssignedSAEnrolment" when {

    "the there is a user assigned SA enrolment" should {
      "call the EACD twice to get the UTR and then enrolled credentials, save to cache and return the credentialId" in {

        when(mockEacdConnector.queryKnownFactsByNinoVerifier(NINO))
          .thenReturn(createInboundResult(Some(UTR)))

        when(mockEacdConnector.getUsersWithSAEnrolment(UTR))
          .thenReturn(createInboundResult(UsersAssignedEnrolment1))

        when(mockJourneyCacheRepository.set(any[UserAnswers])) thenReturn Future.successful(true)

        val result = service.getUsersAssignedSAEnrolment
        whenReady(result.value) { res =>
          res shouldBe Right(UsersAssignedEnrolment1)
        }
      }
    }

    "the user has no SA enrolments associated with their nino" should {
      "call EACD to get the UTR, then save no creds with enrolment to cache and return None" in {

        when(mockEacdConnector.queryKnownFactsByNinoVerifier(NINO))
          .thenReturn(createInboundResult(None))

        when(mockJourneyCacheRepository.set(any[UserAnswers])) thenReturn Future.successful(true)

        val result = service.getUsersAssignedSAEnrolment
        whenReady(result.value) { res =>
          res shouldBe Right(UsersAssignedEnrolmentEmpty)
        }
      }
    }

    "EACD returns an error" should {
      "return an error" in {

        when(mockEacdConnector.queryKnownFactsByNinoVerifier(NINO))
          .thenReturn(createInboundResultError(UnexpectedResponseFromEACD))

        val result = service.getUsersAssignedSAEnrolment
        whenReady(result.value) { res =>
          res shouldBe Left(UnexpectedResponseFromEACD)
        }
      }
    }
  }
}
