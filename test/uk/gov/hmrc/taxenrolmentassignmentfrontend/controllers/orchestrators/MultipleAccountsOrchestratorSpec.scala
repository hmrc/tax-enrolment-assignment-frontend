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

package uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.orchestrators

import cats.data.EitherT
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import play.api.libs.json.Format
import play.api.mvc.AnyContent
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes
import uk.gov.hmrc.taxenrolmentassignmentfrontend.AccountTypes.{
  MULTIPLE_ACCOUNTS,
  PT_ASSIGNED_TO_CURRENT_USER,
  PT_ASSIGNED_TO_OTHER_USER,
  SINGLE_ACCOUNT
}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.auth.RequestWithUserDetails
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.helpers.TestData._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.helpers.TestFixture
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.testOnly
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.{
  InvalidUserType,
  TaxEnrolmentAssignmentErrors
}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.orchestrators.MultipleAccountsOrchestrator

import scala.concurrent.{ExecutionContext, Future}

class MultipleAccountsOrchestratorSpec extends TestFixture with ScalaFutures {

  implicit val defaultPatience = PatienceConfig(
    timeout = Span(TIME_OUT, Seconds),
    interval = Span(INTERVAL, Millis)
  )

  val orchestrator =
    new MultipleAccountsOrchestrator(
      mockTeaSessionCache,
      mockUsersGroupService,
      mockSilentAssignmentService
    )

  s"getDetailsForLandingPage" when {
    s"the accountType $MULTIPLE_ACCOUNTS is  and redirectUrl are available in the cache" should {
      "return the userdetails for the account" in {
        (mockTeaSessionCache
          .getEntry(_: String)(
            _: RequestWithUserDetails[AnyContent],
            _: Format[AccountTypes.Value]
          ))
          .expects("ACCOUNT_TYPE", *, *)
          .returning(Future.successful(Some(MULTIPLE_ACCOUNTS)))

        (mockTeaSessionCache
          .getEntry(_: String)(
            _: RequestWithUserDetails[AnyContent],
            _: Format[String]
          ))
          .expects("redirectURL", *, *)
          .returning(
            Future.successful(
              Some(testOnly.routes.TestOnlyController.successfulCall.url)
            )
          )

        (mockUsersGroupService
          .getAccountDetails(_: String)(
            _: ExecutionContext,
            _: HeaderCarrier,
            _: RequestWithUserDetails[AnyContent]
          ))
          .expects(CREDENTIAL_ID, *, *, *)
          .returning(createInboundResult(accountDetails))

        val res = orchestrator.getDetailsForLandingPage
        whenReady(res.value) { result =>
          result shouldBe Right(accountDetails)
        }
      }
    }
    List(SINGLE_ACCOUNT, PT_ASSIGNED_TO_OTHER_USER, PT_ASSIGNED_TO_CURRENT_USER)
      .foreach { accountType =>
        s"the accountType is $accountType and redirectUrl are available in the cache" should {
          "return the InvalidUserType containing redirectUrl" in {
            (mockTeaSessionCache
              .getEntry(_: String)(
                _: RequestWithUserDetails[AnyContent],
                _: Format[AccountTypes.Value]
              ))
              .expects("ACCOUNT_TYPE", *, *)
              .returning(Future.successful(Some(accountType)))

            (mockTeaSessionCache
              .getEntry(_: String)(
                _: RequestWithUserDetails[AnyContent],
                _: Format[String]
              ))
              .expects("redirectURL", *, *)
              .returning(
                Future.successful(
                  Some(testOnly.routes.TestOnlyController.successfulCall.url)
                )
              )

            val res = orchestrator.getDetailsForLandingPage
            whenReady(res.value) { result =>
              result shouldBe Left(
                InvalidUserType(
                  Some(testOnly.routes.TestOnlyController.successfulCall.url)
                )
              )
            }
          }
        }

        s"the accountType is $accountType is available in the cache but not the redirectUrl" should {
          "return the InvalidUserType not containing redirectUrl" in {
            (mockTeaSessionCache
              .getEntry(_: String)(
                _: RequestWithUserDetails[AnyContent],
                _: Format[AccountTypes.Value]
              ))
              .expects("ACCOUNT_TYPE", *, *)
              .returning(Future.successful(Some(accountType)))

            (mockTeaSessionCache
              .getEntry(_: String)(
                _: RequestWithUserDetails[AnyContent],
                _: Format[String]
              ))
              .expects("redirectURL", *, *)
              .returning(Future.successful(None))

            val res = orchestrator.getDetailsForLandingPage
            whenReady(res.value) { result =>
              result shouldBe Left(InvalidUserType(None))
            }
          }
        }
      }

    s"the cache is empty" should {
      "return the InvalidUserType containing no redirectUrl" in {
        (mockTeaSessionCache
          .getEntry(_: String)(
            _: RequestWithUserDetails[AnyContent],
            _: Format[AccountTypes.Value]
          ))
          .expects("ACCOUNT_TYPE", *, *)
          .returning(Future.successful(None))

        (mockTeaSessionCache
          .getEntry(_: String)(
            _: RequestWithUserDetails[AnyContent],
            _: Format[String]
          ))
          .expects("redirectURL", *, *)
          .returning(Future.successful(None))

        val res = orchestrator.getDetailsForLandingPage
        whenReady(res.value) { result =>
          result shouldBe Left(InvalidUserType(None))
        }
      }
    }
  }

  s"getDetailsForEnroledAfterReportingFraud" when {
    //ToDo uncomment when available
    s"the accountType SA_ASSIGNED_TO_OTHER_USER is and redirectUrl are available in the cache" should {
      "return the userdetails for the account" in {
//        (mockTeaSessionCache
//          .getEntry(_: String)(
//            _: RequestWithUserDetails[AnyContent],
//            _: Format[AccountTypes.Value]
//          ))
//          .expects("ACCOUNT_TYPE", *, *)
//          .returning(Future.successful(Some(SA_ASSIGNED_TO_OTHER_USER)))
//
//        (mockTeaSessionCache
//          .getEntry(_: String)(
//            _: RequestWithUserDetails[AnyContent],
//            _: Format[String]
//          ))
//          .expects("redirectURL", *, *)
//          .returning(
//            Future.successful(
//              Some(testOnly.routes.TestOnlyController.successfulCall.url)
//            )
//          )

        (mockUsersGroupService
          .getAccountDetails(_: String)(
            _: ExecutionContext,
            _: HeaderCarrier,
            _: RequestWithUserDetails[AnyContent]
          ))
          .expects(CREDENTIAL_ID, *, *, *)
          .returning(createInboundResult(accountDetails))

        val res = orchestrator.getDetailsForEnroledAfterReportingFraud
        whenReady(res.value) { result =>
          result shouldBe Right(accountDetails)
        }
      }
    }
//    List(SINGLE_ACCOUNT, PT_ASSIGNED_TO_OTHER_USER, PT_ASSIGNED_TO_CURRENT_USER, MULTIPLE_ACCOUNTS, SA_ASSIGNED_TO_CURRENT_USER)
//      .foreach { accountType =>
//        s"the accountType is $accountType and redirectUrl are available in the cache" should {
//          "return the InvalidUserType containing redirectUrl" in {
//            (mockTeaSessionCache
//              .getEntry(_: String)(
//                _: RequestWithUserDetails[AnyContent],
//                _: Format[AccountTypes.Value]
//              ))
//              .expects("ACCOUNT_TYPE", *, *)
//              .returning(Future.successful(Some(accountType)))
//
//            (mockTeaSessionCache
//              .getEntry(_: String)(
//                _: RequestWithUserDetails[AnyContent],
//                _: Format[String]
//              ))
//              .expects("redirectURL", *, *)
//              .returning(
//                Future.successful(
//                  Some(testOnly.routes.TestOnlyController.successfulCall.url)
//                )
//              )
//
//            val res = orchestrator.getDetailsForEnroledAfterReportingFraud
//            whenReady(res.value) { result =>
//              result shouldBe Left(
//                InvalidUserType(
//                  Some(testOnly.routes.TestOnlyController.successfulCall.url)
//                )
//              )
//            }
//          }
//        }
//
//        s"the accountType is $accountType is available in the cache but not the redirectUrl" should {
//          "return the InvalidUserType not containing redirectUrl" in {
//            (mockTeaSessionCache
//              .getEntry(_: String)(
//                _: RequestWithUserDetails[AnyContent],
//                _: Format[AccountTypes.Value]
//              ))
//              .expects("ACCOUNT_TYPE", *, *)
//              .returning(Future.successful(Some(accountType)))
//
//            (mockTeaSessionCache
//              .getEntry(_: String)(
//                _: RequestWithUserDetails[AnyContent],
//                _: Format[String]
//              ))
//              .expects("redirectURL", *, *)
//              .returning(Future.successful(None))
//
//            val res = orchestrator.getDetailsForEnroledAfterReportingFraud
//            whenReady(res.value) { result =>
//              result shouldBe Left(InvalidUserType(None))
//            }
//          }
//        }
//      }
//
//    s"the cache is empty" should {
//      "return the InvalidUserType containing no redirectUrl" in {
//        (mockTeaSessionCache
//          .getEntry(_: String)(
//            _: RequestWithUserDetails[AnyContent],
//            _: Format[AccountTypes.Value]
//          ))
//          .expects("ACCOUNT_TYPE", *, *)
//          .returning(Future.successful(None))
//
//        (mockTeaSessionCache
//          .getEntry(_: String)(
//            _: RequestWithUserDetails[AnyContent],
//            _: Format[String]
//          ))
//          .expects("redirectURL", *, *)
//          .returning(Future.successful(None))
//
//        val res = orchestrator.getDetailsForEnroledAfterReportingFraud
//        whenReady(res.value) { result =>
//          result shouldBe Left(InvalidUserType(None))
//        }
//      }
//    }
  }

  "checkValidAccountTypeAndEnrolForPT" should {
    "return unit when silentEnrolment successful" in {
      (mockSilentAssignmentService
        .enrolUser()(
          _: RequestWithUserDetails[AnyContent],
          _: HeaderCarrier,
          _: ExecutionContext
        ))
        .expects(*, *, *)
        .returning(
          EitherT.right[TaxEnrolmentAssignmentErrors](Future.successful(Unit))
        )

      val res = orchestrator.checkValidAccountTypeAndEnrolForPT()

      whenReady(res.value) { result =>
        result shouldBe Right((): Unit)
      }
    }
  }
}
