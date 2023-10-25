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

package uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.testOnly

import cats.implicits.toTraverseOps
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.play.http.HeaderCarrierConverter
import uk.gov.hmrc.taxenrolmentassignmentfrontend.connectors.testOnly.{EnrolmentStoreConnectorTestOnly, EnrolmentStoreStubConnectorTestOnly}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.actions.AuthAction
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.helpers.TEAFrontendController
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.UpstreamError
import uk.gov.hmrc.taxenrolmentassignmentfrontend.logging.EventLoggerService
import uk.gov.hmrc.taxenrolmentassignmentfrontend.logging.LoggingEvent._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.formats.EnrolmentsFormats
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.testOnly.AccountDetailsTestOnly

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TestOnlyController @Inject() (
  enrolmentStoreStubConnectorTestOnly: EnrolmentStoreStubConnectorTestOnly,
  enrolmentStoreConnectorTestOnly: EnrolmentStoreConnectorTestOnly,
  mcc: MessagesControllerComponents,
  authAction: AuthAction,
  logger: EventLoggerService
)(implicit ec: ExecutionContext)
    extends TEAFrontendController(mcc) {

  def create: Action[AnyContent] = Action.async { request =>
    val hcFromSession = HeaderCarrierConverter.fromRequestAndSession(request, request.session)
    implicit val hc = if (hcFromSession.authorization.isEmpty) {
      hcFromSession.copy(authorization = HeaderCarrierConverter.fromRequest(request).authorization)
    } else {
      hcFromSession
    }

    val data = request.body.asJson.get.as[AccountDetailsTestOnly]

    (for {
      _ <- enrolmentStoreStubConnectorTestOnly
             .addStubAccount(data)
      _ <- data.enrolments.map(enrolment => enrolmentStoreConnectorTestOnly.upsertEnrolment(enrolment)).sequence
      _ <-
        data.enrolments
          .map(enrolment =>
            enrolmentStoreConnectorTestOnly.addEnrolmentToGroup(data.groupId, data.users.head.credId, enrolment)
          )
          .sequence
    } yield ()).fold(
      {
        case UpstreamError(error) => InternalServerError(error.message)
        case error                => InternalServerError(error.toString)
      },
      _ => Ok("Created")
    )
  }

  def successfulCall: Action[AnyContent] = Action.async { _ =>
    logger.logEvent(logSuccessfulRedirectToReturnUrl)
    Future.successful(Ok("Successful"))
  }

  def usersGroupSearchCall(credId: String): Action[AnyContent] = Action.async { _ =>
    val userDetails =
      UsersGroupsFixedData.usersGroupSearchCreds.getOrElse(credId, UsersGroupsFixedData.defaultUserResponse)
    Future.successful(
      NonAuthoritativeInformation(
        UsersGroupsFixedData.toJson(userDetails)
      )
    )
  }

  def enrolmentsFromAuth(): Action[AnyContent] = authAction { implicit request =>
    Ok(Json.toJson(request.userDetails.enrolments.enrolments)(EnrolmentsFormats.writes).toString())
  }

  val successfulSACall: Action[AnyContent] = Action.async { _ =>
    Future.successful(Ok("Successful Redirect to SA"))
  }

  val authStub: Action[AnyContent] = Action { _ =>
    Ok
  }

  val taxEnrolmentsStub: Action[AnyContent] = Action { _ =>
    NoContent
  }
}
