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

import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.auth.core.retrieve.~
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals.{allEnrolments, nino}
import uk.gov.hmrc.auth.core.{AuthConnector, AuthorisedFunctions}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.http.HeaderCarrierConverter.fromRequestAndSession
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.actions.AuthAction
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.helpers.TEAFrontendController
import uk.gov.hmrc.taxenrolmentassignmentfrontend.logging.EventLoggerService
import uk.gov.hmrc.taxenrolmentassignmentfrontend.logging.LoggingEvent._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.formats.EnrolmentsFormats

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TestOnlyController @Inject() (
  override val authConnector: AuthConnector,
  mcc: MessagesControllerComponents,
  authAction: AuthAction,
  logger: EventLoggerService
)(implicit val executionContext: ExecutionContext)
    extends TEAFrontendController(mcc) with AuthorisedFunctions {

  def successfulCall: Action[AnyContent] = Action.async { request =>
    implicit val hc: HeaderCarrier = fromRequestAndSession(request, request.session)
    authorised().retrieve(nino and allEnrolments) {
      case Some(nino) ~ enrolments =>
        enrolments.enrolments
          .filter(_.key == "HMRC-PT")
          .flatMap { enrolment =>
            enrolment.identifiers
              .filter(id => id.key == "NINO")
          } match {
          case enrolmentIdentifiers if enrolmentIdentifiers.isEmpty =>
            Future.successful(Forbidden("No HMRC-PT enrolment present"))
          case enrolmentIdentifiers
              if enrolmentIdentifiers.exists(enrolmentIdentifier => enrolmentIdentifier.value == nino) =>
            Future.successful(Ok("Successful"))
          case _ =>
            logger.logEvent(logSuccessfulRedirectToReturnUrl)
            Future.successful(Forbidden("HMRC-PT enrolment present with wrong nino"))
        }

      case _ => Future.successful(InternalServerError("Server error"))
    }
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
