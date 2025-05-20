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

package uk.gov.hmrc.taxenrolmentassignmentfrontend.utils

import cats.data.EitherT
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.{any, anyString}
import org.mockito.Mockito.{times, verify, when}
import play.api.http.Status.{INTERNAL_SERVER_ERROR, OK}
import uk.gov.hmrc.auth.core.{Enrolment, EnrolmentIdentifier, Enrolments}
import uk.gov.hmrc.domain.Generator
import uk.gov.hmrc.http.{HttpResponse, UpstreamErrorResponse}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.connectors.TaxEnrolmentsConnector
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.BaseSpec
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.TestData.*
import uk.gov.hmrc.taxenrolmentassignmentfrontend.services.EACDService

import scala.concurrent.Future

class HmrcPTEnrolmentSpec extends BaseSpec {

  lazy val mockTaxEnrolmentsConnector: TaxEnrolmentsConnector = mock[TaxEnrolmentsConnector]
  lazy val mockEacdService: EACDService = mock[EACDService]

  val service = new HmrcPTEnrolment(mockTaxEnrolmentsConnector, mockEacdService)

  "findAndDeleteWrongPTEnrolment" when {
    "There is no enrolment" should {
      "not call eacdService" in {
        when(mockTaxEnrolmentsConnector.deallocateEnrolment(anyString(), any())(any(), any()))
          .thenReturn(EitherT.rightT[Future, UpstreamErrorResponse](HttpResponse(OK, "")))

        val result = service.findAndDeleteWrongPTEnrolment(NINO, Enrolments(Set.empty: Set[Enrolment]), "groupId")
        whenReady(result.value) { res =>
          res shouldBe Right(())
        }
        verify(mockTaxEnrolmentsConnector, times(0)).deallocateEnrolment(anyString(), any())(any(), any())
      }
    }

    "There is no HMRC-PT enrolment" should {
      "not call eacdService" in {
        val enrolments = Set(Enrolment("key", Seq(EnrolmentIdentifier("key", "value")), "activated"))

        when(mockTaxEnrolmentsConnector.deallocateEnrolment(anyString(), any())(any(), any()))
          .thenReturn(EitherT.rightT[Future, UpstreamErrorResponse](HttpResponse(OK, "")))

        val result = service.findAndDeleteWrongPTEnrolment(NINO, Enrolments(enrolments), "groupId")
        whenReady(result.value) { res =>
          res shouldBe Right(())
        }
        verify(mockTaxEnrolmentsConnector, times(0)).deallocateEnrolment(anyString(), any())(any(), any())
      }
    }

    "There is a valid HMRC-PT enrolment" should {
      "not call eacdService" in {
        val enrolments = Set(Enrolment("HMRC-PT", Seq(EnrolmentIdentifier("NINO", NINO.nino)), "activated"))

        when(mockTaxEnrolmentsConnector.deallocateEnrolment(anyString(), any())(any(), any()))
          .thenReturn(EitherT.rightT[Future, UpstreamErrorResponse](HttpResponse(OK, "")))

        val result = service.findAndDeleteWrongPTEnrolment(NINO, Enrolments(enrolments), "groupId")
        whenReady(result.value) { res =>
          res shouldBe Right(())
        }

        verify(mockTaxEnrolmentsConnector, times(0)).deallocateEnrolment(anyString(), any())(any(), any())
      }
    }

    "There is an invalid HMRC-PT enrolment" should {
      "call eacdService" in {
        val enrolments = Set(Enrolment("HMRC-PT", Seq(EnrolmentIdentifier("NINO", secondNino.nino)), "activated"))

        when(
          mockTaxEnrolmentsConnector
            .deallocateEnrolment(anyString(), ArgumentMatchers.eq(s"HMRC-PT~NINO~${secondNino.nino}"))(any(), any())
        )
          .thenReturn(EitherT.rightT[Future, UpstreamErrorResponse](HttpResponse(OK, "")))

        val result = service.findAndDeleteWrongPTEnrolment(NINO, Enrolments(enrolments), "groupId")
        whenReady(result.value) { res =>
          res shouldBe Right(())
        }
        verify(mockTaxEnrolmentsConnector, times(1))
          .deallocateEnrolment(anyString(), ArgumentMatchers.eq(s"HMRC-PT~NINO~${secondNino.nino}"))(any(), any())
      }
    }

    "There is several HMRC-PT enrolments" should {
      "call delete for the invalid enrolments only" in {
        val thirdNino = new Generator().nextNino
        val enrolments = Set(
          Enrolment("HMRC-PT", Seq(EnrolmentIdentifier("NINO", secondNino.nino)), "activated"),
          Enrolment("HMRC-PT", Seq(EnrolmentIdentifier("NINO", thirdNino.nino)), "activated"),
          Enrolment("HMRC-PT", Seq(EnrolmentIdentifier("NINO", NINO.nino)), "activated"),
          Enrolment("FAKE", Seq(EnrolmentIdentifier("NINO", secondNino.nino)), "activated"),
          Enrolment("IR-SA", Seq(EnrolmentIdentifier("UTR", "000000")), "activated")
        )

        when(
          mockTaxEnrolmentsConnector
            .deallocateEnrolment(anyString(), ArgumentMatchers.eq(s"HMRC-PT~NINO~${secondNino.nino}"))(any(), any())
        )
          .thenReturn(EitherT.rightT[Future, UpstreamErrorResponse](HttpResponse(OK, "")))

        when(
          mockTaxEnrolmentsConnector
            .deallocateEnrolment(anyString(), ArgumentMatchers.eq(s"HMRC-PT~NINO~${thirdNino.nino}"))(any(), any())
        )
          .thenReturn(EitherT.rightT[Future, UpstreamErrorResponse](HttpResponse(OK, "")))

        when(
          mockTaxEnrolmentsConnector
            .deallocateEnrolment(anyString(), ArgumentMatchers.eq(s"HMRC-PT~NINO~${NINO.nino}"))(any(), any())
        )
          .thenReturn(EitherT.rightT[Future, UpstreamErrorResponse](HttpResponse(OK, "")))

        when(
          mockTaxEnrolmentsConnector
            .deallocateEnrolment(anyString(), ArgumentMatchers.eq(s"FAKE~NINO~${secondNino.nino}"))(any(), any())
        )
          .thenReturn(EitherT.rightT[Future, UpstreamErrorResponse](HttpResponse(OK, "")))

        when(
          mockTaxEnrolmentsConnector
            .deallocateEnrolment(anyString(), ArgumentMatchers.eq(s"IR-SA~UTR~000000"))(any(), any())
        )
          .thenReturn(EitherT.rightT[Future, UpstreamErrorResponse](HttpResponse(OK, "")))

        val result = service.findAndDeleteWrongPTEnrolment(NINO, Enrolments(enrolments), "groupId")
        whenReady(result.value) { res =>
          res shouldBe Right(())
        }
        verify(mockTaxEnrolmentsConnector, times(1))
          .deallocateEnrolment(anyString(), ArgumentMatchers.eq(s"HMRC-PT~NINO~${secondNino.nino}"))(any(), any())
        verify(mockTaxEnrolmentsConnector, times(1))
          .deallocateEnrolment(anyString(), ArgumentMatchers.eq(s"HMRC-PT~NINO~${thirdNino.nino}"))(any(), any())
        verify(mockTaxEnrolmentsConnector, times(0))
          .deallocateEnrolment(anyString(), ArgumentMatchers.eq(s"HMRC-PT~NINO~${NINO.nino}"))(any(), any())
        verify(mockTaxEnrolmentsConnector, times(0))
          .deallocateEnrolment(anyString(), ArgumentMatchers.eq(s"FAKE~NINO~${secondNino.nino}"))(any(), any())
        verify(mockTaxEnrolmentsConnector, times(0))
          .deallocateEnrolment(anyString(), ArgumentMatchers.eq(s"IR-SA~UTR~000000"))(any(), any())
      }
    }

    "There is a server error" should {
      "return Left" in {
        val enrolments = Set(Enrolment("HMRC-PT", Seq(EnrolmentIdentifier("NINO", secondNino.nino)), "activated"))

        when(
          mockTaxEnrolmentsConnector
            .deallocateEnrolment(anyString(), ArgumentMatchers.eq(s"HMRC-PT~NINO~${secondNino.nino}"))(any(), any())
        )
          .thenReturn(EitherT.leftT[Future, HttpResponse](UpstreamErrorResponse("error", INTERNAL_SERVER_ERROR)))

        val result = service.findAndDeleteWrongPTEnrolment(NINO, Enrolments(enrolments), "groupId")
        whenReady(result.value) { res =>
          res shouldBe a[Left[UpstreamErrorResponse, _]]
        }
        verify(mockTaxEnrolmentsConnector, times(1))
          .deallocateEnrolment(anyString(), ArgumentMatchers.eq(s"HMRC-PT~NINO~${secondNino.nino}"))(any(), any())
      }
    }
  }
}
