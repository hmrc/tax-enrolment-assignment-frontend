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
import play.api.http.Status.{INTERNAL_SERVER_ERROR, OK}
import uk.gov.hmrc.auth.core.{Enrolment, EnrolmentIdentifier, Enrolments}
import uk.gov.hmrc.domain.Generator
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, UpstreamErrorResponse}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.connectors.TaxEnrolmentsConnector
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.BaseSpec
import uk.gov.hmrc.taxenrolmentassignmentfrontend.helpers.TestData._

import scala.concurrent.ExecutionContext

class HmrcPTEnrolmentSpec extends BaseSpec {

  lazy val mockTaxEnrolmentsConnector: TaxEnrolmentsConnector = mock[TaxEnrolmentsConnector]

  val service = new HmrcPTEnrolment(mockTaxEnrolmentsConnector)

  "findAndDeleteWrongPTEnrolment" when {
    "There is no enrolment" should {
      "not call eacdService" in {
        (mockTaxEnrolmentsConnector
          .deallocateEnrolment(_: String, _: String)(
            _: HeaderCarrier,
            _: ExecutionContext
          ))
          .expects(*, *, *, *)
          .returning(EitherT.rightT(HttpResponse(OK, "")))
          .never()

        val result = service.findAndDeleteWrongPTEnrolment(NINO, Enrolments(Set.empty: Set[Enrolment]), "groupId")
        whenReady(result.value) { res =>
          res shouldBe Right(())
        }
      }
    }

    "There is no HMRC-PT enrolment" should {
      "not call eacdService" in {
        val enrolments = Set(Enrolment("key", Seq(EnrolmentIdentifier("key", "value")), "activated"))

        (mockTaxEnrolmentsConnector
          .deallocateEnrolment(_: String, _: String)(
            _: HeaderCarrier,
            _: ExecutionContext
          ))
          .expects(*, *, *, *)
          .returning(EitherT.rightT(HttpResponse(OK, "")))
          .never()

        val result = service.findAndDeleteWrongPTEnrolment(NINO, Enrolments(enrolments), "groupId")
        whenReady(result.value) { res =>
          res shouldBe Right(())
        }
      }
    }

    "There is a valid HMRC-PT enrolment" should {
      "not call eacdService" in {
        val enrolments = Set(Enrolment("HMRC-PT", Seq(EnrolmentIdentifier("NINO", NINO.nino)), "activated"))

        (mockTaxEnrolmentsConnector
          .deallocateEnrolment(_: String, _: String)(
            _: HeaderCarrier,
            _: ExecutionContext
          ))
          .expects(*, *, *, *)
          .returning(EitherT.rightT(HttpResponse(OK, "")))
          .never()

        val result = service.findAndDeleteWrongPTEnrolment(NINO, Enrolments(enrolments), "groupId")
        whenReady(result.value) { res =>
          res shouldBe Right(())
        }
      }
    }

    "There is an invalid HMRC-PT enrolment" should {
      "call eacdService" in {
        val enrolments = Set(Enrolment("HMRC-PT", Seq(EnrolmentIdentifier("NINO", secondNino.nino)), "activated"))

        (mockTaxEnrolmentsConnector
          .deallocateEnrolment(_: String, _: String)(
            _: HeaderCarrier,
            _: ExecutionContext
          ))
          .expects(*, s"HMRC-PT~NINO~${secondNino.nino}", *, *)
          .returning(EitherT.rightT(HttpResponse(OK, "")))
          .once()

        val result = service.findAndDeleteWrongPTEnrolment(NINO, Enrolments(enrolments), "groupId")
        whenReady(result.value) { res =>
          res shouldBe Right(())
        }
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

        (mockTaxEnrolmentsConnector
          .deallocateEnrolment(_: String, _: String)(
            _: HeaderCarrier,
            _: ExecutionContext
          ))
          .expects(*, s"HMRC-PT~NINO~${secondNino.nino}", *, *)
          .returning(EitherT.rightT(HttpResponse(OK, "")))
          .once()

        (mockTaxEnrolmentsConnector
          .deallocateEnrolment(_: String, _: String)(
            _: HeaderCarrier,
            _: ExecutionContext
          ))
          .expects(*, s"HMRC-PT~NINO~${thirdNino.nino}", *, *)
          .returning(EitherT.rightT(HttpResponse(OK, "")))
          .once()

        (mockTaxEnrolmentsConnector
          .deallocateEnrolment(_: String, _: String)(
            _: HeaderCarrier,
            _: ExecutionContext
          ))
          .expects(*, s"HMRC-PT~NINO~${NINO.nino}", *, *)
          .returning(EitherT.rightT(HttpResponse(OK, "")))
          .never()

        (mockTaxEnrolmentsConnector
          .deallocateEnrolment(_: String, _: String)(
            _: HeaderCarrier,
            _: ExecutionContext
          ))
          .expects(*, s"FAKE~NINO~${secondNino.nino}", *, *)
          .returning(EitherT.rightT(HttpResponse(OK, "")))
          .never()

        (mockTaxEnrolmentsConnector
          .deallocateEnrolment(_: String, _: String)(
            _: HeaderCarrier,
            _: ExecutionContext
          ))
          .expects(*, s"IR-SA~UTR~000000", *, *)
          .returning(EitherT.rightT(HttpResponse(OK, "")))
          .never()

        val result = service.findAndDeleteWrongPTEnrolment(NINO, Enrolments(enrolments), "groupId")
        whenReady(result.value) { res =>
          res shouldBe Right(())
        }
      }
    }

    "There is a server error" should {
      "return Left" in {
        val enrolments = Set(Enrolment("HMRC-PT", Seq(EnrolmentIdentifier("NINO", secondNino.nino)), "activated"))

        (mockTaxEnrolmentsConnector
          .deallocateEnrolment(_: String, _: String)(
            _: HeaderCarrier,
            _: ExecutionContext
          ))
          .expects(*, s"HMRC-PT~NINO~${secondNino.nino}", *, *)
          .returning(EitherT.leftT(UpstreamErrorResponse("error", INTERNAL_SERVER_ERROR)))
          .once()

        val result = service.findAndDeleteWrongPTEnrolment(NINO, Enrolments(enrolments), "groupId")
        whenReady(result.value) { res =>
          res shouldBe a[Left[UpstreamErrorResponse, _]]
        }
      }
    }

  }
}
