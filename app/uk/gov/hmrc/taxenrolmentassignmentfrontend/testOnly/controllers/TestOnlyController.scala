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

package uk.gov.hmrc.taxenrolmentassignmentfrontend.testOnly.controllers

import cats.implicits.toTraverseOps
import play.api.libs.json.{JsArray, JsResultException, Json}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.http.{Authorization, HeaderCarrier}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.actions.AuthJourney
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.helpers.TEAFrontendController
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.{UpstreamError, UpstreamUnexpected2XX}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.logging.EventLoggerService
import uk.gov.hmrc.taxenrolmentassignmentfrontend.logging.LoggingEvent._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.services.EACDService
import uk.gov.hmrc.taxenrolmentassignmentfrontend.testOnly.models.AccountDetailsTestOnly
import uk.gov.hmrc.taxenrolmentassignmentfrontend.testOnly.models.TestMocks
import uk.gov.hmrc.taxenrolmentassignmentfrontend.testOnly.utils.AccountUtilsTestOnly
import uk.gov.hmrc.taxenrolmentassignmentfrontend.views.html.testOnly.SelectTestData

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.testOnly.models.TestDataForm.selectUserForm

@Singleton
class TestOnlyController @Inject() (
  accountUtilsTestOnly: AccountUtilsTestOnly,
  mcc: MessagesControllerComponents,
  logger: EventLoggerService,
  authJourney: AuthJourney,
  eacdService: EACDService,
  selectTestDataPage: SelectTestData
)(implicit ec: ExecutionContext)
    extends TEAFrontendController(mcc) {

  def getTestDataInfo: Action[AnyContent] = Action { request =>
    Ok(selectTestDataPage(TestMocks.mocks)(request, mcc.messagesApi.preferred(request)))
  }

  def insertTestData: Action[AnyContent] = Action.async { implicit request =>
    implicit val hc: HeaderCarrier = HeaderCarrier(Some(Authorization("Bearer 1")))

    selectUserForm
      .bindFromRequest()
      .fold(
        _ =>
          Future
            .successful(BadRequest(selectTestDataPage(TestMocks.mocks)(request, mcc.messagesApi.preferred(request)))),
        data => {
          val fileName = data match {
            case "Multiple Accounts: No Enrolments"                => "multipleAccountsNoEnrolments.json"
            case "Multiple Accounts: One with PT and SA Enrolment" => "multipleAccountsOneWithPTAndSAEnrolment.json"
            case "Multiple Accounts: One with PT Enrolment"        => "multipleAccountsOneWithPTEnrolment.json"
            case "Multiple Accounts: One with PT Enrolment, another with SA Enrolment" =>
              "multipleAccountsOneWithPTEnrolmentOtherWithSA.json"
            case "Multiple Accounts: One with SA Enrolment"           => "multipleAccountsOneWithSAEnrolment.json"
            case "Single User: No enrolments"                         => "singleUserNoEnrolments.json"
            case "Single User: SA Enrolments"                         => "singleUserWithSAEnrolment.json"
            case "Throttled Multiple Accounts: Has SA Enrolment"      => "throttledMultipleAccountsHasSA.json"
            case "Throttled Multiple Accounts: No SA Enrolment"       => "throttledMultipleAccountsNoSA.json"
            case "Throttled Multiple Accounts: One with PT Enrolment" => "throttledMultipleAccountsOneWithPT.json"
          }
          val x = Json.parse(FileHelper.loadFile(fileName))
          println("aaaaaa " + x)
          val y = Try(x.as[JsArray]) match {
            case Success(_)                    => x.as[List[AccountDetailsTestOnly]]
            case Failure(_: JsResultException) => List(x.as[AccountDetailsTestOnly])
            case Failure(error)                => throw error
          }
          y.map { data =>
            for {
              _ <- accountUtilsTestOnly.deleteAccountDetails(data)
              _ <- accountUtilsTestOnly.insertAccountDetails(data)
            } yield ()
          }.sequence
            .fold(
              {
                case UpstreamError(error)              => InternalServerError(error.message)
                case UpstreamUnexpected2XX(message, _) => InternalServerError(message)
                case error                             => InternalServerError(error.toString)
              },
              _ => Ok("Created")
            )
        }
      )
  }

  def create: Action[AnyContent] = Action.async { request =>
    implicit val hc: HeaderCarrier = HeaderCarrier(Some(Authorization("Bearer 1")))

    val jsonData = request.body.asJson.get

    val accountList = Try(jsonData.as[JsArray]) match {
      case Success(_)                    => jsonData.as[List[AccountDetailsTestOnly]]
      case Failure(_: JsResultException) => List(jsonData.as[AccountDetailsTestOnly])
      case Failure(error)                => throw error
    }

    accountList
      .map { data =>
        for {
          _ <- accountUtilsTestOnly.deleteAccountDetails(data)
          _ <- accountUtilsTestOnly.insertAccountDetails(data)
        } yield ()
      }
      .sequence
      .fold(
        {
          case UpstreamError(error)              => InternalServerError(error.message)
          case UpstreamUnexpected2XX(message, _) => InternalServerError(message)
          case error                             => InternalServerError(error.toString)
        },
        _ => Ok("Created")
      )
  }

  def delete: Action[AnyContent] = Action.async { request =>
    implicit val hc: HeaderCarrier = HeaderCarrier(Some(Authorization("Bearer 1")))

    val jsonData = request.body.asJson.get

    val accountList = Try(jsonData.as[JsArray]) match {
      case Success(_)                    => jsonData.as[List[AccountDetailsTestOnly]]
      case Failure(_: JsResultException) => List(jsonData.as[AccountDetailsTestOnly])
      case Failure(error)                => throw error
    }

    accountList
      .map { data =>
        accountUtilsTestOnly.deleteAccountDetails(data)
      }
      .sequence
      .fold(
        {
          case UpstreamError(error)              => InternalServerError(error.message)
          case UpstreamUnexpected2XX(message, _) => InternalServerError(message)
          case error                             => InternalServerError(error.toString)
        },
        _ => Ok("Deleted")
      )
  }

  //todo: to be used instead of the call below successfulCall. See DDCNL-8607
  def newSuccessfulCall: Action[AnyContent] = authJourney.authJourney.async { implicit request =>
    logger.logEvent(logSuccessfulRedirectToReturnUrl)
    eacdService.getUsersAssignedPTEnrolment
      .bimap(
        error => InternalServerError(error.toString),
        userAssignedEnrolment =>
          userAssignedEnrolment.enrolledCredential match {
            case None                                                 => InternalServerError("No HMRC-PT enrolment")
            case Some(credID) if credID == request.userDetails.credId => Ok("Successful")
            case Some(credId) =>
              InternalServerError(s"Wrong credid `$credId` in enrolment. It should be ${request.userDetails.credId}")
          }
      )
      .merge
  }

  def successfulCall: Action[AnyContent] = Action.async { _ =>
    logger.logEvent(logSuccessfulRedirectToReturnUrl)
    Future.successful(Ok("Successful"))
  }
}
