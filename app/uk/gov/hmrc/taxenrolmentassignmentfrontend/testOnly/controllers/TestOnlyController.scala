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
import play.api.libs.json.{JsArray, JsResultException}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.http.{Authorization, HeaderCarrier}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.actions.AuthJourney
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.helpers.TEAFrontendController
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.{UpstreamError, UpstreamUnexpected2XX}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.logging.EventLoggerService
import uk.gov.hmrc.taxenrolmentassignmentfrontend.logging.LoggingEvent._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.services.EACDService
import uk.gov.hmrc.taxenrolmentassignmentfrontend.testOnly.models.AccountDetailsTestOnly
import uk.gov.hmrc.taxenrolmentassignmentfrontend.testOnly.utils.AccountUtilsTestOnly

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}

@Singleton
class TestOnlyController @Inject() (
  accountUtilsTestOnly: AccountUtilsTestOnly,
  mcc: MessagesControllerComponents,
  logger: EventLoggerService,
  authJourney: AuthJourney,
  eacdService: EACDService
)(implicit ec: ExecutionContext)
    extends TEAFrontendController(mcc) {

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

}
