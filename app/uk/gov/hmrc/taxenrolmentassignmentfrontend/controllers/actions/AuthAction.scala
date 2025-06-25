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

package uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.actions

import play.api.Logger
import play.api.mvc.Results._
import play.api.mvc._
import uk.gov.hmrc.auth.core.AuthProvider.GovernmentGateway
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals._
import uk.gov.hmrc.auth.core.retrieve.~
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.http.HeaderCarrierConverter
import uk.gov.hmrc.play.http.HeaderCarrierConverter.fromRequestAndSession
import uk.gov.hmrc.taxenrolmentassignmentfrontend.config.AppConfig
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.helpers.ErrorHandler
import uk.gov.hmrc.taxenrolmentassignmentfrontend.controllers.routes
import uk.gov.hmrc.taxenrolmentassignmentfrontend.errors.UnexpectedError
import uk.gov.hmrc.taxenrolmentassignmentfrontend.logging.EventLoggerService
import uk.gov.hmrc.taxenrolmentassignmentfrontend.logging.LoggingEvent._
import uk.gov.hmrc.taxenrolmentassignmentfrontend.models.enums.EnrolmentEnum.{IRSAKey, hmrcPTKey}
import uk.gov.hmrc.taxenrolmentassignmentfrontend.utils.HmrcPTEnrolment

import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

case class UserDetailsFromSession(
  credId: String,
  providerType: String,
  nino: Nino,
  groupId: String,
  email: Option[String],
  affinityGroup: AffinityGroup,
  enrolments: Enrolments,
  hasPTEnrolment: Boolean,
  hasSAEnrolment: Boolean
) {

  val utr: Option[String] = enrolments.getEnrolment(s"$IRSAKey").flatMap(_.getIdentifier("UTR").map(_.value))

}

case class RequestWithUserDetailsFromSession[A](
  request: Request[A],
  userDetails: UserDetailsFromSession,
  sessionID: String
) extends WrappedRequest[A](request)
    with RequestWithUserDetails

trait AuthIdentifierAction
    extends ActionBuilder[RequestWithUserDetailsFromSession, AnyContent]
    with ActionFunction[Request, RequestWithUserDetailsFromSession]

@Singleton
class AuthAction @Inject() (
  override val authConnector: AuthConnector,
  val parser: BodyParsers.Default,
  logger: EventLoggerService,
  val appConfig: AppConfig,
  hmrcPTEnrolment: HmrcPTEnrolment,
  errorHandler: ErrorHandler
)(implicit val executionContext: ExecutionContext)
    extends AuthorisedFunctions
    with AuthIdentifierAction
    with RedirectHelper {

  implicit val baseLogger: Logger = Logger(this.getClass.getName)

  override def invokeBlock[A](
    request: Request[A],
    block: RequestWithUserDetailsFromSession[A] => Future[Result]
  ): Future[Result] = {
    implicit val hc: HeaderCarrier =
      HeaderCarrierConverter.fromRequestAndSession(request, request.session)
    authorised(AuthProviders(GovernmentGateway) and ConfidenceLevel.L200)
      .retrieve(nino and credentials and allEnrolments and groupIdentifier and affinityGroup and email) {
        case Some(nino) ~ Some(credentials) ~ enrolments ~ Some(groupId) ~ Some(affinityGroup) ~ email =>
          implicit val hc: HeaderCarrier = fromRequestAndSession(request, request.session)
          val hasSAEnrolment             = enrolments.getEnrolment(s"$IRSAKey").fold(false)(_.isActivated)
          val userDetails                = UserDetailsFromSession(
            credentials.providerId,
            credentials.providerType,
            Nino(nino),
            groupId,
            email,
            affinityGroup,
            enrolments,
            enrolments.getEnrolment(s"$hmrcPTKey").flatMap(_.identifiers.find(_.value == nino)).isDefined,
            hasSAEnrolment
          )

          val sessionID = hc.sessionId.fold {
            logger.logEvent(logUserDidNotHaveSessionIdGeneratedSessionId(credentials.providerId))
            UUID.randomUUID().toString
          }(_.value)

          val requestWithUserDetailsFromSession = RequestWithUserDetailsFromSession(request, userDetails, sessionID)

          hmrcPTEnrolment
            .findAndDeleteWrongPTEnrolment(Nino(nino), enrolments, groupId)
            .foldF(
              _ =>
                Future.successful(
                  errorHandler
                    .handleErrors(UnexpectedError, "[AuthAction]")(requestWithUserDetailsFromSession, implicitly)
                ),
              _ => block(requestWithUserDetailsFromSession)
            )

        case _ =>
          logger.logEvent(logAuthenticationFailure(s"session missing credential or NINO field for uri: ${request.uri}"))
          Future.successful(Redirect(routes.AuthorisationController.notAuthorised.url))
      } recover {
      case _: NoActiveSession         =>
        toGGLogin // user has come from a gov uk page or a bookmark without authentication - nothing to be done
      case er: AuthorisationException =>
        logger.logEvent(logAuthenticationFailure(s"Auth exception: ${er.getMessage} for  uri ${request.uri}"))
        Redirect(routes.AuthorisationController.notAuthorised.url)
    }
  }
}
