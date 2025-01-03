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
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Request}
import uk.gov.hmrc.http.{Authorization, HeaderCarrier}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.actions.AuthJourney
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.helpers.TEAFrontendController
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.{UpstreamError, UpstreamUnexpected2XX}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.logging.EventLoggerService
import uk.gov.hmrc.taxenrolmentassignmentfrontend.logging.LoggingEvent._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.services.EACDService
import uk.gov.hmrc.taxenrolmentassignmentfrontend.testOnly.config.AppConfigTestOnly
import uk.gov.hmrc.taxenrolmentassignmentfrontend.testOnly.models.CustomTestDataForm.customDataForm
import uk.gov.hmrc.taxenrolmentassignmentfrontend.testOnly.models.{AccountDetailsTestOnly, CustomTestDataForm, TestMocks}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.testOnly.utils.AccountUtilsTestOnly
import uk.gov.hmrc.taxenrolmentassignmentfrontend.testOnly.models.TestDataForm.selectUserForm
import uk.gov.hmrc.taxenrolmentassignmentfrontend.testOnly.services.EnrolmentStoreServiceTestOnly
import uk.gov.hmrc.taxenrolmentassignmentfrontend.testOnly.views.html.{InsertTestData, LoginCheckCompleteView, SelectTestData, SuccessView}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

@Singleton
class TestOnlyController @Inject() (
  accountUtilsTestOnly: AccountUtilsTestOnly,
  mcc: MessagesControllerComponents,
  logger: EventLoggerService,
  authJourney: AuthJourney,
  eacdService: EACDService,
  enrolmentStoreServiceTestOnly: EnrolmentStoreServiceTestOnly,
  selectTestDataPage: SelectTestData,
  insertTestDataView: InsertTestData,
  successPage: SuccessView,
  loginCheckCompleteView: LoginCheckCompleteView,
  appConfigTestOnly: AppConfigTestOnly,
  fileHelper: FileHelper
)(implicit ec: ExecutionContext)
    extends TEAFrontendController(mcc) {

  def getTestDataInfo: Action[AnyContent] = Action { request =>
    Ok(selectTestDataPage(TestMocks.mocks)(request, mcc.messagesApi.preferred(request)))
  }

  def getCustomTestData: Action[AnyContent] = Action { request =>
    val dataToFill = Json.toJson(extractData("singleUserWithSAEnrolment"))
    Ok(
      insertTestDataView(CustomTestDataForm.customDataForm.fill(dataToFill.toString()))(
        request,
        mcc.messagesApi.preferred(request)
      )
    )
  }

  private def insertAccount(
    accounts: List[AccountDetailsTestOnly]
  )(implicit hc: HeaderCarrier, request: Request[AnyRef]) =
    accounts
      .map { account =>
        for {
          _ <- accountUtilsTestOnly.deleteAccountDetails(account)
          _ <- accountUtilsTestOnly.insertAccountDetails(account)
        } yield ()
      }
      .sequence
      .fold(
        error => InternalServerError(error.toString),
        _ => Ok(successPage(accounts, appConfigTestOnly)(request, request2Messages))
      )

  def insertCustomTestData: Action[AnyContent] = Action.async { implicit request =>
    implicit val hc: HeaderCarrier = HeaderCarrier(Some(Authorization("Bearer 1")))

    customDataForm
      .bindFromRequest()
      .fold(
        error =>
          Future
            .successful(BadRequest(insertTestDataView(error)(request, mcc.messagesApi.preferred(request)))),
        data => {
          val json = Try(Json.parse(data.trim).as[List[AccountDetailsTestOnly]])

          json match {
            case Success(accounts) =>
              insertAccount(accounts)
            case Failure(_: JsResultException) => insertAccount(List(Json.parse(data.trim).as[AccountDetailsTestOnly]))
            case Failure(error) =>
              Future
                .successful(
                  BadRequest(
                    insertTestDataView(
                      CustomTestDataForm.customDataForm
                        .bind(Map("user-data" -> data))
                        .withError("Parse Error", error.getMessage)
                    )(
                      request,
                      mcc.messagesApi.preferred(request)
                    )
                  )
                )
          }
        }
      )
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
          val account = extractData(data)
          account
            .map { account =>
              for {
                _ <- accountUtilsTestOnly.deleteAccountDetails(account)
                _ <- accountUtilsTestOnly.insertAccountDetails(account)
              } yield ()
            }
            .sequence
            .fold(
              error => InternalServerError(error.toString),
              _ => Ok(successPage(account, appConfigTestOnly))
            )
        }
      )
  }

  def extractData(file: String) =
    fileHelper.loadFile(s"$file.json") match {
      case Success(json) =>
        Try(json.as[JsArray]) match {
          case Success(_)                    => json.as[List[AccountDetailsTestOnly]]
          case Failure(_: JsResultException) => List(json.as[AccountDetailsTestOnly])
          case Failure(error)                => throw error
        }
      case Failure(error) => throw error
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

  def successfulCall: Action[AnyContent] = authJourney.authJourney.async { implicit request =>
    logger.logEvent(logSuccessfulRedirectToReturnUrl)
    val connectorCall =
      if (appConfigTestOnly.environment == "Staging")
        enrolmentStoreServiceTestOnly.getUsersAssignedPTEnrolmentFromStub(request.userDetails.nino)
      else eacdService.getUsersAssignedPTEnrolment

    connectorCall
      .bimap(
        error => InternalServerError(error.toString),
        userAssignedEnrolment =>
          userAssignedEnrolment.enrolledCredential match {
            case None                                                 => InternalServerError("No HMRC-PT enrolment")
            case Some(credID) if credID == request.userDetails.credId => Ok(loginCheckCompleteView())
            case Some(credId) =>
              InternalServerError(s"Wrong credid `$credId` in enrolment. It should be ${request.userDetails.credId}")
          }
      )
      .merge
  }
}
