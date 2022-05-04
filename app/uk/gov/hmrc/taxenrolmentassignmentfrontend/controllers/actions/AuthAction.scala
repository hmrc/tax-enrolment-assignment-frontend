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

package uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.actions

import play.api.Logger
import play.api.mvc.Results._
import play.api.mvc._
import uk.gov.hmrc.auth.core.AuthProvider.GovernmentGateway
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals._
import uk.gov.hmrc.auth.core.retrieve.~
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.http.HeaderCarrierConverter
import uk.gov.hmrc.taxenrolmentassignmentfrontend.config.AppConfig
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.routes
import uk.gov.hmrc.taxenrolmentassignmentfrontend.logging.EventLoggerService
import uk.gov.hmrc.taxenrolmentassignmentfrontend.logging.LoggingEvent._

import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

case class UserDetailsFromSession(credId: String,
                                  nino: String,
                                  groupId: String,
                                  enrolments: Enrolments,
                                  hasPTEnrolment: Boolean,
                                  hasSAEnrolment: Boolean)

case class RequestWithUserDetailsFromSession[A](
  request: Request[A],
  userDetails: UserDetailsFromSession,
  sessionID: String
) extends WrappedRequest[A](request)

trait AuthIdentifierAction
    extends ActionBuilder[RequestWithUserDetailsFromSession, AnyContent]
    with ActionFunction[Request, RequestWithUserDetailsFromSession]

@Singleton
class AuthAction @Inject()(
  override val authConnector: AuthConnector,
  val parser: BodyParsers.Default,
  logger: EventLoggerService,
  appConfig: AppConfig
)(implicit val executionContext: ExecutionContext)
    extends AuthorisedFunctions
    with AuthIdentifierAction {

  val origin: String = "tax-enrolment-assignment-frontend"
  implicit val baseLogger: Logger = Logger(this.getClass.getName)

  override def invokeBlock[A](
    request: Request[A],
    block: RequestWithUserDetailsFromSession[A] => Future[Result]
  ): Future[Result] = {
    implicit val hc: HeaderCarrier =
      HeaderCarrierConverter.fromRequestAndSession(request, request.session)

    authorised(AuthProviders(GovernmentGateway) and ConfidenceLevel.L200)
      .retrieve(nino and credentials and allEnrolments and groupIdentifier) {
        case Some(nino) ~ Some(credentials) ~ enrolments ~ Some(groupId) =>
          val hasSAEnrolment =
            enrolments.getEnrolment("IR-SA").fold(false)(_.isActivated)
          val hasPTEnrolment = enrolments.getEnrolment("HMRC-PT").isDefined

          val userDetails = UserDetailsFromSession(
            credentials.providerId,
            nino,
            groupId,
            enrolments,
            hasPTEnrolment,
            hasSAEnrolment
          )
          val sessionID = request.session
            .get("sessionId")
            .getOrElse(UUID.randomUUID().toString)

          block(
            RequestWithUserDetailsFromSession(request, userDetails, sessionID)
          )

        case _ =>
          logger.logEvent(
            logAuthenticationFailure(
              s"session missing credential or NINO field for uri: ${request.uri}"
            )
          )
          Future.successful(
            Redirect(routes.AuthorisationController.notAuthorised().url)
          )
      } recover {
      case er: NoActiveSession =>
        logger.logEvent(
          logAuthenticationFailure(
            s"no active session for uri: ${request.uri} with message: ${er.getMessage}"
          ),
          er
        )
        toGGLogin
      case er: AuthorisationException =>
        logger.logEvent(
          logAuthenticationFailure(
            s"Auth exception: ${er.getMessage} for  uri ${request.uri}"
          )
        )
        Redirect(routes.AuthorisationController.notAuthorised().url)
    }
  }

  def toGGLogin: Result = {
    Redirect(
      appConfig.loginURL,
      Map(
        "continue_url" -> Seq(appConfig.loginCallback),
        "origin" -> Seq("tax-enrolment-assignment-frontend")
      )
    )
  }
}
