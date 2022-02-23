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

package uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.auth

import play.api.Logger
import play.api.mvc.Results._
import play.api.mvc._
import uk.gov.hmrc.auth.core.AuthProvider.GovernmentGateway
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals._
import uk.gov.hmrc.auth.core.retrieve.~
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.http.HeaderCarrierConverter
import uk.gov.hmrc.taxenrolmentassignmentfrontend.logging.EventLoggerService
import uk.gov.hmrc.taxenrolmentassignmentfrontend.logging.LoggingEvent._

import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

case class UserDetailsFromSession(credId: String,
                                  nino: String,
                                  hasPTEnrolment: Boolean,
                                  hasSAEnrolment: Boolean)
case class RequestWithUserDetails[A](request: Request[A],
                                     userDetails: UserDetailsFromSession,
                                     sessionID: String)
    extends WrappedRequest[A](request)
trait AuthIdentifierAction
    extends ActionBuilder[RequestWithUserDetails, AnyContent]
    with ActionFunction[Request, RequestWithUserDetails]

@Singleton
class AuthAction @Inject()(
  override val authConnector: AuthConnector,
  val parser: BodyParsers.Default,
  logger: EventLoggerService
)(implicit val executionContext: ExecutionContext)
    extends AuthorisedFunctions
    with AuthIdentifierAction {

  val origin: String = "tax-enrolment-assignment-frontend"
  implicit val baseLogger: Logger = Logger(this.getClass.getName)

  override def invokeBlock[A](
    request: Request[A],
    block: RequestWithUserDetails[A] => Future[Result]
  ): Future[Result] = {
    implicit val hc: HeaderCarrier = {
      HeaderCarrierConverter.fromRequestAndSession(request, request.session)
    }
    authorised(AuthProviders(GovernmentGateway) and ConfidenceLevel.L200)
      .retrieve(nino and credentials and allEnrolments) {
        case Some(nino) ~ Some(credentials) ~ enrolments =>
          val hasSAEnrolment = enrolments.getEnrolment("IR-SA").isDefined
          val hasPTEnrolment = enrolments.getEnrolment("HMRC-PT").isDefined

          val userDetails = UserDetailsFromSession(
            credentials.providerId,
            nino,
            hasPTEnrolment,
            hasSAEnrolment
          )

          val sessionID = request.headers.get("X-Request-ID").getOrElse(UUID.randomUUID().toString)
          block(RequestWithUserDetails(request, userDetails, sessionID))

        case _ =>
          logger.logEvent(
            logAuthenticationFailure(
              s"session missing credential or NINO field for uri: ${request.uri}"
            )
          )
          Future.successful(Unauthorized)
      } recover {
      case er: NoActiveSession =>
        logger.logEvent(
          logAuthenticationFailure(
            s"no active session for uri: ${request.uri} with message: ${er.getMessage}"
          ),
          er
        )
        Unauthorized("NoActiveSession")
      case er: AuthorisationException =>
        logger.logEvent(
          logAuthenticationFailure(
            s"Auth exception: ${er.getMessage} for  uri ${request.uri}"
          )
        )
        Unauthorized(er.getMessage)
    }
  }
}
